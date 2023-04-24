package com.identityworksllc.iiq.common.request;

import com.identityworksllc.iiq.common.threads.SailPointWorker;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.request.AbstractRequestExecutor;
import sailpoint.request.RequestPermanentException;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Request executor to allow partitioning of workers across all nodes in the cluster.
 * Your Request should include an attribute called serializedWorkers, which must be
 * a list of Base64-encoded strings, each representing a Java Serialized worker.
 * Each worker should encapsulate the work it's expected to do and must not depend
 * on any inputs from the Request.
 *
 * (In other words, the worker should be able to do the same job whether invoked via
 * a Request or directly in-line in your Java code.)
 *
 * Workers will be deserialized using ObjectInputStream.
 *
 * Workers will run in a single Sailpoint context and will be invoked via execute()
 * and not via implementation().
 *
 * Each worker will be provided a TaskMonitor for this Request, which it can use to
 * execute partition status updates.
 *
 * I recommend creating a number of partitions with relatively small worker counts,
 * because every update of the Request will need to serialize the entire object,
 * including your strings, back to the database.
 *
 * An interrupted SailPointWorkerExecutor (e.g., via server shutdown) will be
 * deleted rather than resumed when the server is restarted. You will have no
 * notification of this.
 */
public final class SailPointWorkerExecutor extends AbstractRequestExecutor {
    /**
     * The request definition name. You will need to have imported this request
     * definition XML.
     */
    public static final String REQUEST_DEFINITION = "IDW Worker Executor";

    private final Log log;

    /**
     * The currently running worker, used to handle termination gracefully
     */
    private SailPointWorker runningWorker;

    /**
     * Will be set to true when terminate() is called
     */
    private final AtomicBoolean terminated;

    public SailPointWorkerExecutor() {
        this.terminated = new AtomicBoolean();
        this.log = LogFactory.getLog(this.getClass());
    }

    /**
     * Main entry point
     * @see AbstractRequestExecutor#execute(SailPointContext, Request, Attributes)
     */
    @Override
    public void execute(SailPointContext sailPointContext, Request request, Attributes<String, Object> attributes) throws RequestPermanentException {
        if (log.isDebugEnabled()) {
            log.debug("Beginning request " + request.getName());
        }
        runWorkers(sailPointContext, request);
        if (log.isDebugEnabled()) {
            log.debug("Finished request " + request.getName());
        }
    }

    /**
     * Executes the given list of workers serially
     *
     * @param sailPointContext The incoming SailPoint context
     * @param request The Request to process, containing worker objects
     * @throws RequestPermanentException if any failures occur
     */
    private void runWorkers(SailPointContext sailPointContext, Request request) throws RequestPermanentException {
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        TaskMonitor monitor = new TaskMonitor(sailPointContext, request);
        try {
            List<String> workers = request.getAttributes().getStringList(SailPointWorker.MULTI_SERIALIZED_WORKERS_ATTR);
            for (String workerString : Util.safeIterable(workers)) {
                if (terminated.get()) {
                    log.warn("Terminated");
                    break;
                }
                TaskResult masterResult = monitor.lockMasterResult();
                try {
                    if (Thread.interrupted() || masterResult.isTerminateRequested() || masterResult.isTerminated()) {
                        log.warn("Master result is terminated");
                        if (!terminated.get()) {
                            if (runningWorker != null) {
                                runningWorker.terminate();
                            }
                        }
                        break;
                    }
                } finally {
                    monitor.commitMasterResult();
                }
                byte[] workerBytes = Base64.getDecoder().decode(Compressor.decompress(workerString));
                SailPointWorker worker = (SailPointWorker) Util.readSerializedObject(workerBytes);
                try {
                    runningWorker = worker;
                    worker.setMonitor(monitor);

                    // TODO figure out if it's possible to invoke implementation() here
                    worker.execute(sailPointContext, log);
                    completed.incrementAndGet();
                } catch(Exception e) {
                    log.error("Caught an error executing a worker", e);
                    failed.incrementAndGet();
                } finally {
                    runningWorker = null;
                }
            }
        } catch(RequestPermanentException e) {
            throw e;
        } catch(GeneralException | IOException | ClassNotFoundException e) {
            log.error("Caught an error preparing worker execution", e);
            try {
                TaskResult taskResult = monitor.lockPartitionResult();
                try {
                    taskResult.addException(e);
                } finally {
                    monitor.commitPartitionResult();
                }
            } catch (Exception e2) {
                log.debug("Caught an exception trying to log an exception", e2);
            }
            // This causes IIQ to not retry the request
            throw new RequestPermanentException(e);
        } finally {
            try {
                TaskResult taskResult = monitor.lockPartitionResult();
                try {
                    taskResult.setInt("completed", completed.get());
                    taskResult.setInt("failed", failed.get());
                } finally {
                    monitor.commitPartitionResult();
                }
            } catch(Exception e2) {
                log.debug("Caught an exception logging an exception", e2);
            }
        }
    }

    @Override
    public boolean terminate() {
        terminated.set(true);
        if (runningWorker != null) {
            log.warn("Terminating running worker thread " + runningWorker);
            runningWorker.terminate();
        }
        return super.terminate();
    }
}
