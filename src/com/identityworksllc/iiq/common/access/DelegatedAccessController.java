package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.Metered;
import com.identityworksllc.iiq.common.Utilities;
import com.identityworksllc.iiq.common.auth.DummyAuthContext;
import com.identityworksllc.iiq.common.cache.CacheEntry;
import com.identityworksllc.iiq.common.cache.Caches;
import com.identityworksllc.iiq.common.cache.VersionedCacheEntry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.identityworksllc.iiq.common.access.DelegatedAccessConstants.AUDIT_DA_CHECK;

/**
 * Utilities for delegated access checks. The access checks are implemented as follows:
 *
 * - A Configuration exists that contains ThingAccessUtils-friendly access controls.
 *
 * - Access controls are nested and cumulative. If a user can't read from an Identity,
 *   then they trivially can't read any of its attributes.
 *
 * - If a 'global' access control exists, its elements will always be added to the controls.
 *
 * - A purpose can be specified as a colon-delimited string, e.g., read:private:ssn. Access
 *   controls will be added from 'read', 'read:private', 'read:private:ssn', if they exist.
 *   More specific entries will override less specific entries. More specific entries can
 *   also specify a special '_remove' entry that will suppress upper-level controls.
 *
 * If no access controls exist for a purpose string, the answer is always yes.
 *
 * The specific purpose strings are arbitrary and are defined in the various IID plugins.
 * For example, the UI Enhancer has a slew of them, along with its own mechanism for
 * pointing the access checks at the DA adapter.
 */
public class DelegatedAccessController {

    /**
     * The cache of delegated access objects
     */
    private static final ConcurrentHashMap<String, CacheEntry<Map<String, Object>>> cache = new ConcurrentHashMap<>();

    /**
     * The IIQ context
     */
    private final SailPointContext context;

    /**
     * The logger
     */
    private final Log log;

    /**
     * The requester context, usually a plugin API call
     */
    private final UserContext requesterContext;

    /**
     * Constructs a new delegated access controller with the given IIQ and user context
     * @param context The IIQ context
     * @param requester The subject or requester Identity
     */
    public DelegatedAccessController(SailPointContext context, Identity requester) {
        this(new DummyAuthContext(context, requester.getName()));
    }

    /**
     * Constructor for delegated access
     * @param requesterContext A plugin resource (likely 'this' in your plugin)
     */
    public DelegatedAccessController(UserContext requesterContext) {
        this.context = requesterContext.getContext();
        this.log = LogFactory.getLog(DelegatedAccessController.class);

        // NOTE: You can use ThingAccessUtils.createFakePluginContext() to create one of these
        this.requesterContext = Objects.requireNonNull(requesterContext);
    }


    /**
     * Clear cache method, for use via the UI Toolbox cache rule
     */
    public static void clearCache() {
        cache.clear();

        Caches.getConfigurationCache().clear();
    }

    /**
     * Returns true if an explicit control exists for the given purpose token. In
     * other words, if the token is a:b:c, this method returns true only if a:b:c
     * exists in the configuration. A subset, such as a:b, will not match.
     *
     * @param context The IIQ context
     * @param purpose The colon-delimited purpose
     * @return True of an explicit control (i.e., does not match as a substring) exists for the given purpose
     * @throws GeneralException if anything fails
     */
    public static boolean explicitControlExists(SailPointContext context, String purpose) throws GeneralException {
        Configuration delegatedAccessConfig = getDelegatedAccessConfig(context);

        return delegatedAccessConfig.containsAttribute(purpose);
    }

    /**
     * Gets the cached controls for the given purpose or calculates a new one.
     * This can be a somewhat expensive operation, so caching is critical. Caches
     * time out after 60 seconds, or the value of configuration key
     * `IIQCommon.DelegatedAccessController.CacheTimeoutMillis`.
     *
     * @param context The SailPointContext to use the load the object
     * @param purpose The name of the object to retrieve
     * @return The object retrieved
     * @throws IllegalArgumentException if there is a problem loading the object
     */
    private static Map<String, Object> getAssembledControls(SailPointContext context, String purpose) throws IllegalArgumentException {
        return cache.compute(purpose, (key, value) -> {
            // This is the existing entry in the cache
            if (value == null || value.isExpired()) {
                int timeout = Configuration.getSystemConfig().getInt(DelegatedAccessConstants.CONFIG_DA_CACHE_TIMEOUT);
                if (timeout < 1) {
                    timeout = 60000;
                }

                try {
                    return new VersionedCacheEntry<>(new DelegatedAccessAssembler(context).assembleControls(purpose), System.currentTimeMillis() + timeout);
                } catch (GeneralException e) {
                    throw new IllegalArgumentException(e);
                }
            } else {
                return value;
            }
        }).getValue();
    }

    /**
     * Gets the delegated access config object if one exists
     * @param context The IIQ context
     * @return The Configuration if one exists
     * @throws GeneralException if the configuration does not exist
     */
    public static Configuration getDelegatedAccessConfig(SailPointContext context) throws GeneralException {
        SailPointContext previousContext = SailPointFactory.getCurrentContext();
        try {
            SailPointFactory.setContext(context);

            String configName = Configuration.getSystemConfig().getString(DelegatedAccessConstants.CONFIG_DELEGATED_ACCESS);
            if (Util.isNullOrEmpty(configName)) {
                throw new GeneralException("The system configuration must contain a property 'configDelegatedAccess'");
            }

            Configuration delegatedAccessConfig = Caches.getConfiguration(configName);
            if (delegatedAccessConfig == null) {
                throw new GeneralException("The delegated access configuration '" + configName + "' does not exist");
            }
            return delegatedAccessConfig;
        } finally {
            SailPointFactory.setContext(previousContext);
        }
    }

    /**
     * Gets the remote IP address of the user from the given HttpServletRequest. This can
     * be used in a situation where there is no FacesContext, like in a web service call.
     *
     * @param request The request to grab the IP from
     * @return The remote IP of the user
     */
    public static String getRemoteIp(HttpServletRequest request) {
        String remoteAddr = null;
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || remoteAddr.isEmpty()) {
                remoteAddr = request.getRemoteAddr();
            }
        }
        return remoteAddr;
    }

    /**
     * Returns true if, according to the configuration, the logged in user can do the given
     * action (purpose) against the target user. If the concept is generic, like "can load
     * plugin page", you can pass null as the target Identity.
     *
     * The attempt will not be audited.
     *
     * @param target The target user
     * @param purpose The purpose for which we are checking access (e.g., read, edit, etc)
     * @return true if access is allowed
     * @throws GeneralException on any check failures
     */
    public boolean canSeeIdentity(Identity target, String purpose) throws GeneralException {
        return canSeeIdentity(target, purpose, false);
    }

    /**
     * Returns true if, according to the configuration, the logged in user can do the given
     * action (purpose) against the target user. If the concept is generic, like "can load
     * plugin page", you can pass null as the target Identity.
     *
     * @param target The target user
     * @param purpose The purpose for which we are checking access (e.g., read, edit, etc)
     * @param audit True if we should audit this access attempt
     * @return true if access is allowed
     * @throws GeneralException on any check failures
     */
    public boolean canSeeIdentity(Identity target, String purpose, boolean audit) throws GeneralException {
        if (log.isDebugEnabled()) {
            log.debug("START: Access check for " + target + ", purpose = " + purpose);
        }

        boolean result = Metered.meter("DelegatedAccessController.canSeeIdentity.check." + purpose, () -> canSeeIdentityImpl(target, purpose));

        if (log.isDebugEnabled()) {
            log.debug("FINISH: Access check for " + target + ", purpose = " + purpose + ", allowed = " + result);
        }

        if (audit) {
            Metered.meter("DelegatedAccessController.canSeeIdentity.audit", () -> {
                AuditEvent auditEvent = new AuditEvent();
                auditEvent.setSource(requesterContext.getLoggedInUser().getId());
                auditEvent.setTarget(target.getId());
                auditEvent.setAction(AUDIT_DA_CHECK);
                auditEvent.setServerHost(Util.getHostName());
                if (requesterContext instanceof BasePluginResource) {
                    auditEvent.setClientHost(getRemoteIp(((BasePluginResource) requesterContext).getRequest()));
                }
                auditEvent.setString1(purpose);
                auditEvent.setString2(String.valueOf(result));

                Utilities.withPrivateContext((context) -> {
                    Auditor.log(auditEvent);
                });
            });
        }

        return result;
    }

    /**
     * The internal implementation of canSeeIdentity so that it can be easily metered
     * @param target The target identity
     * @param purpose The purpose for which we are checking access
     * @return True if access is allowed
     * @throws GeneralException on any check failure
     */
    private boolean canSeeIdentityImpl(Identity target, String purpose) throws GeneralException {
        Map<String, Object> controls = getAssembledControls(context, purpose);

        boolean result;

        if (controls.isEmpty()) {
            // TODO do we want to default to least-privilege and add privileges?
            result = true;
        } else {
            AccessCheckInput input = new AccessCheckInput();
            input.setTarget(target);
            input.setThingName(purpose);
            input.setConfiguration(controls);
            input.setUserContext(this.requesterContext);

            AccessCheckResponse response = AccessCheck.accessCheck(input);
            result = response.isAllowed();

            if (log.isDebugEnabled()) {
                log.debug("Access check response: " + response);
            }
        }

        return result;
    }

}
