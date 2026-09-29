package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.AccountUtilities;
import com.identityworksllc.iiq.common.AggregationOutcome;
import com.identityworksllc.iiq.common.logging.SLogger;
import com.identityworksllc.iiq.common.vo.OutcomeType;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.ResourceObject;
import sailpoint.object.TaskResult;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes a partial JDBC aggregation against the specified application. This allows
 * you to invoke the aggregation logic in batches on a subset of the data in the application,
 * which may be significantly faster or more conducive to business processes vs. aggregating
 * the entire application every time.
 *
 * You can split the task by replacing the SQL entirely, by adding a filter to the existing
 * SQL (either appending to the WHERE clause or as a subquery), or by specifying a partition
 * number. If you specify a partition number, only that partition's query will be run.
 *
 * This task works by streaming the ResourceObjects from the connector in a single thread,
 * passing each of them off to a multi-threaded Aggregator worker as it is read. The
 * connector's iterator is closed when it is exhausted, when the task completes or fails,
 * or when the task is terminated, whichever comes first.
 */
public class PartialJdbcAggregation extends AbstractThreadedTask<ResourceObject> {

    /**
     * The JDBC connector's partition statements attribute name
     */
    public static final String ATTRIBUTE_PARTITION_STATEMENTS = "partitionStatements";
    /**
     * The JDBC connector's SQL attribute name
     */
    public static final String ATTRIBUTE_SQL = "SQL";
    /**
     * The JDBC connector's flag indicating that the partition statements are stored procedure calls
     */
    public static final String ATTRIBUTE_USE_STORED_PROCEDURE_PARTITION = "useStoredProcedurePartition";
    /**
     * The JDBC connector's flag indicating that the SQL attribute is a stored procedure call
     */
    public static final String ATTRIBUTE_USE_STORED_PROCEDURE_SQL = "useStoredProcedureSqlStmt";
    /**
     * The alias given to the wrapped subquery when a non-AND sqlFilter is used
     */
    public static final String SUBQUERY_ALIAS = "partial_agg";
    /**
     * Logger
     */
    private static final SLogger log = SLogger.getLogger(PartialJdbcAggregation.class);

    /**
     * Wraps the connector's live {@link CloseableIterator} so that it can be consumed by the
     * superclass as a plain {@link Iterator}, while guaranteeing that the underlying iterator
     * is closed exactly once.
     *
     * The iterator reads one object ahead: {@link #hasNext()} fetches the next object from the
     * connector and {@link #next()} returns it. The iterator closes itself when the connector's
     * results are exhausted. Once closed, or once the task has been terminated, it reports no
     * further elements (other than one already prefetched). This also prevents the superclass's
     * final {@link Util#flushIterator(Iterator)} call from draining the rest of the result set
     * after a connector read failure or task termination.
     *
     * {@link #close()} may be invoked from the task termination thread while the task thread
     * is blocked reading from the connector. In that case, the resulting read exception is
     * swallowed and the iterator simply reports that it is exhausted.
     */
    private final class StreamingResourceIterator implements Iterator<ResourceObject> {
        /**
         * Set to true when the delegate has been closed
         */
        private final AtomicBoolean closed;
        /**
         * The number of objects read from the connector so far
         */
        private final AtomicInteger count;
        /**
         * The live iterator from the connector
         */
        private final CloseableIterator<ResourceObject> delegate;
        /**
         * The next object, read ahead from the connector by {@link #hasNext()}. Only
         * accessed by the task thread that consumes this iterator.
         */
        private ResourceObject buffered;

        private StreamingResourceIterator(CloseableIterator<ResourceObject> delegate) {
            this.delegate = Objects.requireNonNull(delegate);
            this.closed = new AtomicBoolean(false);
            this.count = new AtomicInteger(0);
        }

        /**
         * Closes the connector's iterator, if it has not already been closed. Safe to
         * call more than once and from any thread.
         */
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    delegate.close();
                } catch (Exception e) {
                    log.warn("Caught an error closing the connector iterator for application " + applicationName, e);
                }
                log.info("Closed connector iterator for application {0} after reading {1} objects", applicationName, count.get());
            }
        }

        /**
         * Handles an exception thrown by the connector's iterator. If the iterator was
         * closed or the task was terminated concurrently, the exception is an expected
         * side effect and is swallowed. Otherwise, the iterator is closed and the
         * exception is rethrown.
         *
         * @param e The exception thrown by the connector
         * @return Always false, if the exception is not rethrown
         */
        private boolean handleReadFailure(RuntimeException e) {
            if (isStopped()) {
                log.debug("Ignoring connector read error after close or termination", e);
                close();
                return false;
            }
            close();
            throw e;
        }

        /**
         * Checks to see whether the connector's iterator has another record to return. This
         * may hang for a while if the connector is doing live streaming from the result set.
         *
         * If an object is available, stores the next object in 'buffered'.
         *
         * The underlying delegated CloseableIterator will be closed from within this
         * method whenever the task has been stopped or the iterator has been exhausted.
         *
         * @return True if the iterator has another object to return, false if the iterator has been exhausted or the task has been terminated
         */
        @Override
        public boolean hasNext() {
            if (buffered != null) {
                return true;
            }
            if (isStopped()) {
                close();
                return false;
            }
            try {
                if (delegate.hasNext()) {
                    buffered = delegate.next();
                    count.incrementAndGet();
                    return true;
                }
            } catch (NoSuchElementException e) {
                /* Treated as exhaustion below */
            } catch (RuntimeException e) {
                return handleReadFailure(e);
            }
            close();
            return false;
        }

        /**
         * @return True if this iterator has been closed or the task has been terminated
         */
        private boolean isStopped() {
            return closed.get() || terminated.get();
        }

        /**
         * Returns the object prefetched by {@link #hasNext()}. This never reads from the
         * connector directly, so it cannot fail due to a concurrent close or termination
         * once hasNext() has returned true.
         */
        @Override
        public ResourceObject next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ResourceObject next = buffered;
            buffered = null;
            return next;
        }
    }
    /**
     * The rest of the aggregation arguments, to be passed to the {@link sailpoint.api.Aggregator}
     */
    private Attributes<String, Object> aggregateArgs;

    /**
     * The name of the application to aggregate
     */
    private String applicationName;

    /**
     * The partition number to run, if any
     */
    private Integer partition;

    /**
     * The live iterator over the connector's results for the current execution, if any
     */
    private volatile StreamingResourceIterator resourceIterator;

    /**
     * The replacement SQL, if any
     */
    private String sql;

    /**
     * The SQL filter to add, if any
     */
    private String sqlFilter;

    /**
     * Closes the connector's iterator after all threads have completed or failed, if it
     * has not already been closed by exhaustion or termination.
     *
     * @param context The SailPoint context
     */
    @Override
    protected void afterCompletion(SailPointContext context) {
        StreamingResourceIterator iterator = this.resourceIterator;
        if (iterator != null) {
            iterator.close();
        }
        this.resourceIterator = null;
    }

    /**
     * Retrieves the ResourceObjects to aggregate
     *
     * @param context The top-level task Sailpoint context object
     * @param taskArgs The task arguments
     * @return An iterator over the ResourceObjects to aggregate
     * @throws GeneralException if any failures occur
     */
    @Override
    protected Iterator<? extends ResourceObject> getObjectIterator(SailPointContext context, Attributes<String, Object> taskArgs) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);
        String type = application.getType();
        if (!Util.nullSafeEq(type, "JDBC")) {
            throw new IllegalArgumentException("Application " + applicationName + " is not a JDBC application");
        }

        Application cloned = patchApplication(context, application);

        Connector sqlConnector = ConnectorFactory.getConnector(cloned, null);

        log.info("Reading from application {0}", applicationName);
        log.debug("SQL: {0}", cloned.getAttributes().getString(ATTRIBUTE_SQL));

        CloseableIterator<ResourceObject> results;
        try {
            results = sqlConnector.iterateObjects("account", null, new HashMap<>());
        } catch(ConnectorException e) {
            throw new GeneralException(e);
        }

        StreamingResourceIterator streamingIterator = new StreamingResourceIterator(results);
        this.resourceIterator = streamingIterator;

        // Termination handlers run both on terminate() and on normal completion; close() is idempotent
        addTerminationHandler(ctx -> streamingIterator.close());

        return streamingIterator;
    }

    /**
     * Parses the task arguments
     * @param args The task arguments to parse
     * @throws Exception if any failures occur parsing the arguments
     */
    @Override
    protected void parseArgs(Attributes<String, Object> args) throws Exception {
        super.parseArgs(args);

        this.aggregateArgs = new Attributes<>(args);

        if (args.get("partition") != null) {
            this.partition = args.getInt("partition");
        }
        this.sql = args.getString("replacementSQL");
        this.sqlFilter = args.getString("sqlFilter");
        this.applicationName = args.getString("application");
    }

    /**
     * Patches the application with the replacement SQL, if any.
     *
     * @param context The SailPoint context
     * @param application The application to patch
     * @return The patched application
     * @throws GeneralException if any failures occur
     */
    @Nonnull
    private Application patchApplication(SailPointContext context, Application application) throws GeneralException {
        Application cloned = (Application) application.deepCopy((XMLReferenceResolver) context);
        cloned.clearPersistentIdentity();

        if (Util.isNotNullOrEmpty(sql)) {
            cloned.getAttributes().put(ATTRIBUTE_SQL, sql);
        } else if (Util.isNotNullOrEmpty(sqlFilter)) {
            String existingSQL = cloned.getAttributes().getString(ATTRIBUTE_SQL);
            if (sqlFilter.toUpperCase().startsWith("AND ")) {
                String newFilter = existingSQL + " " + sqlFilter;
                cloned.getAttributes().put(ATTRIBUTE_SQL, newFilter);
            } else {
                // The alias is required by most databases other than Oracle; "AS" is omitted because Oracle rejects it
                String wrapped = "SELECT * FROM (" + existingSQL + ") " + SUBQUERY_ALIAS + " WHERE " + sqlFilter;
                cloned.getAttributes().put(ATTRIBUTE_SQL, wrapped);
            }
        } else if (this.partition != null) {
            List<String> partitionStatements = cloned.getAttributes().getStringList(ATTRIBUTE_PARTITION_STATEMENTS);
            if (partitionStatements == null || partitionStatements.isEmpty()) {
                throw new IllegalArgumentException("No partition statements found in application " + applicationName);
            }

            if (partition < 0 || partition >= partitionStatements.size()) {
                throw new IllegalArgumentException("Partition " + partition + " is out of range; application " + applicationName + " has " + partitionStatements.size() + " partition statements (0-based)");
            }

            String partitionSQL = partitionStatements.get(partition);
            cloned.getAttributes().put(ATTRIBUTE_SQL, partitionSQL);

            // The partition statement now runs as the main SQL, so it must use the partition's stored procedure setting
            cloned.getAttributes().put(ATTRIBUTE_USE_STORED_PROCEDURE_SQL, cloned.getAttributes().getBoolean(ATTRIBUTE_USE_STORED_PROCEDURE_PARTITION));
        } else {
            throw new GeneralException("No SQL replacement specified via replacementSQL, sqlFilter, or partition");
        }
        return cloned;
    }

    /**
     * Processes a ResourceObject in a separate thread, ideally in batches. The
     * threading is handled by the superclass, so this method only needs to do the
     * work to process a single object.
     *
     * @param threadContext A private IIQ context for the current JVM thread
     * @param parameters unused in this class
     * @param obj The object to process
     * @return An arbitrary value (ignored by default)
     * @throws GeneralException if any failures occur
     */
    @Override
    public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, ResourceObject obj) throws GeneralException {
        AccountUtilities accountUtilities = new AccountUtilities(threadContext);
        AccountUtilities.AggregateOptions options = new AccountUtilities.AggregateOptions();
        options.setResourceObject(obj);
        options.setApplicationName(applicationName);
        options.setAggregateOptions(aggregateArgs);
        options.setRefreshIdentity(false);
        options.setRunAppCustomization(false);

        AggregationOutcome outcome = accountUtilities.aggregateAccount(options);
        if (outcome == null) {
            throw new GeneralException("Aggregation of record " + obj.getIdentity() + " returned no outcome");
        }

        // Throwing here causes the superclass to count the record as a failure and add the error to the TaskResult
        if (outcome.getStatus() == OutcomeType.Failure || Util.isNotNullOrEmpty(outcome.getErrorMessage())) {
            throw new GeneralException("Aggregation error on record " + obj.getIdentity() + ": " + outcome.getErrorMessage());
        }

        TaskResult aggregatorResult = outcome.getTaskResult(threadContext);
        if (aggregatorResult != null && aggregatorResult.hasErrors()) {
            throw new GeneralException("Aggregation error on record " + obj.getIdentity() + ": " + aggregatorResult.getErrors());
        }

        if (outcome.getStatus() == OutcomeType.Warning || (aggregatorResult != null && aggregatorResult.hasWarnings())) {
            log.warn("Aggregation of record {0} completed with status {1}: {2}", obj.getIdentity(), outcome.getStatus(), aggregatorResult != null ? aggregatorResult.getWarnings() : outcome.getMessages());
        }

        return null;
    }
}
