package com.identityworksllc.iiq.common.query;

import com.identityworksllc.iiq.common.iterators.ResultSetIterator;
import com.identityworksllc.iiq.common.threads.PooledWorkerResults;
import com.identityworksllc.iiq.common.threads.SailPointWorker;
import com.identityworksllc.iiq.common.vo.Failure;
import openconnector.Util;
import org.apache.commons.logging.Log;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplifies querying by automatically enclosing queries in a result processing loop.
 * This is copied from an older project. IIQ also provides a number of similar utilities
 * in their {@link sailpoint.tools.JdbcUtil} class.
 *
 * @param <T> The type returned from the result processor
 */
@SuppressWarnings("unused")
public class QueryUtil<T> {

    /**
     * Callback for processing the result
     *
     * @param <U> The type returned from the result processor, must extend T
     */
    @FunctionalInterface
    public interface ResultProcessor<U> {

        /**
         * Callback to indicate that the result set had no results
         *
         * @throws SQLException on failures
         */
        @SuppressWarnings("unused")
        default void noResult() throws SQLException {
            // Blank by default
        }

        /**
         * Called once per result. Do not call "next" on the ResultSet here.
         *
         * @param result The result set at the current point
         * @return An object of type T
         * @throws GeneralException on any Sailpoint failures
         * @throws SQLException on any database failures
         */
        U processResult(ResultSet result) throws GeneralException, SQLException;
    }

    /**
     * Static helper method to retrieve the first value from the result set as a long
     *
     * @param query The query to run
     * @param resultColumn The column to grab from the results
     * @param Log logger
     * @param parameters Any parameters
     * @return A list of result strings
     * @throws Throwable On failures
     */
    public static Long getLongValue(final String query, final String resultColumn, Log Log, Object... parameters) throws Throwable {
        return new QueryUtil<Long>(Log).getResult(query, result -> result.getLong(resultColumn), parameters);
    }

    /**
     * Retrieves the first string from the result set in the given column, then
     * queries for a sailPoint object of the given type based on that name or
     * ID. The column name is assumed to be 'id', which is the primary key in
     * most of the SailPoint tables.
     *
     * @param context The Sailpoint context to use to query
     * @param cls The class to query based on the ID being pulled
     * @param query The query to run
     * @param log logger
     * @param parameters Any parameters
     * @return A list of result strings
     * @throws Throwable On failures
     */
    public static <T extends SailPointObject> T getObjectByQuery(final SailPointContext context, Class<T> cls, final String query, final Log log, Object... parameters) throws Throwable {
        return getObjectByQuery(context, cls, query, "id", log, parameters);
    }

    /**
     * Retrieves the first string from the result set in the given column, then
     * queries for a sailPoint object of the given type based on that name or
     * ID.
     *
     * @param context The Sailpoint context to use to query
     * @param cls The class to query based on the ID being pulled
     * @param query The query to run
     * @param idColumn The column to grab from the results
     * @param log logger
     * @param parameters Any parameters
     * @return A list of result strings
     * @throws Throwable On failures
     */
    public static <T extends SailPointObject> T getObjectByQuery(final SailPointContext context, Class<T> cls, final String query, final String idColumn, final Log log, Object... parameters) throws Throwable {
        String id = getStringValue(query, idColumn, log, parameters);
        return context.getObject(cls, id);
    }

    /**
     * Static helper method ot retrieve values from the query as a list of strings
     *
     * @param query The query to run
     * @param resultColumn The column to grab from the results
     * @param Log logger
     * @param parameters Any parameters
     * @return A list of result strings
     * @throws Throwable On failures
     */
    public static List<String> getStringList(final String query, final String resultColumn, Log Log, Object... parameters) throws Throwable {
        return new QueryUtil<String>(Log).getResults(query, result -> result.getString(resultColumn), parameters);
    }

    /**
     * Static helper method to retrieve the first string value from the result set
     *
     * @param query The query to run
     * @param resultColumn The column to grab from the results
     * @param Log logger
     * @param parameters Any parameters
     * @return A list of result strings
     * @throws Throwable On failures
     */
    public static String getStringValue(final String query, final String resultColumn, Log Log, Object... parameters) throws Throwable {
        return new QueryUtil<String>(Log).getResult(query, result -> result.getString(resultColumn), parameters);
    }

    /**
     * Retrieves a unique object with the given Filter string
     * @param context The Sailpoint Context
     * @param cls The class to query for
     * @param filterString The Filter string
     * @param <T> The type of the object to query
     * @return The queried object
     * @throws GeneralException if too many or too few objects are found
     */
    public static <T extends SailPointObject> T getUniqueObject(SailPointContext context, Class<T> cls, String filterString) throws GeneralException {
        Filter filter = Filter.compile(filterString);
        return getUniqueObject(context, cls, filter);
    }

    /**
     * Retrieves a unique object with the given Filter string
     * @param context The Sailpoint Context
     * @param cls The class to query for
     * @param filter a Filter object
     * @param <T> The type of the object to query
     * @return The queried object
     * @throws GeneralException if too many or too few objects are found
     */
    public static <T extends SailPointObject> T getUniqueObject(SailPointContext context, Class<T> cls, Filter filter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(filter);
        return getUniqueObject(context, cls, qo);
    }

    /**
     * Retrieves a unique object with the given Filter string
     * @param context The Sailpoint Context
     * @param cls The class to query for
     * @param queryOptions a QueryOptions object which will be cloned before querying
     * @param <T> The type of the object to query
     * @return The queried object
     * @throws ObjectNotFoundException if no matches are found
     * @throws GeneralException if too many or too few objects are found
     */
    public static <T extends SailPointObject> T getUniqueObject(SailPointContext context, Class<T> cls, QueryOptions queryOptions) throws ObjectNotFoundException, GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.setFilters(queryOptions.getFilters());
        qo.setCacheResults(queryOptions.isCacheResults());
        qo.setDirtyRead(queryOptions.isDirtyRead());
        qo.setDistinct(queryOptions.isDistinct());
        qo.setFlushBeforeQuery(queryOptions.isFlushBeforeQuery());
        qo.setGroupBys(queryOptions.getGroupBys());
        qo.setOrderings(queryOptions.getOrderings());
        qo.setScopeResults(queryOptions.getScopeResults());
        qo.setTransactionLock(queryOptions.isTransactionLock());
        qo.setUnscopedGloballyAccessible(queryOptions.getUnscopedGloballyAccessible());

        qo.setResultLimit(2);

        List<T> results = context.getObjects(cls, qo);
        if (results == null || results.isEmpty()) {
            throw new ObjectNotFoundException();
        } else if (results.size() > 1) {
            throw new GeneralException("Expected a unique result, got " + results.size() + " results");
        }
        return results.get(0);
    }

    /**
     * Set up the given parameters in the prepared statmeent
     * @param stmt The statement
     * @param parameters The parameters
     * @throws SQLException if any failures occur setting parameters
     */
    public static void setupParameters(PreparedStatement stmt, Object[] parameters) throws SQLException {
        Parameters.setupParameters(stmt, parameters);
    }

    /**
     * Logger
     */
    private final Log logger;
    /**
     * Connection to SailPoint
     */
    private final SailPointContext sailPointContext;

    /**
     * Constructor
     * @param Log logger
     * @throws GeneralException if there is an error getting the current thread's SailPoint context
     */
    public QueryUtil(@SuppressWarnings("unused") Log Log) throws GeneralException {
        this(SailPointFactory.getCurrentContext(), Log);
    }

    /**
     * Constructor
     * @param context The SailPoint context
     * @param log The logger
     */
    public QueryUtil(SailPointContext context, Log log) {
        this.sailPointContext = context;
        this.logger = log;
    }

    /**
     * @param query The query to execute
     * @param processor The class to call for every iteration of the loop
     * @param parameters The parameters for the query, if any
     * @return A list of results output by the ResultProcessor
     * @throws GeneralException on any Sailpoint failures
     * @throws SQLException on any database failures
     */
    public T getResult(String query, ResultProcessor<? extends T> processor, Object... parameters) throws GeneralException, SQLException {
        logger.debug("Query = " + query);
        T output = null;
        try (Connection conn = ContextConnectionWrapper.getConnection(sailPointContext)) {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                setupParameters(stmt, parameters);
                try (ResultSet results = stmt.executeQuery()) {
                    if (results.next()) {
                        output = processor.processResult(results);
                    } else {
                        processor.noResult();
                    }
                }
            }
        }
        return output;
    }

    /**
     * @param query The query to execute
     * @param processor The class to call for every iteration of the loop
     * @param parameters The parameters for the query, if any
     * @return A list of results output by the ResultProcessor
     * @throws GeneralException on any Sailpoint failures
     * @throws SQLException on any database failures
     */
    public List<T> getResults(String query, ResultProcessor<? extends T> processor, Object... parameters) throws GeneralException, SQLException {
        logger.debug("Query = " + query);
        List<T> output = new ArrayList<>();
        try (Connection conn = ContextConnectionWrapper.getConnection(sailPointContext)) {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                setupParameters(stmt, parameters);
                try (ResultSet results = stmt.executeQuery()) {
                    boolean hasResults = false;
                    while (results.next()) {
                        hasResults = true;
                        output.add(processor.processResult(results));
                    }
                    if (!hasResults) {
                        processor.noResult();
                    }
                }
            }
        }
        return output;
    }

    /**
     * Iterates a query by invoking a Beanshell callback for each method
     * @param inputs The inputs
     * @throws GeneralException if anything goes wrong
     */
    public void iterateQuery(IterateQueryOptions inputs) throws GeneralException {
        try (Connection connection = inputs.openConnection()) {
            try (NamedParameterStatement stmt = new NamedParameterStatement(connection, inputs.getQuery())) {
                if (!Util.isEmpty(inputs.getQueryParams())) {
                    stmt.setParameters(inputs.getQueryParams());
                }

                try (ResultSet results = stmt.executeQuery()) {
                    ResultSetMetaData rsmd = results.getMetaData();
                    List<String> columns = new ArrayList<>();
                    for(int c = 1; c <= rsmd.getColumnCount(); c++) {
                        columns.add(rsmd.getColumnLabel(c));
                    }
                    ResultSetIterator rsi = new ResultSetIterator(results, columns, sailPointContext);
                    while(rsi.hasNext()) {
                        Map<String, Object> row = rsi.nextRow();
                        inputs.doCallback(row);
                    }
                }
            }
        } catch(SQLException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Iterates over a query in parallel, making a call to the defined callback
     * in the input options. (NOTE: Beanshell is explicitly thread-safe, but you
     * should use the thread context provided and you should not access shared
     * resources without doing your own thread-safety stuff.)
     *
     * @param inputs The input options
     * @param threads The number of threads to use
     * @throws GeneralException if anything fails
     */
    public PooledWorkerResults<Map<String, Object>> parallelIterateQuery(IterateQueryOptions inputs, int threads) throws GeneralException {
        PooledWorkerResults<Map<String, Object>> resultContainer = new PooledWorkerResults<>();

        ExecutorService executor = Executors.newWorkStealingPool(threads);
        logger.info("Starting worker pool with " + threads + " threads");

        try (Connection connection = inputs.openConnection()) {
            try (NamedParameterStatement stmt = new NamedParameterStatement(connection, inputs.getQuery())) {
                if (!Util.isEmpty(inputs.getQueryParams())) {
                    stmt.setParameters(inputs.getQueryParams());
                }

                try (ResultSet results = stmt.executeQuery()) {
                    ResultSetMetaData rsmd = results.getMetaData();
                    List<String> columns = new ArrayList<>();
                    for(int c = 1; c <= rsmd.getColumnCount(); c++) {
                        columns.add(rsmd.getColumnLabel(c));
                    }
                    ResultSetIterator rsi = new ResultSetIterator(results, columns, sailPointContext);
                    while(rsi.hasNext() && Thread.currentThread().isInterrupted()) {
                        final Map<String, Object> row = new HashMap<>(rsi.nextRow());

                        SailPointWorker worker = setupWorker(inputs, resultContainer, row);

                        executor.submit(worker.runnable());
                    }

                    if (Thread.currentThread().isInterrupted()) {
                        executor.shutdownNow();
                        resultContainer.setInterrupted(true);
                    } else {
                        executor.shutdown();
                        while (!executor.isTerminated()) {
                            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
                            if (!terminated) {
                                logger.debug("Waiting for thread pool to complete...");
                            }
                        }
                    }
                }
            }
        } catch(SQLException | InterruptedException e) {
            resultContainer.setInterrupted(true);
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
            throw new GeneralException(e);
        }

        return resultContainer;
    }

    /**
     * Sets up the parallel SailPointWorker that will invoke the callback and do
     * appropriate error handling.
     *
     * @param inputs The original query inputs
     * @param resultContainer The results container for reporting results
     * @param row The output row
     * @return if any failures occur
     */
    private SailPointWorker setupWorker(IterateQueryOptions inputs, PooledWorkerResults<Map<String, Object>> resultContainer, Map<String, Object> row) {
        SailPointWorker.ExceptionHandler exceptionHandler = t -> {
            logger.error("Caught an error processing result row", t);
            if (t instanceof Exception) {
                resultContainer.addFailure(new Failure<>(row, (Exception) t));
            }
        };

        SailPointWorker worker = new SailPointWorker() {
            @Override
            public Object execute(SailPointContext context, Log logger) throws Exception {
                inputs.doParallelCallback(context, row);
                return null;
            }
        };

        worker.setExceptionHandler(exceptionHandler);
        worker.setCompletedCounter(resultContainer.getCompleted());
        worker.setFailedCounter(resultContainer.getFailed());
        return worker;
    }

    /**
     * Run an update statement against the database directly (use sparingly)
     *
     * @param query The query to execute
     * @param parameters The parameters to include in the query
     * @return The return from executeUpdate()
     * @throws Exception On failures
     */
    public int update(String query, Object... parameters) throws Exception {
        logger.debug("Query = " + query);
        try (Connection conn = ContextConnectionWrapper.getConnection(sailPointContext)) {
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                setupParameters(stmt, parameters);
                return stmt.executeUpdate();
            }
        }
    }
}
