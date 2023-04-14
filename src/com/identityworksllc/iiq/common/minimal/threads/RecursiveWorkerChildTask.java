package com.identityworksllc.iiq.common.minimal.threads;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.Util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * A task that runs a recursive set of SailPointWorkers and their children
 * in threads, allowing each parent to access its childrens' outputs.
 *
 * All children will be run first in the same thread pool using {@link ForkJoinTask#invokeAll(Collection)}.
 * If any children fail, an Exception will be thrown and this task will also fail. If all children succeed,
 * this task will be invoked in this thread. When this task completes, if it has a parent, its output
 * will be set into the parent's dependency map.
 */
/*package*/ class RecursiveWorkerChildTask extends RecursiveTask<Object> {
    private final Log log;
    /**
     * Parent task (can be null)
     */
    private final SailPointWorker parent;

    /**
     * The worker to run in this thread
     */
    private final SailPointWorker worker;

    public RecursiveWorkerChildTask(SailPointWorker worker, SailPointWorker parent) {
        this.worker = worker;
        this.parent = parent;
        this.log = LogFactory.getLog(this.getClass());
    }

    /**
     * Computes the result of this task, first executing the child tasks as described
     * in the class Javadocs.
     *
     * @return The output of this worker's execute method
     */
    @Override
    protected Object compute() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Processing worker " + worker.getWorkerName());
            }

            List<RecursiveWorkerChildTask> childTasks = null;
            if (!Util.isEmpty(worker.children)) {
                childTasks =
                        worker.children.stream().map(w -> new RecursiveWorkerChildTask(w, worker)).collect(Collectors.toList());

                ForkJoinTask.invokeAll(childTasks);
            }

            for(RecursiveWorkerChildTask child : Util.safeIterable(childTasks)) {
                child.get();
            }

            // Make sure the worker doesn't re-execute the children
            worker.executeChildren = false;

            if (log.isDebugEnabled()) {
                log.debug("Children completed successfully; executing worker " + worker.getWorkerName());
            }

            Callable<Object> callable = worker.toCallable();
            Object output = callable.call();

            if (log.isDebugEnabled()) {
                log.debug("Done executing worker " + worker.getWorkerName());
            }

            if (parent != null) {
                parent.dependencyOutput.put(worker.getWorkerName(), output);
            }

            return output;
        } catch(Exception e) {
            log.error("Unable to complete worker " + worker.getWorkerName(), e);
            completeExceptionally(e);
        }
        return null;
    }
}
