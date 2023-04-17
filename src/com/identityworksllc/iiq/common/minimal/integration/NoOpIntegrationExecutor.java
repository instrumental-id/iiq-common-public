package com.identityworksllc.iiq.common.minimal.integration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements a no-op integration executor, suitable for detached apps. The
 * integration will accept any inputs and return success. The result will
 * contain the existing Link with the given changes applied, which makes
 * it suitable for optimistic provisioning.
 *
 * Note that you could specify this integration executor only for specific
 * operations on the configuration or allow it to be used for any.
 */
public class NoOpIntegrationExecutor extends AbstractCommonIntegrationExecutor {

    private static final Log log = LogFactory.getLog(NoOpIntegrationExecutor.class);
    private boolean runAfterRule;
    private boolean runBeforeRule;

    public NoOpIntegrationExecutor() {
        this.runBeforeRule = false;
        this.runAfterRule = false;
    }

    /**
     * Always return committed from checkStatus
     * @param requestId Unclear
     * @return A committed result
     * @throws Exception on failures
     */
    @Override
    public ProvisioningResult checkStatus(String requestId) throws Exception {
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        return result;
    }

    @Override
    public void configure(SailPointContext context, IntegrationConfig config) throws Exception {
        super.configure(context, config);

        Attributes<String, Object> attributes = config.getAttributes();
        if (attributes != null) {
            if (attributes.containsKey("runBeforeRule")) {
                this.runBeforeRule = attributes.getBoolean("runBeforeRule");
            }
            if (attributes.containsKey("runAfterRule")) {
                this.runAfterRule = attributes.getBoolean("runAfterRule");
            }
        }
    }

    /**
     * Finds the existing Link in IIQ based on the account request
     * @param acctReq The account request being provisioned
     * @return The Link, if any, otherwise null
     * @throws GeneralException if the AccountRequest matches more than one Link
     */
    protected Link findExistingLink(ProvisioningPlan.AccountRequest acctReq) throws GeneralException {
        Filter filter = Filter.and(Filter.eq("application.name", acctReq.getApplicationName()), Filter.eq("nativeIdentity", acctReq.getNativeIdentity()));
        QueryOptions qo = new QueryOptions();
        qo.addFilter(filter);
        List<Link> existingLinks = super.getContext().getObjects(Link.class, qo);
        if (existingLinks == null || existingLinks.size() == 0) {
            return null;
        } else if (existingLinks.size() == 1) {
            return existingLinks.get(0);
        } else {
            throw new GeneralException("The provisioning plan with app = " + acctReq.getApplicationName() + ", native identity = " + acctReq.getNativeIdentity() + " matches " + existingLinks.size() + " existing Links??");
        }
    }

    /**
     * Generates the ResourceObject from the existing link and the account request. This
     * will be returned back through the connector like most of the "real" connectors.
     * @param existingLink The existing Link, if one exists, or null
     * @param acctReq The account request that was just provisioned
     * @return The resulting ResourceObject with the current state of the account
     * @throws GeneralException if any failures occur
     */
    @SuppressWarnings("unchecked")
    protected ResourceObject generateResourceObject(Link existingLink, ProvisioningPlan.AccountRequest acctReq) throws GeneralException {
        ResourceObject ro = new ResourceObject();
        if (acctReq.getOperation() != null && acctReq.getOperation() == ProvisioningPlan.AccountRequest.Operation.Delete) {
            ro.setDelete(true);
            ro.setRemove(true);
        } else {
            if (existingLink != null) {
                ro.setAttributes(existingLink.getAttributes().mediumClone());
            }

            if (acctReq.getOperation() != null && acctReq.getOperation() == ProvisioningPlan.AccountRequest.Operation.Disable) {
                ro.setAttribute("IIQDisabled", true);
            } else if (acctReq.getOperation() != null && acctReq.getOperation() == ProvisioningPlan.AccountRequest.Operation.Enable) {
                ro.setAttribute("IIQDisabled", false);
            }

            ro.setIdentity(acctReq.getNativeIdentity());
            if (acctReq.getAttributeRequests() != null) {
                for(ProvisioningPlan.AttributeRequest attr : acctReq.getAttributeRequests()) {
                    if (attr.getOperation() == ProvisioningPlan.Operation.Set) {
                        ro.setAttribute(attr.getName(), attr.getValue());
                    } else if (attr.getOperation() == ProvisioningPlan.Operation.Add) {
                        List<String> values = ro.getStringList(attr.getName());
                        if (values == null) {
                            values = new ArrayList<>();
                        }
                        if (attr.getValue() instanceof Collection) {
                            values.addAll((Collection<? extends String>) attr.getValue());
                        } else {
                            values.add((String) attr.getValue());
                        }
                        ro.setAttribute(attr.getName(), values);
                    } else if (attr.getOperation() == ProvisioningPlan.Operation.Remove) {
                        List<String> values = ro.getStringList(attr.getName());
                        if (values == null) {
                            values = new ArrayList<>();
                        } else {
                            values = new ArrayList<>(values);
                        }
                        if (attr.getValue() instanceof Collection) {
                            values.removeAll((Collection<? extends String>) attr.getValue());
                        } else {
                            String attrValue = Util.otoa(attr.getValue());
                            if (Util.isNotNullOrEmpty(attrValue)) {
                                values.remove(attrValue);
                            }
                        }
                        ro.setAttribute(attr.getName(), values);
                    }
                }
            }
        }
        Application application = acctReq.getApplication(getContext());
        ro.setAttribute(application.getAccountSchema().getIdentityAttribute(), acctReq.getNativeIdentity());
        if (log.isDebugEnabled()) {
            log.debug("Returning ResourceObject " + ro.toXml());
        }
        return ro;
    }

    /**
     * Creates a new plan to use as a container for plans of a given application
     * type.
     *
     * @param plan The existing plan to copy values from
     * @param applicationName The application name
     * @return The new plan
     */
    private ProvisioningPlan newPlan(ProvisioningPlan plan, String applicationName) {
        ProvisioningPlan appPlan = new ProvisioningPlan();
        if (plan.getArguments() != null) {
            appPlan.setArguments(new Attributes<>(plan.getArguments()));
        }
        appPlan.setIdentity(plan.getIdentity());
        appPlan.setIntegrationData(plan.getIntegrationData());
        appPlan.setTargetIntegration(plan.getTargetIntegration());
        appPlan.setComments(plan.getComments());

        if (appPlan.getArguments() == null) {
            appPlan.setArguments(new Attributes<>());
        }
        appPlan.getArguments().put("noOpApplication", applicationName);
        return appPlan;
    }

    /**
     * Fakes invocation of the provisioning operations for each of the account requests in the
     * given plan. The before and after provisioning rules may optionally be invoked.
     *
     * @param plan The plan being provisioned
     * @return the provisioning result
     * @throws GeneralException on failures
     */
    @Override
    public ProvisioningResult provision(ProvisioningPlan plan) throws GeneralException {
        beforeProvision(plan);
        ProvisioningResult result = new ProvisioningResult();

        if (plan.getAccountRequests() != null) {
            Map<String, ProvisioningPlan> plansByApplication = new HashMap<>();
            for(ProvisioningPlan.AccountRequest acctReq : plan.getAccountRequests()) {
                plansByApplication.computeIfAbsent(acctReq.getApplicationName(), (k) -> newPlan(plan, acctReq.getApplicationName()));
                plansByApplication.get(acctReq.getApplicationName()).add(acctReq);
            }

            for(String app : plansByApplication.keySet()) {
                ProvisioningPlan dividedPlan = plansByApplication.get(app);
                if (this.runBeforeRule) {
                    runBeforeProvisioningRule(dividedPlan, app);
                }
                for(ProvisioningPlan.AccountRequest acctReq : Util.safeIterable(dividedPlan.getAccountRequests())) {
                    provision(dividedPlan, acctReq);
                }
                if (this.runAfterRule) {
                    runAfterProvisioningRule(dividedPlan, app);
                }
            }
        }
        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        if (log.isDebugEnabled()) {
            log.debug(result.toXml());
        }
        afterProvision(plan, result);
        return result;
    }

    /**
     * Provisions the account request in question, calling the various hook and default
     * implementation methods to perform the various steps.
     *
     * @param plan The provisioning plan
     * @param acctReq The account request
     * @throws GeneralException if any failures occur
     */
    private void provision(ProvisioningPlan plan, ProvisioningPlan.AccountRequest acctReq) throws GeneralException {
        beforeProvisionAccount(plan, acctReq);
        Link existingLink = findExistingLink(acctReq);
        ProvisioningResult accountResult = new ProvisioningResult();
        accountResult.setStatus(ProvisioningResult.STATUS_COMMITTED);
        accountResult.setObject(generateResourceObject(existingLink, acctReq));
        acctReq.setResult(accountResult);
        afterProvisionAccount(plan, acctReq);
    }

    /**
     * Runs the Application's after-prov rule. You should be sure that you only use this
     * when your application's after-prov rules are consequence free. You will NOT be provided
     * a connector.
     *
     * Additionally, the variable 'noOpIntegration' will be present and set to Boolean.TRUE.
     *
     * @param plan The plan
     * @param applicationName The application name
     * @throws GeneralException on failures
     */
    private void runAfterProvisioningRule(ProvisioningPlan plan, String applicationName) throws GeneralException {
        Application application = getContext().getObject(Application.class, applicationName);
        if (application != null && application.getAfterProvisioningRule() != null) {
            Rule afterProvRule = getContext().getObject(Rule.class, application.getAfterProvisioningRule());
            if (afterProvRule != null) {
                Map<String, Object> ruleInputs = new HashMap<>();
                ruleInputs.put("plan", plan);
                ruleInputs.put("application", application);
                ruleInputs.put("noOpIntegration", Boolean.TRUE);
                ruleInputs.put("result", plan.getResult());

                getContext().runRule(afterProvRule, ruleInputs);
            }
        }
    }

    /**
     * Runs the Application's before-prov rule. You should be sure that you only use this
     * when your application's before-prov rules are consequence free outside of IIQ. You will
     * NOT be provided a connector.
     *
     * Additionally, the variable 'noOpIntegration' will be present and set to Boolean.TRUE.
     *
     * @param plan The plan
     * @param applicationName The application name
     * @throws GeneralException on failures
     */
    private void runBeforeProvisioningRule(ProvisioningPlan plan, String applicationName) throws GeneralException {
        Application application = getContext().getObject(Application.class, applicationName);
        if (application != null && application.getBeforeProvisioningRule() != null) {
            Rule beforeProvRule = getContext().getObject(Rule.class, application.getBeforeProvisioningRule());
            if (beforeProvRule != null) {
                Map<String, Object> ruleInputs = new HashMap<>();
                ruleInputs.put("plan", plan);
                ruleInputs.put("application", application);
                ruleInputs.put("noOpIntegration", Boolean.TRUE);

                getContext().runRule(beforeProvRule, ruleInputs);
            }
        }
    }


}
