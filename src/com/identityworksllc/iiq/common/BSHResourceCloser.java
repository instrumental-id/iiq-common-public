package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.CloseableIterator;

/**
 * An interface to implement the "try with resources" type of structure
 * in Beanshell, which does not support it. You should create an anonymous
 * inner class extending {@link BSHResourceCloser}, then pass it to {@link #execute(BSHResourceCloser)}.
 *
 * The open() method will be called and should produce an AutoCloseable
 * resource. After that, the run() method will be invoked with the
 * opened resource. If you need more than one thing to be opened, you
 * may want to return a {@link Closer}.
 *
 * If your resource is AutoCloseable, its close() method will be invoked.
 * If not, you must implement close() in your class.
 *
 * You may
 *
 * Example:
 *
 * ```
 * BSHResourceCloser.execute(new BSHResourceCloser() {
 *     public Object open() {
 *         // Open a connection or something
 *     }
 * });
 *
 * @param <T> The resource type
 * @param <U> The return type
 */
public abstract class BSHResourceCloser<T, U> {

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(BSHResourceCloser.class);

    /**
     * Executes the given Beanshell method with the resource, closing it once
     * the method has completed.
     *
     * @param resource The resource to use
     * @param bshThis The 'this' reference in your Beanshell script
     * @param methodName The method name to invoke with the resource
     * @param <T> The type of the resource
     * @return The result of the Beanshell method
     * @throws Exception if anything fails
     */
    public static <T> Object execute(T resource, bsh.This bshThis, String methodName) throws Exception {
        BSHResourceCloser<T, Object> closer = new BSHResourceCloser<T, Object>() {
            @Override
            public T open() throws Exception {
                return resource;
            }

            @Override
            public Object run(T rin) throws Exception {
                Object[] inputs = new Object[1];
                inputs[0] = rin;
                return bshThis.invokeMethod(methodName, inputs);
            }
        };

        return execute(closer);
    }

    /**
     * Executes your action after getting the resource required
     *
     * @param closer The resource closer
     * @param <T> The resource type
     * @param <U> The return type
     * @return The return from your run() method
     * @throws Exception if the resource open or the call fails
     */
    public static <T, U> U execute(BSHResourceCloser<T, U> closer) throws Exception {
        T resource = closer.open();
        try {
            return closer.run(resource);
        } finally {
            try {
                if (resource instanceof AutoCloseable) {
                    ((AutoCloseable) resource).close();
                } else if (resource instanceof CloseableIterator) {
                    ((CloseableIterator<?>) resource).close();
                } else {
                    closer.close(resource);
                }
            } catch(Exception e) {
                log.error("Error closing resource", e);
            }
        }
    }

    /**
     * Closes the given resource. This will be invoked if the resource does
     * not implement AutoCloseable.
     *
     * @param resource the resource to close
     */
    public void close(T resource) {
        /* Nothing by default */
        log.warn("Default implementation BSHResourceCloser.close() invoked. This means that the resource is not AutoCloseable and you must implement close() yourself.");
    }

    /**
     * Opens the resource for this action
     * @return The resource
     * @throws Exception if anything fails
     */
    public abstract T open() throws Exception;

    /**
     * Executes the action, passing the resource returned from open()
     * @param resource The resource to use
     * @return An arbitrary return value
     * @throws Exception If the action fails for some reason
     */
    public abstract U run(T resource) throws Exception;
}
