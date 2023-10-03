package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.HybridObjectMatcher;
import com.identityworksllc.iiq.common.Utilities;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IncrementalProjectionIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A task executor for finding and removing unwanted negative role assignments.
 *
 * An audit event of type 'negativeRoleAssignmentsRemoved' will be logged if action
 * is taken.
 */
public class RemoveNegativeRoleAssignments extends AbstractTaskExecutor {

    /**
     * Logger
     */
    private final Log log;

    /**
     * Terminated flag, set by {@link #terminate()}
     */
    private final AtomicBoolean terminated;

    /**
     * Construct a new task executor
     */
    public RemoveNegativeRoleAssignments() {
        this.terminated = new AtomicBoolean();
        this.log = LogFactory.getLog(RemoveNegativeRoleAssignments.class);
    }

    @Override
    public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attributes) throws Exception {
        // Identities matching this filter will be included in the cleanup
        String additionalFilterString = attributes.getString("identityFilter");
        Filter additionalFilter = Util.isNotNullOrEmpty(additionalFilterString) ? Filter.compile(additionalFilterString) : null;

        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.like("preferences", "roleAssignments", Filter.MatchMode.ANYWHERE));

        List<String> fields = new ArrayList<>();
        fields.add("id");
        fields.add("name");
        fields.add("displayName");
        fields.add("preferences");

        IncrementalProjectionIterator results = new IncrementalProjectionIterator(context, Identity.class, qo, fields);
        while(results.hasNext()) {
            if (terminated.get()) {

                break;
            }
            Object[] row = results.next();

            String id = Util.otoa(row[0]);
            String prefs = Util.otoa(row[3]);

            @SuppressWarnings("unchecked")
            Map<String, Object> prefsMap = (Map<String, Object>) AbstractXmlObject.parseXml(context, prefs);
            if (prefsMap != null) {
                @SuppressWarnings("unchecked")
                List<RoleAssignment> roleAssignments = (List<RoleAssignment>) prefsMap.get("roleAssignments");
                for(RoleAssignment ra : Util.safeIterable(roleAssignments)) {
                    if (ra.isNegative()) {
                        Utilities.withPrivateContext((privateContext) -> {
                            processIdentity(privateContext, id, additionalFilter);
                        });
                    }
                }
            }
        }
    }

    /**
     * Processes a single Identity by removing any negative RoleAssignments
     *
     * TODO wrap this in a worker thread and a private IIQ context
     *
     * @param context The IIQ context to use
     * @param identityId The identity ID
     * @param additionalFilter Optionally, an additional filter to further constrain the users operated upon
     * @throws GeneralException if anything goes wrong
     */
    private void processIdentity(SailPointContext context, String identityId, Filter additionalFilter) throws GeneralException {
        // Lock the Identity, just in case we're getting refreshed or provisioned at the same time
        boolean assignmentsRemoved = false;

        Identity identity = ObjectUtil.lockIdentity(context, identityId);
        try {
            boolean shouldSkip = false;

            if (additionalFilter != null) {
                HybridObjectMatcher matcher = new HybridObjectMatcher(context, additionalFilter);
                shouldSkip = !matcher.matches(identity);
            }

            if (shouldSkip) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping Identity " + identity.getDisplayableName() + " because it matches the skip filter");
                }
            } else {
                List<RoleAssignment> roleAssignments = identity.getRoleAssignments();
                for (RoleAssignment ra : Util.safeIterable(roleAssignments)) {
                    if (ra.isNegative()) {
                        assignmentsRemoved = true;
                        identity.removeRoleAssignment(ra);
                    }
                }
            }
        } finally {
            // This also saves and commits the Identity object, even if the lock has
            // somehow gone away.
            ObjectUtil.unlockIfNecessary(context, identity);
        }

        // Only audit if an assignment was actually removed
        if (assignmentsRemoved) {
            AuditEvent ae = new AuditEvent();
            ae.setAction("negativeRoleAssignmentsRemoved");
            ae.setTarget(identity.getName());
            ae.setServerHost(Util.getHostName());
            ae.setSource(this.getClass().getSimpleName());

            // Force it, even if Auditor.log would skip it
            context.saveObject(ae);
            context.commitTransaction();
        }

    }

    @Override
    public boolean terminate() {
        this.terminated.set(true);
        return true;
    }
}
