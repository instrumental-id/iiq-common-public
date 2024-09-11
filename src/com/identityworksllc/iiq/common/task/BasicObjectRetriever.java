package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.Functions;
import com.identityworksllc.iiq.common.iterators.TransformingIterator;
import com.identityworksllc.iiq.common.query.ContextConnectionWrapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.*;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implements a reusable implementation of {@link ObjectRetriever} for use with any
 * number of scheduled tasks.
 *
 * This class takes the following input arguments in its constructor:
 *
 *  - values: If the input type is 'provided', these values will be interpreted as the raw input.
 *  - retrievalSql: A single-column SQL query that will produce a list of strings. The first column must be a string. Other columns will be ignored.
 *  - retrievalSqlClass: If specified, the single-column result from the query will be used as an ID look up the object with this type
 *  - retrievalConnector: The name of an Application; the iteration will be over a list of ResourceObjects as though an aggregation is running
 *  - retrievalFile: The fully qualified name of an existing, readable text file. Each line will be passed as a List of Strings to convertObject. If a delimiter is specified, the List will contain each delimited element. If not, the list will contain the entire line.
 *  - retrievalFileDelimiter: If specified, the file will be interpreted as a CSV; each line as a List will be passed to threadExecute
 *  - retrievalFileClass: If specified, the file will be interpreted as a CSV and the first column will be interpreted as an ID or name of this class type.
 *  - retrievalRule: The name of a Rule that will produce an Iterator over the desired objects
 *  - retrievalScript: The source of a Script that will produce an Iterator over the desired objects
 *  - retrievalFilter: A Filter string combined with retrievalFilterClass to do a search in SailPoint
 *  - retrievalFilterClass: The class name to search with the retrievalFilter
 *
 * You are expected to provide a {@link TransformingIterator} supplier that will convert
 * from the output type listed below to the desired type. If your TransformingIterator
 * does not produce the correct output, it is likely that you will run into ClassCastExceptions.
 * That is beyond the scope of this class.
 *
 * Default object types if you do not convert them afterwards are:
 *
 *  'provided':
 *    String
 *
 *  'sql':
 *    If a class is specified, instances of that class. If not, strings.
 *
 *  'connector':
 *    ResourceObject
 *
 *  'file':
 *    List of Strings
 *
 *  'rule' and 'script':
 *    Arbitrary. You are expected to return the right data type from your rule.
 *    You may return an IncrementalObjectIterator, an Iterator, an Iterable, or
 *    any other object (which will be wrapped in a singleton list).
 *
 *  'filter':
 *    An object of the specified filter class, or Identity if one is not specified.
 *    The iterator will wrap an IncrementalObjectIterator so that not all objects
 *    are loaded into memory at once.
 *
 * If you are using this class in a context that can be interrupted (e.g., in a job),
 * you will need to provide a way for this class to register a "termination handler"
 * by calling {@link #setTerminationRegistrar(Consumer)}. Your termination logic
 * MUST invoke any callbacks registered with your consumer.
 *
 * @param <ItemType>
 *
 * @author Devin Rosenbauer
 * @author Instrumental Identity
 */
public class BasicObjectRetriever<ItemType> implements ObjectRetriever<ItemType> {
    /**
     * The list of retrieval types
     */
    enum RetrievalType {
        rule,
        script,
        sql,
        filter,
        connector,
        file,
        provided
    }

    /**
     * The class logger
     */
    private final Log log;
    /**
     * The list of provided values
     */
    private List<String> providedValues;
    /**
     * The application used to retrieve any inputs
     */
    private Application retrievalApplication;
    /**
     * The file used to retrieve any inputs
     */
    private File retrievalFile;
    /**
     * Retrieval file delimiter
     */
    private String retrievalFileDelimiter;
    /**
     * The class to retrieve, assuming the default retriever is used
     */
    private Class<? extends SailPointObject> retrievalFileClass;
    /**
     * The filter used to retrieve any objects, assuming the default retriever is used
     */
    private Filter retrievalFilter;
    /**
     * The class to retrieve, assuming the default retriever is used
     */
    private Class<? extends SailPointObject> retrievalFilterClass;
    /**
     * A rule that returns objects to be iterated over in parallel
     */
    private Rule retrievalRule;
    /**
     * A script that returns objects to be iterated over in parallel
     */
    private Script retrievalScript;
    /**
     * A SQL query that returns IDs of objects to be iterated in parallel
     */
    private String retrievalSql;
    /**
     * The class represented by the SQL output
     */
    private Class<? extends SailPointObject> retrievalSqlClass;
    /**
     * The type of retrieval, which must be one of the defined values
     */
    private RetrievalType retrievalType;

    /**
     * The TaskResult of the running task
     */
    private final TaskResult taskResult;

    /**
     * The callback for registering termination handlers, set by setTerminationRegistrar
     */
    private Consumer<Functions.GenericCallback> terminationRegistrar;

    /**
     * The callback used to create the output iterator to the given type
     */
    private final Function<Iterator<?>, TransformingIterator<Object, ItemType>> transformerConstructor;

    /**
     * Constructs a new Basic object retriever
     * @param context The Sailpoint context to use for searching
     * @param arguments The arguments to the task (or other)
     * @param transformerConstructor The callback that creates a transforming iterator
     * @param taskResult The task result, or null
     * @throws GeneralException if any failures occur
     */
    public BasicObjectRetriever(SailPointContext context, Attributes<String, Object> arguments, Function<Iterator<?>, TransformingIterator<Object, ItemType>> transformerConstructor, TaskResult taskResult) throws GeneralException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(arguments);

        initialize(context, arguments);

        this.log = LogFactory.getLog(BasicObjectRetriever.class);
        this.transformerConstructor = transformerConstructor;
        this.taskResult = taskResult;
    }


    /**
     * Retrieves the contents of an input file
     * @return An iterator over the contents of the file
     * @throws IOException if there is an error reading the file
     * @throws GeneralException if there is an error doing anything Sailpoint-y
     */
    private Iterator<List<String>> getFileContents() throws IOException, GeneralException {
        Iterator<List<String>> items;
        List<List<String>> itemList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(retrievalFile)))) {
            RFC4180LineParser parser = null;
            if (Util.isNotNullOrEmpty(retrievalFileDelimiter)) {
                parser = new RFC4180LineParser(retrievalFileDelimiter);
            }
            String line;
            while((line = reader.readLine()) != null) {
                if (Util.isNotNullOrEmpty(line.trim())) {
                    if (parser != null) {
                        List<String> item = parser.parseLine(line);
                        itemList.add(item);
                    } else {
                        List<String> lineList = Collections.singletonList(line);
                        itemList.add(lineList);
                    }
                }
            }
        }
        items = itemList.iterator();
        return items;
    }

    /**
     * Gets the object iterator, which will be an instance of {@link TransformingIterator} to
     * convert whatever are the inputs. See the class Javadoc for information about what the
     * default return types from each query method are.
     *
     * @param context The context to use during querying
     * @param arguments Any additional query arguments
     * @return An iterator over the retrieved objects
     * @throws GeneralException if any failures occur
     */
    @Override
    @SuppressWarnings("unchecked")
    public Iterator<ItemType> getObjectIterator(SailPointContext context, Attributes<String, Object> arguments) throws GeneralException {
        Objects.requireNonNull(context);

        Map<String, Object> retrievalParams = new HashMap<>();
        retrievalParams.put("log", log);
        retrievalParams.put("context", context);
        retrievalParams.put("taskResult", taskResult);
        TransformingIterator<?, ItemType> items = null;
        if (retrievalType == RetrievalType.provided) {
            items = transformerConstructor.apply(providedValues.iterator());
        } else if (retrievalType == RetrievalType.rule) {
            if (retrievalRule == null) {
                throw new IllegalArgumentException("Retrieval rule must exist");
            }
            Object output = context.runRule(retrievalRule, retrievalParams);
            if (output instanceof IncrementalObjectIterator) {
                items = transformerConstructor.apply((IncrementalObjectIterator)output);
            } else if (output instanceof Iterator) {
                items = transformerConstructor.apply((Iterator)output);
            } else if (output instanceof Iterable) {
                items = transformerConstructor.apply(((Iterable)output).iterator());
            } else {
                items = transformerConstructor.apply(Collections.singletonList(output).iterator());
            }
        } else if (retrievalType == RetrievalType.script) {
            if (retrievalScript == null) {
                throw new IllegalArgumentException("A retrieval script must be defined with 'script' retrieval type");
            }
            if (log.isDebugEnabled()) {
                log.debug("Running retrieval script: " + retrievalScript);
            }
            Object output = context.runScript(retrievalScript, retrievalParams);
            if (output instanceof IncrementalObjectIterator) {
                items = transformerConstructor.apply((IncrementalObjectIterator)output);
            } else if (output instanceof Iterator) {
                items = transformerConstructor.apply((Iterator)output);
            } else if (output instanceof Iterable) {
                items = transformerConstructor.apply(((Iterable)output).iterator());
            } else {
                items = transformerConstructor.apply(Collections.singletonList(output).iterator());
            }
        } else if (retrievalType == RetrievalType.filter) {
            if (retrievalFilter == null || retrievalFilterClass == null) {
                throw new IllegalArgumentException("A retrieval filter and class name must be specified for 'filter' retrieval type");
            }
            QueryOptions options = new QueryOptions();
            options.addFilter(retrievalFilter);
            items = transformerConstructor.apply(new IncrementalObjectIterator<>(context, retrievalFilterClass, options));
        } else if (retrievalType == RetrievalType.file) {
            try {
                Iterator<List<String>> fileContents = getFileContents();
                if (retrievalFileClass != null) {
                    items = transformerConstructor.apply(new TransformingIterator<>(fileContents, (id) -> Util.isEmpty(id) ? null : (ItemType) context.getObject(retrievalFileClass, Util.otoa(id.get(0)))));
                } else {
                    items = transformerConstructor.apply(fileContents);
                }
            } catch (IOException e) {
                throw new GeneralException(e);
            }
        } else if (retrievalType == RetrievalType.sql) {
            List<String> objectNames = new ArrayList<>();
            // There is probably a more efficient way to do this, but it's not clear
            // how to automatically clean up the SQL resources afterwards...
            try (Connection connection = ContextConnectionWrapper.getConnection(context)) {
                try (PreparedStatement stmt = connection.prepareStatement(retrievalSql)) {
                    try (ResultSet results = stmt.executeQuery()) {
                        while(results.next()) {
                            String output = results.getString(1);
                            if (Util.isNotNullOrEmpty(output)) {
                                objectNames.add(output);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                throw new GeneralException(e);
            }
            if (retrievalSqlClass != null) {
                items = transformerConstructor.apply(new TransformingIterator<>(objectNames.iterator(), (id) -> (ItemType) context.getObject(retrievalSqlClass, id)));
            } else {
                items = transformerConstructor.apply(objectNames.iterator());
            }
        } else if (retrievalType == RetrievalType.connector) {
            if (retrievalApplication == null) {
                throw new IllegalArgumentException("You must specify an application for 'connector' retrieval type");
            }
            if (retrievalApplication.supportsFeature(Application.Feature.NO_AGGREGATION)) {
                throw new IllegalArgumentException("The application " + retrievalApplication.getName() + " does not support aggregation");
            }
            try {
                Connector connector = ConnectorFactory.getConnector(retrievalApplication, null);
                CloseableIterator<ResourceObject> objectIterator = connector.iterateObjects(retrievalApplication.getAccountSchema().getName(), null, new HashMap<>());
                if (terminationRegistrar != null) {
                    terminationRegistrar.accept((terminationContext) -> objectIterator.close());
                }
                return transformerConstructor.apply(new AbstractThreadedObjectIteratorTask.CloseableIteratorWrapper(objectIterator));
            } catch(ConnectorException e) {
                throw new GeneralException(e);
            }
        } else {
            throw new IllegalStateException("You must specify a rule, script, filter, sql, file, connector, or list to retrieve target objects");
        }
        items.ignoreNulls();
        return items;
    }

    /**
     * Returns true if the retriever is configured with a retrieval class appropriate
     * for the retrieval type, otherwise returns false.
     * @return True if a retrieval class is present
     */
    public boolean hasRetrievalClass() {
        if (getRetrievalType() == RetrievalType.file) {
            return this.retrievalFileClass != null;
        } else if (getRetrievalType() == RetrievalType.filter) {
            return this.retrievalFilterClass != null;
        } else if (getRetrievalType() == RetrievalType.sql) {
            return this.retrievalSqlClass != null;
        }
        return false;
    }

    /**
     * Gets the retrieval type
     * @return The retrieval type
     */
    public RetrievalType getRetrievalType() {
        return retrievalType;
    }

    /**
     * Initializes this retriever from the input parameters
     * @param context The context
     * @param args The input arguments, usually from a Task
     * @throws GeneralException if any Sailpoint-related failures occur
     * @throws IllegalArgumentException if any arguments are incorrect or missing
     */
    @SuppressWarnings("unchecked")
    private void initialize(SailPointContext context, Attributes<String, Object> args) throws GeneralException {
        this.retrievalType = RetrievalType.valueOf(args.getString("retrievalType"));

        if (retrievalType == RetrievalType.provided) {
            List<String> values = args.getStringList("values");
            if (values == null) {
                throw new IllegalArgumentException("For input type = provided, a CSV in 'values' is required");
            }

            this.providedValues = values;
        }

        if (retrievalType == RetrievalType.sql) {
            if (Util.isNullOrEmpty(args.getString("retrievalSql"))) {
                throw new IllegalArgumentException("For input type = sql, a retrievalSql is required");
            }

            String className = args.getString("retrievalSqlClass");
            if (Util.isNotNullOrEmpty(className)) {
                this.retrievalSqlClass = (Class<? extends SailPointObject>) ObjectUtil.getSailPointClass(className);
            }
            this.retrievalSql = args.getString("retrievalSql");
        }

        if (retrievalType == RetrievalType.connector) {
            String applicationName = args.getString("applicationName");
            this.retrievalApplication = context.getObject(Application.class, applicationName);
            if (this.retrievalApplication == null) {
                throw new IllegalArgumentException("The application " + applicationName + " does not exist");
            }
        }

        if (retrievalType == RetrievalType.file && Util.isNotNullOrEmpty(args.getString("retrievalFile"))) {
            String filename = args.getString("retrievalFile");
            this.retrievalFile = new File(filename);
            this.retrievalFileDelimiter = args.getString("retrievalFileDelimiter");
            String className = args.getString("retrievalFileClass");
            if (Util.isNotNullOrEmpty(className)) {
                this.retrievalFileClass = (Class<? extends SailPointObject>) ObjectUtil.getSailPointClass(className);
            }
            if (!this.retrievalFile.exists()) {
                throw new IllegalArgumentException("Input file " + filename + " does not exist");
            }
            if (!this.retrievalFile.isFile()) {
                throw new IllegalArgumentException("Input file " + filename + " does not appear to be a file (is it a directory?)");
            }
            if (!this.retrievalFile.canRead()) {
                throw new IllegalArgumentException("Input file " + filename + " is not readable by the IIQ process");
            }
        }

        if (retrievalType == RetrievalType.rule && Util.isNotNullOrEmpty(args.getString("retrievalRule"))) {
            this.retrievalRule = context.getObjectByName(Rule.class, args.getString("retrievalRule"));
        }

        if (retrievalType == RetrievalType.script && Util.isNotNullOrEmpty(args.getString("retrievalScript"))) {
            this.retrievalScript = new Script();
            retrievalScript.setSource(args.getString("retrievalScript"));
        }

        if (retrievalType == RetrievalType.filter && Util.isNotNullOrEmpty(args.getString("retrievalFilter"))) {
            this.retrievalFilter = Filter.compile(args.getString("retrievalFilter"));
            String className = args.getString("retrievalFilterClass");
            if (Util.isNotNullOrEmpty(className)) {
                this.retrievalFilterClass = (Class<? extends SailPointObject>) ObjectUtil.getSailPointClass(className);
            } else {
                log.warn("No retrieval filter class given; assuming Identity");
                this.retrievalFilterClass = Identity.class;
            }
        }
    }

    /**
     * Sets the termination registrar object
     * @param registar The termination registrar object
     */
    @Override
    public void setTerminationRegistrar(Consumer<Functions.GenericCallback> registar) {
        this.terminationRegistrar = registar;
    }
}
