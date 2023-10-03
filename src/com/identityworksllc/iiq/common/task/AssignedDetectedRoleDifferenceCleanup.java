package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.iterators.BatchingIterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Identitizer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.identityworksllc.iiq.common.CommonConstants.ARG_STRING_FALSE;
import static com.identityworksllc.iiq.common.CommonConstants.ARG_STRING_TRUE;

/**
 * Assigned role differences
 */
public class AssignedDetectedRoleDifferenceCleanup extends AbstractTaskExecutor {

    /**
     * The HQL to run to detect differences
     */
    private final static String HQL = "select i.id, iar.name, req.name \n" +
            "from Identity i\n" +
            "inner join i.assignedRoles iar\n" +
            "inner join iar.requirements req\n" +
            "where\n" +
            "not exists (\n" +
            "      select 1 from i.bundles b where b.name = req.name\n" +
            ")\n";
    private final Log log;

    /**
     * Flag indicating that the task is terminated
     */
    private final AtomicBoolean terminated;

    /**
     * Constructor
     */
    public AssignedDetectedRoleDifferenceCleanup() {
        this.terminated = new AtomicBoolean(false);
        this.log = LogFactory.getLog(AssignedDetectedRoleDifferenceCleanup.class);
    }

    /**
     * Executor for this task: scans for users missing required IT roles and either
     * refreshes them or reports on them.
     *
     * @param context The context from IIQ
     * @param taskSchedule The task schedule
     * @param taskResult The task result
     * @param attributes The task attributes
     * @throws Exception if anything fails
     */
    @Override
    public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attributes) throws Exception {
        boolean doRefresh = attributes.getBoolean("doRefresh", false);
        boolean doReport = attributes.getBoolean("doReport", true);

        String populationName = attributes.getString("savePopulationName");

        TaskMonitor monitor = new TaskMonitor(context, taskResult);

        AtomicInteger missingRoles = new AtomicInteger();

        log.info("Running HQL: " + HQL);
        QueryOptions qo = new QueryOptions();
        Iterator<Object[]> results = context.search(Identity.class, qo, HQL);

        Set<String> identitiesToRefresh = new HashSet<>();

        List<Map<String, Object>> resultList = new ArrayList<>();

        while(results.hasNext()) {
            Object[] row = results.next();

            String id = Util.otoa(row[0]);
            String assignedRoleName = Util.otoa(row[1]);
            String missingRequirement = Util.otoa(row[2]);

            Map<String, Object> logData = new HashMap<>();
            logData.put("identity", id);
            logData.put("businessRole", assignedRoleName);
            logData.put("requiredRoleMissing", missingRequirement);

            if (log.isDebugEnabled()) {
                log.debug("Missing IT role for assigned Business role: " + logData);
            }

            resultList.add(logData);

            identitiesToRefresh.add(id);

            monitor.updateProgress("Processing Identity " + id);

            missingRoles.incrementAndGet();

            if (terminated.get()) {
                Util.flushIterator(results);
                break;
            }
        }

        // Constructs a big gross population containing all of the IDs of all users with
        // missing entitlements.
        if (Util.isNotNullOrEmpty(populationName)) {
            log.info("Creating ID List population: " + populationName);

            GroupDefinition groupDefinition = context.getObjectByName(GroupDefinition.class, populationName);
            if (groupDefinition == null) {
                groupDefinition = new GroupDefinition();
                groupDefinition.setName(populationName);
                groupDefinition.setPrivate(false);
            }

            try (BatchingIterator<String> iterator = new BatchingIterator<>(identitiesToRefresh.iterator(), 250)) {
                List<Filter> idFilters = new ArrayList<>();

                while (iterator.hasNext()) {
                    List<String> batch = iterator.next();
                    idFilters.add(Filter.in("id", batch));
                }

                groupDefinition.setFilter(Filter.or(idFilters));
            }

            if (!terminated.get()) {
                context.saveObject(groupDefinition);
            }
        }

        // Do refreshes if that's enabled
        if (doRefresh && !terminated.get()) {
            Attributes<String, Object> args = new Attributes<>();
            args.put(Identitizer.ARG_PROVISION, ARG_STRING_TRUE);
            args.put(Identitizer.ARG_CHECK_HISTORY, ARG_STRING_TRUE);
            args.put(Identitizer.ARG_CORRELATE_ENTITLEMENTS, ARG_STRING_TRUE);
            args.put(Identitizer.ARG_REFRESH_IDENTITY_ENTITLEMENTS, ARG_STRING_TRUE);
            args.put(Identitizer.ARG_PROCESS_TRIGGERS, ARG_STRING_FALSE);
            args.put(Identitizer.ARG_REFRESH_PROVISIONING_REQUESTS, ARG_STRING_TRUE);
            args.put(Identitizer.ARG_REFRESH_ROLE_METADATA, ARG_STRING_TRUE);
            args.put(Identitizer.ARG_REFRESH_SOURCE, Source.Task);
            args.put(Identitizer.ARG_REFRESH_SOURCE_WHO, context.getUserName());

            Identitizer identitizer = new Identitizer(context, args);

            for(String id : identitiesToRefresh) {
                if (terminated.get()) {
                    log.warn("Task was terminated");
                    break;
                }

                Identity identity = context.getObject(Identity.class, id);

                if (log.isDebugEnabled()) {
                    log.debug("Refreshing Identity " + identity.getDisplayableName());
                }

                monitor.updateProgress("Refreshing Identity " + identity.getDisplayableName());

                try {
                    identitizer.refresh(identity);
                } catch(GeneralException e) {
                    TaskResult primaryResult = monitor.lockMasterResult();
                    try {
                        primaryResult.addException(e);
                    } finally {
                        monitor.commitMasterResult();
                    }

                    log.error("Caught an error refreshing Identity", e);
                }
            }

            identitizer.cleanup();
        }

        TaskResult primaryResult = monitor.lockMasterResult();
        try {
            if (terminated.get()) {
                primaryResult.addMessage(Message.warn("Task was terminated by user request"));
            }
            if (doReport) {
                primaryResult.setAttribute("report", resultList);
            }
            primaryResult.setInt("affectedIdentities", identitiesToRefresh.size());
            primaryResult.setInt("missingRoles", missingRoles.get());
        } finally {
            monitor.commitMasterResult();
        }
    }

    /**
     * Invoked by IIQ on termination. Sets the terminated flag to true so that
     * the task knows to stop.
     *
     * @return always boolean true
     */
    @Override
    public boolean terminate() {
        terminated.getAndSet(true);
        return true;
    }
}
