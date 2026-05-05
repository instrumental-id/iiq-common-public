package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.logging.SLogger;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.*;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.Util;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task to clean up stuck WorkflowCases that don't have an associated TaskResult. These will
 * cause the workflow processor in Perform Maintenance to log an error and ignore it, causing
 * an enormous amount of log noise.
 *
 * Only WorkflowCases more than one hour old will be considered, avoiding conflicts with legitimately
 * long-running workflows that have not yet persisted fully.
 *
 * This is an unusual situation, and is most likely to arise if IIQ encounters a persistence error
 * (such as database pool exhaustion) while attempting to save a new WorkflowCase and its TaskResult.
 */
public class CleanupStuckWorkflowCases extends AbstractTaskExecutor {
    private static final SLogger log = new SLogger(CleanupStuckWorkflowCases.class);
    private final AtomicBoolean terminated;

    /**
     * Constructor for the CleanupStuckWorkflowCases task executor. Initializes the terminated flag to false.
     */
    public CleanupStuckWorkflowCases() {
        this.terminated = new AtomicBoolean(false);
    }

    /**
     * Executes the task to clean up stuck WorkflowCases. It iterates through all WorkflowCases, identifies those
     * that are more than one hour old and do not have an associated TaskResult, and deletes them. The method checks
     * the terminated flag to allow for graceful termination of the task.
     *
     * TODO: Add an option to exclude particular workflow names or workflow case names
     *
     * @param context the SailPointContext for accessing IIQ objects and services
     * @param taskSchedule the TaskSchedule that triggered this execution
     * @param taskResult the TaskResult object to record the results of this execution
     * @param attributes the attributes passed to this task execution
     * @throws Exception if an error occurs during execution
     */
    @Override
    public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attributes) throws Exception {
        QueryOptions qo = new QueryOptions();

        Instant cutoff = ZonedDateTime.now().minusHours(1).toInstant();

        IncrementalObjectIterator<WorkflowCase> iterator = new IncrementalObjectIterator<>(context, WorkflowCase.class, qo);

        List<String> deleteIds = new ArrayList<>();

        while (iterator.hasNext()){
            if (terminated.get()) {
                break;
            }
            boolean proceed = false;
            WorkflowCase workflowCase = iterator.next();
            if (workflowCase.getCreated() != null) {
                var created = workflowCase.getCreated().toInstant();
                if (created.isBefore(cutoff)) {
                    proceed = true;
                }
            }

            if (proceed) {
                var taskResultId = Util.otoa(workflowCase.getAttribute("taskResultId"));
                if (Util.isNullOrEmpty(taskResultId)) {
                    // Flag for delete
                    log.info("Workflow case {0} is stuck with no task result. Deleting...", workflowCase.getId());
                    deleteIds.add(workflowCase.getId());
                }
            }
        }

        context.decache();

        Terminator terminator = new Terminator(context);

        for(String id : deleteIds) {
            if (terminated.get()) {
                break;
            }
            WorkflowCase workflowCase = context.getObject(WorkflowCase.class, id);
            if (workflowCase != null) {
                log.info("Deleting workflow case {0}...", workflowCase.getId());
                terminator.deleteObject(workflowCase);
            } else {
                log.debug("Workflow case {0} evaporated prior to deletion", id);
            }
        }

        if (terminated.get()) {
            log.warn("Task was terminated by user request");
        }
    }

    /**
     * Terminates the execution of this task by setting the terminated flag to true. The execute method checks
     * this flag and will stop processing if it is set, allowing for graceful termination of the task.
     *
     * @return true if the termination signal was successfully received, false otherwise
     */
    @Override
    public boolean terminate() {
        this.terminated.set(true);
        return true;
    }
}
