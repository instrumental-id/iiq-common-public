package com.identityworksllc.iiq.common;

import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the {@link ProvisioningUtilities} class
 */
public class ProvisioningArguments {

    /**
     * Case name template
     */
    private String caseNameTemplate = "{Workflow} - {IdentityName} - {Timestamp}";

    /**
     * Any extra parameters to pass to the provisioning workflow or Provisioner
     */
    private Map<String, Object> defaultExtraParameters;
    /**
     * If true, the plan will be compiled and an error will be thrown before Provisioner / LCM submission if any account selections are present
     */
    private boolean errorOnAccountSelection;
    /**
     * If true, the plan will be compiled and an error will be thrown before Provisioner / LCM submission if any unmanaged plans are present
     */
    private boolean errorOnManualTask;
    /**
     * If true, the plan will be compiled and an error with be thrown before the Provisioner / LCM submission if expansion results in any new account creations
     */
    private boolean errorOnNewAccount;
    /**
     * If true, the plan will be compiled and an error will be thrown before Provisioner / LCM submission if any unanswered questions
     */
    private boolean errorOnProvisioningForms;

    /**
     * The name of the field to use to pass the Identity Name to the provisioning workflow
     */
    private String identityFieldName;

    /**
     * The name of the field to use to pass the Provisioning Plan to provisioning workflow
     */
    private String planFieldName;

    /**
     * If true, the provisioning workflow will be used; otherwise the Provisioner will be
     * invoked directly
     */
    private boolean useWorkflow;

    /**
     * The name of the workflow to use for provisioning, if useWorkflow is true
     */
    private String workflowName;

    /**
     * Basic constructor that creates a no-approval, no-notification provisioning
     */
    public ProvisioningArguments() {
        defaultExtraParameters = new HashMap<>();
        defaultExtraParameters.put(ProvisioningUtilities.PLAN_PARAM_APPROVAL_SCHEME, ProvisioningUtilities.NO_APPROVAL_SCHEME);
        defaultExtraParameters.put(ProvisioningUtilities.PLAN_PARAM_NOTIFICATION_SCHEME, ProvisioningUtilities.NO_APPROVAL_SCHEME);

        workflowName = "LCM Provisioning";
        useWorkflow = true;
        identityFieldName = "identityName";
        planFieldName = "plan";
    }

    public String getCaseNameTemplate() {
        return caseNameTemplate;
    }

    public Map<String, Object> getDefaultExtraParameters() {
        return defaultExtraParameters;
    }

    public String getIdentityFieldName() {
        return identityFieldName;
    }

    public String getPlanFieldName() {
        return planFieldName;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public boolean isErrorOnAccountSelection() {
        return errorOnAccountSelection;
    }

    public boolean isErrorOnManualTask() {
        return errorOnManualTask;
    }

    public boolean isErrorOnNewAccount() {
        return errorOnNewAccount;
    }

    public boolean isErrorOnProvisioningForms() {
        return errorOnProvisioningForms;
    }

    public boolean isUseWorkflow() {
        return useWorkflow;
    }

    /**
     * Merges in another workflow config
     * @param other The other workflow config
     */
    public void merge(ProvisioningArguments other) {
        setWorkflowName(other.workflowName);
        setUseWorkflow(other.useWorkflow);
        setPlanFieldName(other.planFieldName);
        setIdentityFieldName(other.identityFieldName);
        setCaseNameTemplate(other.caseNameTemplate);
        setErrorOnProvisioningForms(other.errorOnManualTask);
        setErrorOnAccountSelection(other.errorOnAccountSelection);
        setErrorOnNewAccount(other.errorOnNewAccount);
        setErrorOnManualTask(other.errorOnManualTask);

        if (other.defaultExtraParameters != null) {
            this.defaultExtraParameters.putAll(other.defaultExtraParameters);
        }
    }

    public void setCaseNameTemplate(String caseNameTemplate) {
        if (Util.isNotNullOrEmpty(caseNameTemplate)) {
            this.caseNameTemplate = caseNameTemplate;
        }
    }

    public void setDefaultExtraParameters(Map<String, Object> defaultExtraParameters) {
        if (defaultExtraParameters != null) {
            this.defaultExtraParameters = defaultExtraParameters;
        } else {
            this.defaultExtraParameters = new HashMap<>();
        }
    }

    public void setErrorOnAccountSelection(boolean errorOnAccountSelection) {
        this.errorOnAccountSelection = errorOnAccountSelection;
    }

    public void setErrorOnManualTask(boolean errorOnManualTask) {
        this.errorOnManualTask = errorOnManualTask;
    }

    public void setErrorOnNewAccount(boolean errorOnNewAccount) {
        this.errorOnNewAccount = errorOnNewAccount;
    }

    public void setErrorOnProvisioningForms(boolean errorOnProvisioningForms) {
        this.errorOnProvisioningForms = errorOnProvisioningForms;
    }

    public void setIdentityFieldName(String identityFieldName) {
        if (Util.isNotNullOrEmpty(identityFieldName)) {
            this.identityFieldName = identityFieldName;
        }
    }

    public void setPlanFieldName(String planFieldName) {
        if (Util.isNotNullOrEmpty(planFieldName)) {
            this.planFieldName = planFieldName;
        }
    }

    public void setUseWorkflow(boolean useWorkflow) {
        this.useWorkflow = useWorkflow;
    }

    public void setWorkflowName(String workflowName) {
        if (Util.isNotNullOrEmpty(workflowName)) {
            this.workflowName = workflowName;
        }
    }
}
