package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.ThingAccessUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;

/**
 * Implements some common access checks that are more complex than a straightforward
 * implementation.
 */
public class CommonAccessChecks {
    private static final Log log = LogFactory.getLog(CommonAccessChecks.class);

    /**
     * Plugin resource, containing IIQ context and user context
     */
    private final BasePluginResource pluginResource;

    /**
     * Constructs a new CommonAccessChecks utility with the given context and identity
     * @param context The IIQ context
     * @param source The requesting / subject Identity
     */
    public CommonAccessChecks(SailPointContext context, Identity source) {
        this.pluginResource = ThingAccessUtils.createFakePluginContext(context, source, null);
    }

    /**
     * Constructs a new CommonAccessChecks utility with the given existing {@link BasePluginResource}.
     * @param pluginResource The plugin resource
     */
    public CommonAccessChecks(BasePluginResource pluginResource) {
        this.pluginResource = pluginResource;
    }

    /**
     * Returns true if the logged in user is allowed to view the specific field on the
     * specified account, owned by the specified person.
     *
     * This will check three different criteria:
     *
     * - view:account:(application):field:(field)
     * - view:account:field:(field)
     * - view:account:(application)
     *
     * If the first criteria exists explicitly (i.e., not via a substring), it is
     * authoritative and the other two are skipped.
     *
     * Otherwise, both the second and third criteria must allow access.
     *
     * @param target The owner of the account
     * @param applicationName The name of the application
     * @param fieldName The field name on the application
     * @return True if the subject Identity can see the given field on the given account type
     * @throws GeneralException if anything fails during the check
     */
    public boolean canSeeLinkField(Identity target, String applicationName, String fieldName) throws GeneralException {
        SailPointContext context = pluginResource.getContext();

        DelegatedAccessController da = new DelegatedAccessController(pluginResource);

        String daTokenField = "view:account:field:" + fieldName;
        String daTokenAppField = "view:account:" + applicationName + ":field:" + fieldName;
        String daTokenApp = "view:account:" + applicationName;

        // Is the field visible on THIS account?
        if (DelegatedAccessController.explicitControlExists(context, daTokenAppField)) {
            boolean explicitCheck = da.canSeeIdentity(target, daTokenAppField);
            if (log.isDebugEnabled()) {
                log.debug("For token " + daTokenAppField + " the answer is " + explicitCheck);
            }
            return explicitCheck;
        }

        // Is the field visible on any account?
        boolean fieldVisible = da.canSeeIdentity(target, daTokenField);

        // Is an account of this type visible? (skip if field is not visible)
        boolean accountVisible = da.canSeeIdentity(target, daTokenApp);

        boolean visible = fieldVisible && accountVisible;

        if (log.isDebugEnabled()) {
            log.debug("For token " + daTokenField + " the answer is " + fieldVisible);
            log.debug("For token " + daTokenApp + " the answer is " + accountVisible);
        }

        return visible;
    }
}
