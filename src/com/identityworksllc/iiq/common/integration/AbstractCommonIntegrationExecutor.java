package com.identityworksllc.iiq.common.integration;

import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.GeneralException;

/**
 * Superclass containing common hooks for the custom integration executor classes
 */
public abstract class AbstractCommonIntegrationExecutor extends AbstractIntegrationExecutor {

    /**
     * A hook that can be implemented by a subclass to be called after provisioning
     * @param plan The plan that was just provisioned
     * @param result The result of the plan provisioning
     * @throws GeneralException on failures
     */
    protected void afterProvision(ProvisioningPlan plan, ProvisioningResult result) throws GeneralException {
        /* Nothing here, for overrides only */
    }

    /**
     * A hook that can be implemented by a subclass to be called after each account request provisioning
     * @param plan The plan being provisioned
     * @param accountRequest The account request that was just provisioned
     * @throws GeneralException on failures
     */
    protected void afterProvisionAccount(ProvisioningPlan plan, ProvisioningPlan.AccountRequest accountRequest) throws GeneralException {

    }

    /**
     * A hook that can be implemented by a subclass to be called before provisioning
     * @param plan The plan about to be provisioned
     * @throws GeneralException on failures
     */
    protected void beforeProvision(ProvisioningPlan plan) throws GeneralException {
        /* Nothing here, for overrides only */
    }

    /**
     * A hook that can be implemented by a subclass to be called before each account request provisioning
     * @param plan The plan being provisioned
     * @param accountRequest The account request about to be provisioned
     * @throws GeneralException on failures
     */
    protected void beforeProvisionAccount(ProvisioningPlan plan, ProvisioningPlan.AccountRequest accountRequest) throws GeneralException {

    }

}
