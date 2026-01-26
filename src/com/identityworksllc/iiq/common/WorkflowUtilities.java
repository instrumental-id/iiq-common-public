package com.identityworksllc.iiq.common;

import sailpoint.api.ObjectUtil;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.*;
import sailpoint.request.WorkflowRequestExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Message;
import sailpoint.transformer.IdentityTransformer;
import sailpoint.workflow.StandardWorkflowHandler;
import sailpoint.workflow.WorkflowContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Workflow utilities
 */
@SuppressWarnings("unused")
public class WorkflowUtilities extends AbstractBaseUtility {

    /**
     * An enumeration of available LCM workflow types and the system config property required to access them
     */
    @SuppressWarnings("javadoc")
    public enum LCMWorkflowType {
        ACCESS_REQUEST("AccessRequest"),
        ACCOUNT_REQUEST("AccountsRequest"),
        ENTITLEMENT_REQUEST("EntitlementsRequest"),
        EXPIRE_PASSWORD("ExpirePassword"),
        FORGOT_PASSWORD("ForgotPassword"),
        IDENTITY_CREATE("IdentityCreateRequest"),
        IDENTITY_EDIT("IdentityEditRequest"),
        PASSWORD("PasswordsRequest"),
        ROLES_REQUEST("RolesRequest"),
        SELF_SERVICE_REGISTRATION("SelfServiceRegistrationRequest"),
        UNLOCK_ACCOUNT("UnlockAccount");

        /**
         * The workflow configuration name
         */
        private final String workflowFlowName;

        /**
         * Constructs an enum constant
         *
         * @param flow The LCM flow name
         */
        LCMWorkflowType(String flow) {
            this.workflowFlowName = flow;
        }

        /**
         * Retrieves the workflow object associated with the given action
         *
         * @param context SailPoint context used to do the retrieval
         * @return The workflow object to use for this LCM action
         * @throws GeneralException if any failures occur
         */
        public Workflow getConfiguredWorkflow(SailPointContext context) throws GeneralException {
            String workflowName = ObjectUtil.getLCMWorkflowName(context, getFlowName());
            Workflow wf = null;
            if (workflowName != null) {
                wf = context.getObjectByName(Workflow.class, workflowName);
            }
            return wf;
        }

        /**
         * Retrieves the workflow name associated with the given action
         *
         * @param context SailPoint context used to do the retrieval
         * @return The workflow name to use for this LCM action
         * @throws GeneralException if any failures occur
         */
        public String getConfiguredWorkflowName(SailPointContext context) throws GeneralException {
            String wfName = ObjectUtil.getLCMWorkflowName(context, getFlowName());
            if (wfName == null || wfName.isEmpty()) {
                return "LCM Provisioning";
            }
            return wfName;
        }

        /**
         * Retrieves the flow name associated with this workflow type
         *
         * @return The flow name
         */
        public String getFlowName() {
            return this.workflowFlowName;
        }
    }

    /**
     * An enumeration of available non-LCM workflow types and the system property required to access them
     * <p>
     * TODO Finish this
     */
    @SuppressWarnings("javadoc")
    public enum WorkflowType {
        IDENTITY_REFRESH("IdentityRefresh"),
        IDENTITY_UPDATE("IdentityUpdate");

        private final String workflowFlowName;

        WorkflowType(String flow) {
            this.workflowFlowName = flow;
        }

        public String getFlowName() {
            return this.workflowFlowName;
        }
    }

    /**
     * Constructor for workflow utilities
     *
     * @param c The current SailPointContext
     */
    public WorkflowUtilities(SailPointContext c) {
        super(c);
    }

    /**
     * Creates a new form model map with the required parameters set. If you
     * need to add complex values to the Map, using {@link MapUtil#put(Map, String, Object)}
     * is a great option.
     *
     * @return A new form model with the required parameters
     */
    public static Map<String, Object> createFormModel() {
        Map<String, Object> model = new HashMap<>();
        model.put(IdentityTransformer.ATTR_TRANSFORMER_CLASS, IdentityTransformer.class.getName());
        model.put(IdentityTransformer.ATTR_TRANSFORMER_OPTIONS, "");
        return model;
    }

    /**
     * Launches the given workflow asynchronously, using a Request to launch the
     * Workflow and associated TaskResult at a later time. If the launch time is
     * not in the future, it will be set to the current System timestamp.
     *
     * @param wf         The workflow to launch
     * @param caseName   The case name ot use
     * @param parameters The parameters to the workflow
     * @param launchTime The time at which the workflow will be launched (in milliseconds since epoch)
     * @throws GeneralException if any IIQ error occurs
     */
    public void launchAsync(Workflow wf, String caseName, Map<String, Object> parameters, long launchTime) throws GeneralException {
        long launchTimeFinal = Math.max(launchTime, System.currentTimeMillis());

        Attributes<String, Object> reqArgs = new Attributes<>();
        reqArgs.put(StandardWorkflowHandler.ARG_REQUEST_DEFINITION, WorkflowRequestExecutor.DEFINITION_NAME);
        reqArgs.put(StandardWorkflowHandler.ARG_WORKFLOW, wf.getName());
        reqArgs.put(StandardWorkflowHandler.ARG_REQUEST_NAME, caseName);
        reqArgs.putAll(parameters);

        Request req = new Request();
        RequestDefinition reqdef = context.getObject(RequestDefinition.class, WorkflowRequestExecutor.DEFINITION_NAME);
        req.setDefinition(reqdef);
        req.setEventDate(new Date(launchTimeFinal));
        req.setOwner(context.getObjectByName(Identity.class, "spadmin"));
        req.setName(caseName);
        req.setAttributes(reqdef, reqArgs);

        RequestManager.addRequest(context, req);
    }

    /**
     * Launches the given workflow asynchronously, using a Request to launch the
     * Workflow and associated TaskResult at a later time. If the launch time is
     * not in the future, it will be set to the current System timestamp.
     *
     * @param wf         The workflow to launch
     * @param caseName   The case name ot use
     * @param parameters The parameters to the workflow
     * @param launchTime The time at which the workflow will be launched
     * @throws GeneralException if any IIQ error occurs
     */
    public void launchAsync(Workflow wf, String caseName, Map<String, Object> parameters, LocalDate launchTime) throws GeneralException {
        long launchTimeMillis = launchTime.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        launchAsync(wf, caseName, parameters, launchTimeMillis);
    }

    /**
     * Launches the given workflow asynchronously, using a Request to launch the
     * Workflow and associated TaskResult at a later time. If the launch time is
     * not in the future, it will be set to the current System timestamp.
     *
     * @param wf         The workflow to launch
     * @param caseName   The case name ot use
     * @param parameters The parameters to the workflow
     * @param launchTime The time at which the workflow will be launched
     * @throws GeneralException if any IIQ error occurs
     */
    public void launchAsync(Workflow wf, String caseName, Map<String, Object> parameters, LocalDateTime launchTime) throws GeneralException {
        long launchTimeMillis = launchTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        launchAsync(wf, caseName, parameters, launchTimeMillis);
    }

    /**
     * Launches the given workflow with the given delay
     *
     * @param wf         The workflow to launch
     * @param caseName   The case name ot use
     * @param parameters The parameters to the workflow
     * @param howMuch    How much to delay the launch of the workflow
     * @param timeUnit   The time unit represented by the howMuch parameter
     * @throws GeneralException if any IIQ error occurs
     */
    public void launchDelayed(Workflow wf, String caseName, Map<String, Object> parameters, long howMuch, TimeUnit timeUnit) throws GeneralException {
        if (howMuch < 0) {
            throw new IllegalArgumentException("The delay must be a positive number");
        }
        long launchTime = System.currentTimeMillis();
        long delayMs = TimeUnit.MILLISECONDS.convert(howMuch, timeUnit);
        long delayDays = TimeUnit.DAYS.convert(howMuch, timeUnit);
        if (delayDays > 30) {
            log.warn("Submitting workflow " + caseName + " with a delay greater than 30 days; is this what you want?");
        }
        launchTime += delayMs;
        launchAsync(wf, caseName, parameters, launchTime);
    }

    /**
     * Launches the given workflow with the given delay
     *
     * @param wf         The workflow to launch
     * @param caseName   The case name ot use
     * @param parameters The parameters to the workflow
     * @param howMuch    How much to delay the launch of the workflow
     * @param timeUnit   The time unit represented by the howMuch parameter
     * @throws GeneralException if any IIQ error occurs
     */
    public void launchDelayed(Workflow wf, String caseName, Map<String, Object> parameters, long howMuch, ChronoUnit timeUnit) throws GeneralException {
        if (howMuch < 0) {
            throw new IllegalArgumentException("The delay must be a positive number");
        }
        long launchTime = System.currentTimeMillis();
        Instant when = Instant.now().plus(howMuch, timeUnit);
        long delayMs = when.toEpochMilli() - launchTime;
        long delayDays = TimeUnit.DAYS.convert(delayMs, TimeUnit.MILLISECONDS);
        if (delayDays > 30) {
            log.warn("Submitting workflow " + caseName + " with a delay greater than 30 days; is this what you want?");
        }
        launchTime += delayMs;
        launchAsync(wf, caseName, parameters, launchTime);
    }

    /**
     * Internal method to launch a workflow using the RequestManager. Used for testing
     * by overriding this method to do something different.
     *
     * @param context The SailPointContext to use for launching the workflow
     * @param request The Request to launch
     * @throws GeneralException if any IIQ error occurs
     */
    protected void launchInternal(SailPointContext context, Request request) throws GeneralException {
        RequestManager.addRequest(context, request);
    }


    /**
     * Launches the given workflow quietly (i.e. absorbs errors if there are any)
     *
     * @param wf         The workflow to launch
     * @param caseName   The case name ot use
     * @param parameters The parameters to the workflow
     */
    public void launchQuietly(Workflow wf, String caseName, Map<String, Object> parameters) {
        try {
            Workflower workflower = new Workflower(context);
            workflower.launchSafely(wf, caseName, parameters);
        } catch (GeneralException e) {
            /* TODO log and ignore */
        }
    }

    /**
     * Sets the target workflow reference of a step to another workflow at runtime. This can be used for polymorphic behavior.
     *
     * @param wfcontext      The workflow to modify
     * @param stepName       The step name to modify
     * @param targetWorkflow The new target workflow for that step
     * @throws GeneralException if any IIQ error occurs
     */
    public void setStepTargetWorkflow(WorkflowContext wfcontext, String stepName, String targetWorkflow) throws GeneralException {
        Workflow.Step step = wfcontext.getWorkflow().getStep(stepName);
        step.setSubProcess(context.getObjectByName(Workflow.class, targetWorkflow));

        List<Workflow.Arg> arguments = new ArrayList<>();
        Attributes<String, Object> variables = wfcontext.getWorkflow().getVariables();

        for (String key : variables.getKeys()) {
            if (!key.equals("targetWorkflow")) {
                Workflow.Arg arg = new Workflow.Arg();
                arg.setName(key);
                arg.setValue("ref:" + key);
                arguments.add(arg);
            }
        }

        step.setArgs(arguments);
    }

    /**
     * Appends an error message to the given workflow context / task result with a timestamp
     *
     * @param wfcontext The Workflow Context (wfcontext from any workflow step)
     * @param message   The message to append
     */
    public void wfError(WorkflowContext wfcontext, String message) {
        WorkflowCase wfcase = wfcontext.getWorkflowCase();
        if (wfcase != null) {
            wfcase.addMessage(Message.error(Utilities.timestamp() + ": " + message));
        }
    }

    /**
     * Appends a message to the given workflow context / task result with a timestamp
     *
     * @param wfcontext The Workflow Context (wfcontext from any workflow step)
     * @param message   The message to append
     */
    public void wfMessage(WorkflowContext wfcontext, String message) {
        WorkflowCase wfcase = wfcontext.getWorkflowCase();
        if (wfcase != null) {
            wfcase.addMessage(Utilities.timestamp() + ": " + message);
        }
    }

    /**
     * Appends a warning message to the given workflow context / task result with a timestamp
     *
     * @param wfcontext The Workflow Context (wfcontext from any workflow step)
     * @param message   The message to append
     */
    public void wfWarn(WorkflowContext wfcontext, String message) {
        WorkflowCase wfcase = wfcontext.getWorkflowCase();
        if (wfcase != null) {
            wfcase.addMessage(Message.warn(Utilities.timestamp() + ": " + message));
        }
    }
}
