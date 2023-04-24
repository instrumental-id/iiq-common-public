package com.identityworksllc.iiq.common.threads;

import com.identityworksllc.iiq.common.vo.Failure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A container for holding the results of a whole pool of workers. This makes
 * working with the outputs of pooled actions easier in Beanshell and other
 * similar contexts.
 *
 * @param <T> The type of the results expected
 */
public class PooledWorkerResults<T> {
    /**
     * The shared completion counter
     */
    private final AtomicInteger completed;

    /**
     * The shared failure counter
     */
    private final AtomicInteger failed;

    /**
     * The shared list of failures
     */
    private final List<Failure<T, ? extends Exception>> failures;

    /**
     * The flag indicating that the action has been interrupted
     */
    private boolean interrupted;

    /**
     * Creates a new pooled worker result container
     */
    public PooledWorkerResults() {
        this.completed = new AtomicInteger();
        this.failed = new AtomicInteger();
        this.failures = new ArrayList<>();
    }

    /**
     * Adds a failure to the output
     * @param f The failure object, containing the thing that failed and the exception
     */
    public void addFailure(Failure<T, ? extends Exception> f) {
        this.failures.add(f);
    }

    /**
     * Gets the completion counter
     * @return The completion counter
     */
    public AtomicInteger getCompleted() {
        return completed;
    }

    /**
     * Gets the failure counter
     * @return The failure counter
     */
    public AtomicInteger getFailed() {
        return failed;
    }

    /**
     * Gets the list of any failures associated with this pooled action
     * @return The list of failures
     */
    public List<Failure<T, ? extends Exception>> getFailures() {
        return failures;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    /**
     * Sets this action as interrupted
     * @param interrupted The interrupted flag
     */
    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }
}
