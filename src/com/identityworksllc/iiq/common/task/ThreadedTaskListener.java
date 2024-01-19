package com.identityworksllc.iiq.common.task;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * An interface allowing {@link AbstractThreadedTask} jobs to communicate
 * with their threads without exposing the entire task model.
 *
 * The thread runner can override any or all of the methods.
 *
 * @param <T> The type of item accepted by the task
 */
public abstract class ThreadedTaskListener<T> {

    /**
     * Returns a default no-op threaded task context
     * @param <X> The expected type (usually inferred)
     * @return The resulting no-op threaded task context
     */
    public static <X> ThreadedTaskListener<X> defaultThreadedTaskContext() {
        return new ThreadedTaskListener<X>() {

        };
    }

    /**
     * Invoked by the default worker thread after each batch is completed.
     * This can be overridden by a subclass to do arbitrary cleanup.
     *
     * @param threadContext The context for this thread
     * @throws GeneralException if anything goes wrong
     */
    public void afterBatch(SailPointContext threadContext) throws GeneralException {
        /* No-op by default */
    }

    /**
     * Invoked by the default worker thread before each batch is begun. If this
     * method throws an exception, the batch worker ought to prevent the batch
     * from being executed.
     *
     * @param threadContext The context for this thread
     * @throws GeneralException if any failures occur
     */
    public void beforeBatch(SailPointContext threadContext) throws GeneralException {
        /* No-op by default */
    }

    /**
     * A hook that can be invoked by a worker before the action takes place
     * @param theThread The currently executing thread
     * @param input The input record being processed
     */
    public void beforeExecution(Thread theThread, T input) {

    }

    /**
     * Handles an exception during execution
     * @param e the exception
     */
    public void handleException(Exception e) {

    }

    /**
     * Handles a failure during execution on the given item
     * @param input The input
     */
    public void handleFailure(T input) {

    }

    /**
     * Handles successful completion of execution on the given item
     * @param input The input
     */
    public void handleSuccess(T input) {

    }

    /**
     * Returns true if the parent task has been terminated
     * @return Terminated status
     */
    public boolean isTerminated() {
        return false;
    }
}
