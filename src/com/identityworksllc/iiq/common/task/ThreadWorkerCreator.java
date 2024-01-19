package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.threads.SailPointWorker;

/**
 * A functional interface for generating a SailPointWorker from a given
 * set of objects, a consumer for those objects, and a taskContext for
 * doing updates.
 *
 * @param <T> the type of the object
 */
@FunctionalInterface
public interface ThreadWorkerCreator<T> {
    /**
     * Creates a new worker which should be runnable in either a thread or a partition
     * for the given list of objects.
     *
     * @param objects The objects in question
     * @param consumer The private context consumer
     * @param taskContext The task context
     * @return The constructed worker
     */
    SailPointWorker createWorker(Iterable<T> objects, PrivateContextObjectConsumer<T> consumer, ThreadedTaskListener<T> taskContext);
}
