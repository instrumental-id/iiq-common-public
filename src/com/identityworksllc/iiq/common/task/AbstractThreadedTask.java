package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.CommonConstants;
import com.identityworksllc.iiq.common.Functions;
import com.identityworksllc.iiq.common.iterators.BatchingIterator;
import com.identityworksllc.iiq.common.iterators.TransformingIterator;
import com.identityworksllc.iiq.common.threads.SailPointWorker;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An abstract superclass for nearly all custom multi-threaded SailPoint tasks. This task
 * will retrieve a list of objects and then pass each of them to a processor method
 * in the subclass in parallel.
 *
 * The overall flow is:
 *
 *  1. Invoke {@link #parseArgs(Attributes)} to extract task arguments. Subclasses should
 *     override this to retrieve their own parameters.
 *  2. Invoke {@link #getObjectIterator(SailPointContext, Attributes)} to retrieve a list of
 *     items to iterate over. This iterator can be "streaming" or not.
 *  3. Invoke {@link #submitAndWait(SailPointContext, TaskResult, Iterator)} to begin processing
 *     of the list of items. This can be replaced by a subclass, but the default flow is described
 *     below. It is unlikely that you will need to change it.
 *  4. Clean up the thread pool and update the TaskResult with outcomes.
 *
 * Submit-and-wait proceeds as follows:
 *
 *  1. Retrieve the batch size from {@link #getBatchSize()}
 *  2. Create a thread pool with the specified number of threads in it.
 *  3. For each item, invoke the subclass's {@link #threadExecute(SailPointContext, Map, T)} method,
 *     passing the current item in the third parameter. If a batch size is set, more than one
 *     item will be passed in a single worker thread, eliminating the need to build and destroy
 *     lots of private contexts. This will likely be more efficient for large operations.
 *
 * Via the {@link SailPointWorker} class, the {@link #threadExecute(SailPointContext, Map, T)} method
 * will also receive an appropriately thread-specific SailPointContext object that can be used without
 * worrying about transaction length or overlap.
 *
 * Subclasses can override most parts of this process by extending the various protected methods.
 *
 * Subclasses cannot direct receive a termination notification, but can register any number of
 * termination handlers by invoking {@link #addTerminationHandler(Functions.GenericCallback)}.
 * This class makes a best effort to invoke all termination handlers.
 *
 * @param <T> The type of input object that will be passed to threadExecute.
 */
public abstract class AbstractThreadedTask<T> extends AbstractTaskExecutor implements PrivateContextObjectConsumer<T> {

    /**
     * The default threaded task listener
     */
    private class DefaultThreadedTaskListener extends ThreadedTaskListener<T> {
        /**
         * The task result to update with output
         */
        private final TaskResult taskResult;

        public DefaultThreadedTaskListener(TaskResult taskResult) {
            this.taskResult = taskResult;
        }

        @Override
        public void afterBatch(SailPointContext threadContext) throws GeneralException {
            AbstractThreadedTask.this.beforeBatch(threadContext);
        }

        @Override
        public void beforeBatch(SailPointContext taskContext) throws GeneralException {
            AbstractThreadedTask.this.beforeBatch(taskContext);
        }

        @Override
        public void beforeExecution(Thread theThread, T input) {
            if (beforeExecutionHook != null) {
                beforeExecutionHook.accept(theThread, input);
            }
        }

        @Override
        public void handleException(Exception e) {
            taskResult.addException(e);
        }

        @Override
        public void handleFailure(T input) {
            failureMarker.accept(input);
        }

        @Override
        public void handleSuccess(T input) {
            successMarker.accept(input);
        }

        @Override
        public boolean isTerminated() {
            return AbstractThreadedTask.this.terminated.get();
        }
    }

    /**
     * The batch size, which may be zero for no batching
     */
    private int batchSize;
    /**
     * If present, this BiConsumer can be invoked before execution of each object.
     * The subclass is responsible for making this call. This is mainly useful as
     * a testing hook.
     */
    protected BiConsumer<Thread, Object> beforeExecutionHook;
    /**
     * The parent SailPoint context
     */
    protected SailPointContext context;
    /**
     * The thread pool
     */
    protected ExecutorService executor;
    /**
     * The counter of how many threads have indicated failures
     */
    protected AtomicInteger failureCounter;
    /**
     * The callback on failed execution for each item
     */
    private Consumer<T> failureMarker;
    /**
     * The log object
     */
    protected Log log;
    /**
     * The counter of how many threads have indicated success
     */
    protected AtomicInteger successCounter;
    /**
     * The callback on successful execution for each item
     */
    private Consumer<T> successMarker;
    /**
     * The TaskResult to keep updated with changes
     */
    protected TaskResult taskResult;

    /**
     * The TaskSchedule, which can be used in querying
     */
    protected TaskSchedule taskSchedule;
    /**
     * The boolean flag indicating that this task has been terminated
     */
    protected final AtomicBoolean terminated;
    /**
     * A set of callbacks to run on task termination
     */
    private final List<Functions.GenericCallback> terminationHandlers;
    /**
     * How many threads are to be created
     */
    protected int threadCount;

    /**
     * A way to override creation of the thread workers
     */
    private ThreadWorkerCreator<T> workerCreator;

    public AbstractThreadedTask() {
        this.terminated = new AtomicBoolean(false);
        this.successCounter = new AtomicInteger(0);
        this.failureCounter = new AtomicInteger(0);
        this.terminationHandlers = new ArrayList<>();
    }

    /**
     * Adds a termination handler to this execution of the task
     * @param handler The termination handler to run on completion
     */
    protected final void addTerminationHandler(Functions.GenericCallback handler) {
        this.terminationHandlers.add(handler);
    }

    /**
     * Invoked by the default worker thread after each batch is completed.
     * This can be overridden by a subclass to do arbitrary cleanup.
     *
     * @param context The context for this thread
     * @throws GeneralException if anything goes wrong
     */
    public void afterBatch(SailPointContext context) throws GeneralException {
        /* No-op by default */
    }

    /**
     * Invoked by the default worker thread before each batch is begun. If this
     * method throws an exception, the batch worker ought to prevent the batch
     * from being executed.
     *
     * @param context The context for this thread
     * @throws GeneralException if any failures occur
     */
    public void beforeBatch(SailPointContext context) throws GeneralException {
        /* No-op by default */
    }

    /**
     * The main method of this task executor, which invokes the appropriate hook methods.
     */
    @Override
    public final void execute(SailPointContext ctx, TaskSchedule ts, TaskResult tr, Attributes<String, Object> args) throws Exception {
        TaskMonitor monitor = new TaskMonitor(ctx, tr);

        this.terminated.set(false);
        this.successCounter.set(0);
        this.failureCounter.set(0);
        this.terminationHandlers.clear();

        this.workerCreator = ThreadExecutorWorker::new;
        this.failureMarker = this::markFailure;
        this.successMarker = this::markSuccess;

        this.log = LogFactory.getLog(this.getClass());
        this.context = ctx;
        this.taskResult = tr;
        this.taskSchedule = ts;

        monitor.updateProgress("Parsing input arguments");
        monitor.commitMasterResult();

        parseArgs(args);

        monitor.updateProgress("Retrieving target objects");
        monitor.commitMasterResult();

        Iterator<? extends T> items = getObjectIterator(ctx, args);
        if (items != null) {
            monitor.updateProgress("Processing target objects");
            monitor.commitMasterResult();

            submitAndWait(ctx, taskResult, items);

            if (!terminated.get()) {
                monitor.updateProgress("Invoking termination handlers");
                monitor.commitMasterResult();

                runTerminationHandlers();
            }
        }
    }

    /**
     * Retrieves an iterator over batches of items, with the size suggested by the second
     * parameter. If left unmodified, returns either a {@link BatchingIterator} when the
     * batch size is greater than 1, or a {@link TransformingIterator} that constructs a
     * singleton list for each item when batch size is 1.
     *
     * If possible, the returned Iterator should simply wrap the input, rather than
     * consuming it. This allows for "live" iterators that read from a data source
     * directly rather than pre-reading. However, beware Hibernate iterators here
     * because a 'commit' can kill those mid-iterate.
     *
     * @param items The input iterator of items
     * @param batchSize The batch size
     * @return The iterator over a list of items
     */
    protected Iterator<List<T>> createBatchIterator(Iterator<? extends T> items, int batchSize) {
        Iterator<List<T>> batchingIterator;
        if (batchSize > 1) {
            // Batching iterator will combine items into lists of up to batchSize
            batchingIterator = new BatchingIterator<>(items, batchSize);
        } else {
            // This iterator will just transform each item into a list containing only that item
            batchingIterator = new TransformingIterator<T, List<T>>(items, Collections::singletonList);
        }
        return batchingIterator;
    }

    /**
     * Gets the batch size for this task. By default, this is the batch size passed
     * as an input to the task, but this may be overridden by subclasses.
     *
     * @return The batch size for each thread
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Gets the running executor for this task
     * @return The executor
     */
    public final ExecutorService getExecutor() {
        return this.executor;
    }

    /**
     * Retrieves an Iterator that will produce the stream of objects to be processed
     * in parallel. Each object produced by this Iterator will be passed in its turn
     * to {@link #threadExecute(SailPointContext, Map, Object)} as the third parameter.
     *
     * IMPORTANT NOTES RE: HIBERNATE:
     *
     * It may be unwise to return a "live" Hibernate iterator of the sort provided by
     * context.search here. The next read of the iterator will fail with a "Result Set
     * Closed" exception if anything commits this context while the iterator is still
     * being consumed. It is likely that the first worker threads will execute before
     * the iterator is fully read.
     *
     * If you return a SailPointObject or any other object dependent on a Hibernate
     * context, you will likely receive context-related errors in your worker thread
     * unless you make an effort to re-attach the object to the thread context.
     *
     * TODO One option may be to pass in a private context here, but it couldn't be closed until after iteration is complete.
     *
     * @param context The top-level task Sailpoint context
     * @param args The task arguments
     * @return An iterator containing the objects to be iterated over
     * @throws GeneralException if any failures occur
     */
    protected abstract Iterator<? extends T> getObjectIterator(SailPointContext context, Attributes<String, Object> args) throws GeneralException;

    /**
     * Marks this item as a failure by incrementing the failure counter. Subclasses
     * may override this method to add additional behavior.
     */
    protected void markFailure(T item) {
        failureCounter.incrementAndGet();
    }

    /**
     * Marks this item as a success by incrementing the success counter. Subclasses
     * may override this method to add additional behavior.
     */
    protected void markSuccess(T item) {
        successCounter.incrementAndGet();
    }

    /**
     * Extracts the thread count from the task arguments. Subclasses should override
     * this method to extract their own arguments. You must either call super.parseArgs()
     * in any subclass implementation of this method or set {@link #threadCount} yourself.
     *
     * @param args The task arguments
     * @throws Exception if any failures occur parsing arguments
     */
    protected void parseArgs(Attributes<String, Object> args) throws Exception {
        this.threadCount = Util.atoi(args.getString(CommonConstants.THREADS_ATTR));
        if (this.threadCount < 1) {
            this.threadCount = Util.atoi(args.getString("threadCount"));
        }
        if (this.threadCount < 1) {
            this.threadCount = 1;
        }

        this.batchSize = args.getInt("batchSize", 0);
    }

    /**
     * Prepares the thread pool executor. The default implementation simply constructs
     * a fixed-size executor service, but subclasses may override this behavior with
     * their own implementations.
     *
     * After this method is finished, the {@link #executor} attribute should be set
     * to an {@link ExecutorService} that can accept new inputs.
     *
     * @throws GeneralException if any failures occur
     */
    protected void prepareExecutor() throws GeneralException {
        executor = Executors.newFixedThreadPool(threadCount);
    }

    /**
     * Runs the cleanup handler
     */
    private void runTerminationHandlers() {
        if (terminationHandlers.size() > 0) {
            try {
                SailPointContext context = SailPointFactory.getCurrentContext();
                for (Functions.GenericCallback handler : terminationHandlers) {
                    try {
                        handler.run(context);
                    } catch(Error e) {
                        throw e;
                    } catch(Throwable e) {
                        log.error("Caught an error while running termination handlers", e);
                    }
                }
            } catch (GeneralException e) {
                log.error("Caught an error while running termination handlers", e);
            }
        }
    }

    /**
     * Sets the "before execution hook", an optional pluggable callback that will
     * be invoked prior to the execution of each thread. This BiConsumer's accept()
     * method must be thread-safe as it will be invoked in parallel.
     *
     * @param beforeExecutionHook An optional BiConsumer callback hook
     */
    public final void setBeforeExecutionHook(BiConsumer<Thread, Object> beforeExecutionHook) {
        this.beforeExecutionHook = beforeExecutionHook;
    }

    /**
     * Sets the failure marking callback
     * @param failureMarker The callback invoked on item failure
     */
    public final void setFailureMarker(Consumer<T> failureMarker) {
        this.failureMarker = failureMarker;
    }

    /**
     * Sets the success marking callback
     * @param successMarker The callback invoked on item failure
     */
    public final void setSuccessMarker(Consumer<T> successMarker) {
        this.successMarker = successMarker;
    }

    /**
     * Sets the worker creator function. This function should return a SailPointWorker
     * extension that will take the given List of typed items and process them when
     * its thread is invoked.
     *
     * @param workerCreator The worker creator function
     */
    public final void setWorkerCreator(ThreadWorkerCreator<T> workerCreator) {
        this.workerCreator = workerCreator;
    }

    /**
     * Submits the iterator of items to the thread pool, calling threadExecute for each
     * one, then waits for all of the threads to complete or the task to be terminated.
     *
     * @param context The SailPoint context
     * @param taskResult The taskResult to update (for monitoring)
     * @param items The iterator over items being processed
     * @throws GeneralException if any failures occur
     */
    protected void submitAndWait(SailPointContext context, TaskResult taskResult, Iterator<? extends T> items) throws GeneralException {
        int batchSize = getBatchSize();
        final TaskMonitor monitor = new TaskMonitor(context, taskResult);

        // Default listener allowing individual worker state to be propagated up
        // through the various callbacks, hooks, and listeners on this task.
        ThreadedTaskListener<T> taskContext = new DefaultThreadedTaskListener(taskResult);
        try {
            prepareExecutor();
            AtomicInteger totalCount = new AtomicInteger();
            try {
                Iterator<List<T>> batchingIterator = createBatchIterator(items, batchSize);

                batchingIterator.forEachRemaining(listOfObjects -> {
                    SailPointWorker worker = workerCreator.createWorker(listOfObjects, this, taskContext);
                    worker.setMonitor(monitor);
                    executor.submit(worker.runnable());
                    totalCount.incrementAndGet();
                });
            } finally {
                Util.flushIterator(items);
            }

            try {
                monitor.updateProgress("Submitted " + totalCount.get() + " tasks");
                monitor.commitMasterResult();
            } catch(GeneralException e) {
                /* Ignore this */
            }

            // No further items can be submitted to the executor at this point
            executor.shutdown();

            log.info("Waiting for all threads in task " + taskResult.getName() + " to terminate");

            int totalItems = totalCount.get();
            if (batchSize > 1) {
                totalItems = totalItems * batchSize;
            }

            while(!executor.isTerminated()) {
                executor.awaitTermination(5, TimeUnit.SECONDS);
                int finished = this.successCounter.get() + this.failureCounter.get();
                try {
                    monitor.updateProgress("Completed " + finished + " of " + totalItems + " items");
                    monitor.commitMasterResult();
                } catch(GeneralException e) {
                    /* Ignore this */
                }
            }

            log.info("All threads have terminated in task " + taskResult.getName());

            taskResult.setAttribute("successes", successCounter.get());
            taskResult.setAttribute("failures", failureCounter.get());
            monitor.commitMasterResult();
        } catch(InterruptedException e) {
            terminate();
            throw new GeneralException(e);
        }
    }

    /**
     * Terminates the task by setting the terminated flag, interrupting the executor, waiting five seconds for it to exit, then invoking any shutdown hooks
     *
     * @return Always true
     */
    @Override
    public final boolean terminate() {
        if (!terminated.get()) {
            synchronized(terminated) {
                if (!terminated.get()) {
                    terminated.set(true);
                    if (executor != null && !executor.isTerminated()) {
                        executor.shutdownNow();
                        if (terminationHandlers.size() > 0) {
                            try {
                                executor.awaitTermination(2L, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                log.debug("Interrupted while waiting during termination", e);
                            }
                        }
                    }
                    runTerminationHandlers();
                }
            }
        }
        return true;
    }

    /**
     * This method will be called in parallel for each item produced by {@link #getObjectIterator(SailPointContext, Attributes)}.
     *
     * DO NOT use the parent context in this method. You will encounter Weird Database Issues.
     *
     * @param threadContext A private IIQ context for the current JVM thread
     * @param parameters A set of default parameters suitable for a Rule or Script. In the default implementation, the object will be in this map as 'object'.
     * @param obj The object to process
     * @return An arbitrary value (ignored by default)
     * @throws GeneralException if any failures occur
     */
    public abstract Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, T obj) throws GeneralException;
}
