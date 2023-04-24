package com.identityworksllc.iiq.common;

import sailpoint.tools.GeneralException;

import java.io.Serializable;

/**
 * A generic task callback that can be used in various contexts. A subclass
 * can provide hooks for any part of the task process.
 *
 * @param <T> The type of the task object
 * @param <O> The type of the output object
 */
public abstract class TaskCallback<T, O> implements Serializable {
    /**
     * Invoked in a 'finally' after the task completes, but before it returns
     * to the orchestrator
     *
     * @param task The task object
     */
    public void afterFinish(T task) {
        /* Nothing by default */
    }

    /**
     * Invoked prior to the start of the task
     * @param task The task that is about to start
     */
    public void beforeStart(T task) throws GeneralException {
        /* Nothing by default */
    }

    /**
     * Invoked after the task has failed
     *
     * @param task The task that has failed
     * @param failureMessage A failure message (possibly null)
     * @param exception The exception that caused the failure
     */
    public void onFailure(T task, String failureMessage, Exception exception) {
        /* Nothing by default */
    }

    /**
     * Invoked after the task has completed
     *
     * @param task The task
     * @param output The output
     */
    public void onSuccess(T task, O output) {
        /* Nothing by default */
    }
}
