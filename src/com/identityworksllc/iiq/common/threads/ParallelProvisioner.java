package com.identityworksllc.iiq.common.threads;

import com.identityworksllc.iiq.common.Maybe;
import com.identityworksllc.iiq.common.Metered;
import com.identityworksllc.iiq.common.annotation.Experimental;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.provisioning.PlanEvaluator;

import java.util.*;
import java.util.concurrent.*;

/**
 * A class implementing parallel provisioner execution. Each provisioning operation
 * will run in its own thread, resulting in either a ProvisioningProject or some
 * exception. The {@link Maybe} class is used to encapsulate the state, containing
 * either a valid output or an exception, for each item.
 *
 * The {@link Provisioner} will be invoked with `disableRetryRequest` set to `true`,
 * so the caller is responsible for detecting and retrying any provisioning failures.
 *
 * @since 2024-09-26
 */
@Experimental
public class ParallelProvisioner {
    public static class ParallelProvisioningTask {
        private final ProvisioningPlan plan;
        private final Future<Maybe<ProvisioningProject>> projectFuture;

        /**
         * Constructs a n ew ParallelProvisioningTask for the given pair of plan
         * and background executor.
         *
         * @param plan The plan
         * @param projectFuture The project future, from {@link ParallelProvisioningWorker}
         */
        public ParallelProvisioningTask(ProvisioningPlan plan, Future<Maybe<ProvisioningProject>> projectFuture) {
            this.plan = plan;
            this.projectFuture = projectFuture;
        }

        /**
         * Attempts to cancel the running background task
         * @return Attempts to cancel the running background task
         */
        public boolean cancel() {
            return this.projectFuture.cancel(true);
        }

        /**
         * Gets the original ProvisioningPlan associated with this outcome
         * @return The provisioning plan
         */
        public ProvisioningPlan getPlan() {
            return plan;
        }

        /**
         * If the task has completed, returns an {@link Optional} containing the {@link ProvisioningProject},
         * which itself will contain the outcome of provisioning. If the task has not completed, or was canceled,
         * returns an empty {@link Optional}. If the task completed, but failed, re-throws the exception.
         *
         * @return An {@link Optional} {@link ProvisioningProject}, as described above
         * @throws Exception if the execution finished but failed, or if there is an error retrieving the outcome
         */
        public Optional<ProvisioningProject> getProject() throws Exception {
            if (!isDone()) {
                return Optional.empty();
            }

            if (this.projectFuture.isCancelled()) {
                return Optional.empty();
            }

            Maybe<ProvisioningProject> output = this.projectFuture.get();
            if (output.hasValue()) {
                return Optional.of(output.get());
            } else {
                throw (Exception) output.getError();
            }
        }

        /**
         * Returns true if {@link Future#isDone()} returns true.
         *
         * @return True if the background task is either done or canceled
         */
        public boolean isDone() {
            return this.projectFuture.isDone();
        }
    }

    /**
     * The worker thread for the parallel provisioning operation. Submits the given plan
     * to the {@link Provisioner} after reloading its contained {@link Identity}. If the
     * plan succeeds, returns a {@link Maybe} containing the project.
     */
    private static class ParallelProvisioningWorker extends SailPointWorker {
        /**
         * The arguments for this provisioning worker
         */
        private final Attributes<String, Object> args;

        /**
         * The plan for this provisioning worker
         */
        private final ProvisioningPlan plan;

        /**
         * Constructs a new worker object for the given plan + arguments
         * @param plan The provisioning plan for this worker
         * @param args The arguments for this worker
         */
        public ParallelProvisioningWorker(ProvisioningPlan plan, Map<String, Object> args) {
            this.plan = plan;
            this.args = new Attributes<>(args);
        }

        /**
         * Executes the provisioning plan in the thread context
         *
         * @param threadContext The private context to use for this thread worker
         * @param logger The log attached to this Worker
         * @return A {@link Maybe} containing the provisioning project or an error
         */
        @Override
        public Object execute(SailPointContext threadContext, Log logger) {
            try {
                plan.setIdentity(threadContext.getObject(Identity.class, plan.getIdentity().getId()));

                if (logger.isDebugEnabled()) {
                    logger.debug("Executing provisioning plan: " + plan.toXml());
                }

                args.put(PlanEvaluator.ARG_NO_RETRY_REQUEST, true);

                Provisioner provisioner = new Provisioner(threadContext, args);
                provisioner.setSource(Source.Batch);
                provisioner.execute(plan);

                return Maybe.of(provisioner.getProject());
            } catch (Exception e) {
                String identityName = plan.getIdentity() != null ? plan.getIdentity().getName() : "(null)";
                logger.warn("Failed to execute parallel provision plan for Identity " + identityName, e);
                return Maybe.of(e);
            }
        }
    }

    /**
     * Internal logger
     */
    private static final Log log = LogFactory.getLog(ParallelProvisioner.class);

    /**
     * The arguments for the provisioning action
     */
    private final Map<String, Object> arguments;

    /**
     * The number of threads for the provisioning thread pool
     */
    private final int threads;

    /**
     * Constructs a new parallel provisioner with the given number of threads and
     * the default set of arguments.
     *
     * @param threads The number of threads
     */
    public ParallelProvisioner(int threads) {
        this.arguments = new HashMap<>();
        this.threads = Math.max(1, threads);
    }

    /**
     * Constructs a new parallel provisioner with the given number of threads and
     * the given set of arguments.
     *
     * @param threads The number of threads
     * @param arguments The arguments passed to the Provisioner in each thread
     */
    public ParallelProvisioner(Map<String, Object> arguments, int threads) {
        this.arguments = arguments;
        this.threads = threads;
    }

    /**
     * Provisions the given set of plans in the thread pool.
     *
     * @param plans The plans to provision
     * @return A set of {@link ParallelProvisioningTask} objects, each representing the (future) outcome of one plan execution
     */
    public List<ParallelProvisioningTask> provisionPlans(List<ProvisioningPlan> plans) {
        if (log.isDebugEnabled()) {
            log.debug("Submitting " + plans.size() + " plans to a thread pool of size " + threads);
        }

        List<ParallelProvisioningTask> futures = new ArrayList<>();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 180, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);

        for(ProvisioningPlan plan : plans) {
            Future<Maybe<ProvisioningProject>> future = executor.submit(
                    () -> Metered.meter("ParallelProvisioner.execute", () -> {
                        Callable<Object> obj = new ParallelProvisioningWorker(plan, arguments).toCallable();
                        try {
                            Object output = obj.call();
                            return (Maybe<ProvisioningProject>) output;
                        } catch (Exception e) {
                            return Maybe.of(e);
                        }
                    })
            );
            futures.add(new ParallelProvisioningTask(plan, future));
        }

        executor.shutdown();

        return futures;
    }
}
