package com.identityworksllc.iiq.common.threads;

import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

/**
 * A container to be used to start a list of potentially recursive workers. The output will be
 * a list of objects from each worker. Workers will be invoked in a {@link RecursiveWorkerChildTask}
 * with a null parent.
 */
/*package*/ class RecursiveWorkerContainer extends RecursiveTask<List<Object>> {
    /**
     * THe workers
     */
    private final List<SailPointWorker> workerList;

    /**
     * Basic construction
     * @param workers The list of workers to execute in order
     */
    public RecursiveWorkerContainer(List<SailPointWorker> workers) {
        this.workerList = workers;
    }

    /**
     * Computes the list of worker responses by running them in child tasks, potentially
     * in a separate thread.
     *
     * @return The list of outputs (some potentially null) from each worker
     */
    private List<Object> completeWorkerList() {
        List<Object> outcomes = new ArrayList<>();
        List<RecursiveWorkerChildTask> tasks = new ArrayList<>();
        for (SailPointWorker worker : Util.safeIterable(workerList)) {
            RecursiveWorkerChildTask singleWorkerContainer = new RecursiveWorkerChildTask(worker, null);
            singleWorkerContainer.fork();
            tasks.add(singleWorkerContainer);
        }

        for(RecursiveWorkerChildTask task : tasks) {
            outcomes.add(task.join());
        }

        return outcomes;
    }

    /**
     * Entry point by the ForkJoinPool to calculate the output of this step
     * @return The output of each of the workers
     */
    @Override
    protected List<Object> compute() {
        try {
            return completeWorkerList();
        } catch(Exception e) {
            completeExceptionally(e);
        }

        return null;
    }

}
