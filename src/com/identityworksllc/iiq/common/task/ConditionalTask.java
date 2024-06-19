package com.identityworksllc.iiq.common.task;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.*;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A task executor that will invoke a Rule that returns a boolean indicating that
 * another task should be run. This can be used to skip an expensive task, or a task
 * likely to cause problems, such as during externally defined maintenance windows,
 * final exam periods, or other critical times.
 *
 * BETA!
 */
public class ConditionalTask extends AbstractTaskExecutor {
    private TaskManager taskManager;
    private final AtomicBoolean terminated;

    /**
     * Constructs a new conditional task
     */
    public ConditionalTask() {
        this.terminated = new AtomicBoolean();
    }

    @Override
    public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attributes) throws Exception {
        TaskMonitor monitor = new TaskMonitor(context, taskResult);
        super.setMonitor(monitor);

        this.taskManager = new TaskManager(context);

        boolean wait = attributes.getBoolean("awaitCompletion", false);

        String ruleName = attributes.getString("conditionalRuleName");
        Rule conditionalRule = context.getObject(Rule.class, ruleName);
        if (conditionalRule == null) {
            throw new GeneralException("Unable to find conditional rule: " + ruleName);
        }

        String taskName = attributes.getString("taskName");
        TaskDefinition taskDef = context.getObject(TaskDefinition.class, taskName);
        if (taskDef == null) {
            throw new GeneralException("Unable to find child task: " + taskName);
        }

        Map<String, Object> inputs = new HashMap<>();

        Object ruleOutput = context.runRule(conditionalRule, inputs);
        if (ruleOutput instanceof Boolean) {
            boolean shouldRun = (Boolean) ruleOutput;
            if (shouldRun) {
                if (wait) {
                    TaskResult result = taskManager.runSync(taskDef, new HashMap<>());

                    TaskResult parent = monitor.lockMasterResult();
                    try {
                        parent.assimilateResult(result);
                    } finally {
                        monitor.commitMasterResult();
                    }
                } else {
                    TaskSchedule schedule = taskManager.run(taskDef, new HashMap<>());
                    if (schedule != null) {
                        TaskResult parent = monitor.lockMasterResult();
                        try {
                            parent.addMessage(Message.info("Started task " + schedule.getName()));
                        } finally {
                            monitor.commitMasterResult();
                        }
                    }
                }
            } else {
                TaskResult parent = monitor.lockMasterResult();
                try {
                    parent.addMessage(Message.info("Conditional rule returned false, indicating that task " + taskDef.getName() + " should be skipped"));
                } finally {
                    monitor.commitMasterResult();
                }
            }
        } else {
            throw new GeneralException("Illegal output of conditional rule: " + ruleOutput);
        }
    }

    /**
     * Terminates the task
     * @return True, indicating that we have reacted to the termination
     */
    @Override
    public boolean terminate() {
        this.terminated.set(true);
        if (this.taskManager != null) {
            this.taskManager.terminate();
        }
        return true;
    }
}
