package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.logging.SLogger;
import com.identityworksllc.iiq.common.threads.SailPointWorker;
import org.apache.commons.logging.Log;
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The worker for handling each input object. The input type is always a list
 * of the given objects, to support batching, but for non-batched situations,
 * the list may be of length 1.
 *
 * None of the inputs may be null.
 */
public class ThreadExecutorWorker<T> extends SailPointWorker {
    /**
     * The ultimate consumer of each item. In the default setup, this will
     * invoke {@link AbstractThreadedTask#threadExecute(SailPointContext, Map, Object)},
     * but consumers can do whatever they want.
     */
    private final PrivateContextObjectConsumer<T> consumer;

    /**
     * The objects to iterate in this thread
     */
    private final Iterable<T> objects;

    /**
     * The task context, used to check status, increment counters, etc
     */
    private final ThreadedTaskListener<T> taskListener;

    /**
     * Basic constructor, corresponds to {@link ThreadWorkerCreator}.
     *
     * None of the inputs may be null.
     *
     * @param objects The objects to iterate over
     * @param consumer The consumer of those objects (i.e., who is implementing threadExecute)
     * @param taskContext The task context
     */
    public ThreadExecutorWorker(Iterable<T> objects, PrivateContextObjectConsumer<T> consumer, ThreadedTaskListener<T> taskContext) {
        this.objects = Objects.requireNonNull(objects);
        this.consumer = Objects.requireNonNull(consumer);
        this.taskListener = Objects.requireNonNull(taskContext);
    }

    /**
     * Invokes {@link PrivateContextObjectConsumer#threadExecute(SailPointContext, Map, Object)} for
     * each object in the list. Also invokes a variety of callbacks via the taskContext.
     *
     * @param threadContext The thread context
     * @param logger        The log attached to this Worker
     * @return always null
     * @throws InterruptedException if the thread has been interrupted
     */
    @Override
    public Object execute(SailPointContext threadContext, Log logger) throws InterruptedException {
        SLogger slogger = new SLogger(logger);
        boolean skip = false;
        if (!taskListener.isTerminated()) {
            try {
                taskListener.beforeBatch(threadContext);
            } catch(GeneralException e) {
                skip = true;
                logger.error("Caught an error invoking beforeBatch; skipping batch", e);
            }
            if (!skip) {
                for (T in : objects) {
                    taskListener.beforeExecution(Thread.currentThread(), in);

                    if (Thread.interrupted() || taskListener.isTerminated()) {
                        throw new InterruptedException("Thread interrupted");
                    }
                    Map<String, Object> args = new HashMap<>();
                    args.put("context", threadContext);
                    args.put("log", slogger);
                    args.put("logger", slogger);
                    args.put("object", in);
                    args.put("worker", this);
                    args.put("taskListener", this.taskListener);
                    args.put("monitor", this.monitor);
                    try {
                        consumer.threadExecute(threadContext, args, in);
                        taskListener.handleSuccess(in);
                        threadContext.commitTransaction();
                    } catch (Exception e) {
                        taskListener.handleException(e);
                        taskListener.handleFailure(in);
                    }
                }
            }
        }

        if (!skip) {
            try {
                taskListener.afterBatch(threadContext);
            } catch (GeneralException e) {
                logger.error("Caught an error invoking afterBatch", e);
            }
        }
        return null;
    }
}
