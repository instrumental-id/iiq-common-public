package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.CloseableIterator;

import java.util.Optional;
import java.util.Stack;

/**
 * A container that will auto-close its contents when it is itself closed.
 * Closeable items are stored in a {@link Stack} and will be closed in the
 * reverse order of being added. Errors will be logged and ignored.
 *
 * Items implementing {@link AutoCloseable} (most of them) and items implementing
 * {@link CloseableIterator} are stored separately.
 *
 * Example in Beanshell (with dummy methods):
 *
 * ```
 * try {
 *     Connection dbConnection = closer.add(getDatabaseConnection());
 *
 *     PreparedStatement stmt = closer.add(dbConnection.prepareStatement("select id from spt_identity where name = ?"));
 *     stmt.setString(1, someName);
 *
 *     ResultSet results = closer.add(stmt.executeQuery());
 *     while(results.next()) {
 *         // do stuff
 *     }
 * } finally {
 *     // All three objects are closed here, in reverse order
 *     closer.close();
 * }
 * ```
 *
 * @see AutoCloseable
 */
public class Closer implements AutoCloseable {
    /**
     * The logger
     */
    private static final Log log = LogFactory.getLog(Closer.class);

    /**
     * A list of items that will be auto-closed, in the reverse of the
     * order in which they were added
     */
    private final Stack<AutoCloseable> autocloseList;

    private final Stack<CloseableIterator<?>> closeableIteratorList;

    /**
     * Creates a new Closer container with an empty set of items
     */
    public Closer() {
        this.autocloseList = new Stack<>();
        this.closeableIteratorList = new Stack<>();
    }

    /**
     * Creates a new Closer containing the given item
     * @param cl1 The item to add
     */
    public Closer(AutoCloseable cl1) {
        this();
        add(cl1);
    }

    /**
     * Creates a new Closer containing the given items
     * @param cl1 The item to add
     * @param cl2 The item to add
     */
    public Closer(AutoCloseable cl1, AutoCloseable cl2) {
        this(cl1);
        add(cl2);
    }

    /**
     * Creates a new Closer containing the given items
     * @param cl1 The item to add
     * @param cl2 The item to add
     * @param cl3 The item to add
     */
    public Closer(AutoCloseable cl1, AutoCloseable cl2, AutoCloseable cl3) {
        this(cl1, cl2);
        add(cl3);
    }

    /**
     * Creates a new Closer containing the given items
     * @param cl1 The item to add
     * @param cl2 The item to add
     * @param cl3 The item to add
     * @param cl4 The item to add
     */
    public Closer(AutoCloseable cl1, AutoCloseable cl2, AutoCloseable cl3, AutoCloseable cl4) {
        this(cl1, cl2, cl3);
        add(cl4);
    }

    /**
     * Adds the {@link AutoCloseable} item to the stack to be closed when this container is closed
     * @param ac The auto-closeable item to add to the stack
     * @return The passed object, allowing you to open and add in the same line of code
     * @param <T> Some object extending AutoCloseable
     */
    public <T extends AutoCloseable> T add(T ac) {
        this.autocloseList.push(ac);
        return ac;
    }

    /**
     * Adds the {@link CloseableIterator} item to the stack to be closed when this container is closed
     * @param ci The CloseableIterator item to add to the stack
     * @return The passed object, allowing you to open and add in the same line of code
     */
    public CloseableIterator<?> add(CloseableIterator<?> ci) {
        this.closeableIteratorList.push(ci);
        return ci;
    }

    /**
     * Pops each item off the stack and calls close() on it. Any errors will be logged
     * and ignored. The list will be empty after this method is finished, allowing those
     * objects to be garbage-collected.
     *
     * @throws Exception If anything uncatchable fails
     */
    @Override
    public void close() throws Exception {
        while(!autocloseList.isEmpty()) {
            AutoCloseable ac = autocloseList.pop();
            try {
                ac.close();
            } catch(Exception e) {
                log.error("Unable to close item of type " + ac.getClass(), e);
            }
        }

        while(!closeableIteratorList.isEmpty()) {
            CloseableIterator<?> ci = closeableIteratorList.pop();
            try {
                ci.close();
            } catch(Exception e) {
                log.error("Unable to close item of type " + ci.getClass(), e);
            }
        }
    }

    /**
     * Extracts the managed object of the given type. For example, assuming that you
     * passed a {@link java.sql.Connection} into a {@link Closer}:
     *
     * ```
     * Connection conn = closer.get(Connection.class);
     * ```
     * @param requestedType The type of the requested class
     * @return An optional containing the object, if one matches
     * @param <T> The output object
     */
    public <T extends AutoCloseable> Optional<T> get(Class<T> requestedType) {
        for(AutoCloseable ac : this.autocloseList) {
            if (requestedType.isInstance(ac)) {
                return Optional.of(requestedType.cast(ac));
            }
        }

        return Optional.empty();
    }
}
