package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.Closeable;
import java.sql.Connection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Utilities to quietly (i.e. without throwing an exception) invoke certain common
 * operations. This prevents the need to write try/catch boilerplate. In most cases, the
 * methods will return a sensible default (either null or an empty value) when
 * the operation fails.
 *
 * Exceptions will be logged at info level.
 */
public class Quietly {
    /**
     * Logger for this class
     */
    private static final Log logger = LogFactory.getLog(Quietly.class);

    /**
     * Closes the thing quietly
     * @param thing The thing to close
     */
    public static void close(Closeable thing) {
        invoke(thing, Closeable::close);
    }

    /**
     * Closes the thing quietly
     * @param thing The thing to close
     */
    public static void close(AutoCloseable thing) {
        invoke(thing, AutoCloseable::close);
    }

    /**
     * Quietly gets a Sailpoint object
     *
     * @see SailPointContext#getObject(Class, String)
     */
    public static <SpoType extends SailPointObject> SpoType getObject(SailPointContext context, Class<SpoType> objectType, String idOrName) {
        if (Util.isNotNullOrEmpty(idOrName)) {
            try {
                return context.getObject(objectType, idOrName);
            } catch (GeneralException e) {
                logger.info("Caught an exception in Quietly.getObject", e);
            }
        }
        return null;
    }

    /**
     * Quietly gets a Sailpoint object
     *
     * @see SailPointContext#getObjectById(Class, String)
     */
    public static <SpoType extends SailPointObject> SpoType getObjectById(SailPointContext context, Class<SpoType> objectType, String id) {
        if (Util.isNotNullOrEmpty(id)) {
            try {
                return context.getObjectById(objectType, id);
            } catch (GeneralException e) {
                logger.info("Caught an exception in Quietly.getObjectById", e);
            }
        }
        return null;
    }

    /**
     * Quietly gets a Sailpoint object
     *
     * @see SailPointContext#getObjectByName(Class, String)
     */
    public static <SpoType extends SailPointObject> SpoType getObjectByName(SailPointContext context, Class<SpoType> objectType, String name) {
        if (Util.isNotNullOrEmpty(name)) {
            try {
                return context.getObjectByName(objectType, name);
            } catch (GeneralException e) {
                logger.info("Caught an exception in Quietly.getObjectByName", e);
            }
        }
        return null;
    }

    /**
     * Performs the action, logging and swallowing any exceptions
     * @param input The input object
     * @param action The action to perform on the input object
     * @param <T> The type of the input object
     */
    public static <T> void invoke(T input, Functions.ConsumerWithError<T> action) {
        try {
            action.acceptWithError(input);
        } catch(Throwable t) {
            logger.error("Caught an exception in doQuietly", t);
            if (t instanceof Error) {
                throw (Error)t;
            }
        }
    }

    /**
     * Performs the action, logging and swallowing any exceptions
     * @param input The input object
     * @param action The action to perform on the input object
     * @param <In> The type of the input object
     */
    public static <In, In2, Out> Out invokeWithOutput(In input, In2 input2, Functions.BiFunctionWithError<In, In2, Out> action) {
        try {
            return action.applyWithError(input, input2);
        } catch(Throwable t) {
            logger.error("Caught an exception in doQuietly", t);
            if (t instanceof Error) {
                throw (Error)t;
            }
        }
        return null;
    }

    /**
     * Performs the action, logging and swallowing any exceptions
     * @param input The input object
     * @param action The action to perform on the input object
     * @param <In> The type of the input object
     */
    public static <In, Out> Out invokeWithOutput(In input, Functions.FunctionWithError<In, Out> action) {
        try {
            return action.applyWithError(input);
        } catch(Throwable t) {
            logger.error("Caught an exception in doQuietly", t);
            if (t instanceof Error) {
                throw (Error)t;
            }
        }
        return null;
    }

    /**
     * Rolls back the connection quietly
     * @param connection The conection to roll back
     */
    public static void rollback(Connection connection) {
        invoke(connection, Connection::rollback);
    }

    /**
     * Quietly searches for and returns an iterator of Sailpoint objects. If this
     * set is expected to be large, you should use IncrementalObjectIterator instead.
     *
     * @see SailPointContext#search(Class, QueryOptions)
     */
    public static <SpoType extends SailPointObject> Iterator<SpoType> search(SailPointContext context, Class<SpoType> objectType, QueryOptions qo) {
        try {
            Iterator<SpoType> iterator = context.search(objectType, qo);
            if (iterator != null) {
                return iterator;
            }
        } catch(GeneralException e) {
            logger.info("Caught an exception in Quietly.search", e);
        }
        return Collections.emptyIterator();
    }

    /**
     * Quietly searches for and returns an iterator of Sailpoint objects
     *
     * @see SailPointContext#search(Class, QueryOptions)
     */
    public static <SpoType extends SailPointObject> Iterator<Object[]> search(SailPointContext context, Class<SpoType> objectType, QueryOptions qo, List<String> columns) {
        try {
            Iterator<Object[]> iterator = context.search(objectType, qo, columns);
            if (iterator != null) {
                return iterator;
            }
        } catch(GeneralException e) {
            logger.info("Caught an exception in Quietly.search", e);
        }
        return Collections.emptyIterator();
    }

}
