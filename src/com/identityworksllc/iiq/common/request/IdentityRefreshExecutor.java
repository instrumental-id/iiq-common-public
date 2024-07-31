package com.identityworksllc.iiq.common.request;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Identitizer;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.request.AbstractRequestExecutor;
import sailpoint.request.RequestPermanentException;
import sailpoint.request.RequestTemporaryException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Date;
import java.util.Map;

/**
 * Request executor to do an async refresh on a single user. The Request must
 * contain at least a String identityName. All other arguments on the Request
 * will be interpreted as input options to the Identitizer API.
 *
 * If no other arguments are supplied beyond an 'identityName', the refresh will
 * run with the following default options set to true:
 *
 * - promoteAttributes
 * - correlateEntitlements (refresh assigned / detected roles)
 * - provisionIfChanged
 * - processTriggers
 * - refreshManagerStatus
 *
 * The following options will always be set to true:
 *
 * - noCheckPendingWorkflow
 */
public class IdentityRefreshExecutor extends AbstractRequestExecutor {
    /**
     * Launches an Identity Refresh request against the given Identity, with the given options.
     * The "IDW Identity Refresh Request" request definition must be installed in the IIQ
     * system for this to work properly.
     *
     * @param context The IIQ context to use to launch the request
     * @param targetIdentity The target Identity to refresh
     * @param options Any refresh options (may be null)
     * @throws GeneralException if launching the request fails
     * @throws IllegalArgumentException if the inputs are improper
     */
    public static void launchRequest(SailPointContext context, Identity targetIdentity, Map<String, Object> options) throws GeneralException {
        if (targetIdentity == null) {
            throw new IllegalArgumentException("targetIdentity is null");
        }
        launchRequest(context, targetIdentity.getName(), options);
    }

    /**
     * Launches an Identity Refresh request against the given Identity, with the given options.
     * The "IDW Identity Refresh Request" request definition must be installed in the IIQ
     * system for this to work properly.
     *
     * @param context The IIQ context to use to launch the request
     * @param targetIdentityName The target Identity to refresh
     * @param options Any refresh options (may be null)
     * @throws GeneralException if launching the request fails
     * @throws IllegalArgumentException if the inputs are improper
     */
    public static void launchRequest(SailPointContext context, String targetIdentityName, Map<String, Object> options) throws GeneralException {
        launchRequest(context, targetIdentityName, options, 0L);
    }

    /**
     * Launches an Identity Refresh request against the given Identity, with the given options.
     * The "IDW Identity Refresh Request" request definition must be installed in the IIQ
     * system for this to work properly.
     *
     * @param context The IIQ context to use to launch the request
     * @param targetIdentityName The name of the target Identity to refresh
     * @param options Any refresh options (may be null)
     * @param launchTimestamp The epoch timestamp at which this request should be launched; less than 1 indicates immediate
     * @throws GeneralException if launching the request fails
     * @throws IllegalArgumentException if the inputs are improper
     */
    public static void launchRequest(SailPointContext context, String targetIdentityName, Map<String, Object> options, long launchTimestamp) throws GeneralException {
        if (Util.isNullOrEmpty(targetIdentityName)) {
            throw new IllegalArgumentException("targetIdentityName is null or empty");
        }

        Request input = new Request();
        input.setName("Refresh Identity " + targetIdentityName);
        input.setDefinition(context.getObjectByName(RequestDefinition.class, REQUEST_DEFINITION));

        if (launchTimestamp > 0) {
            input.setNextLaunch(new Date(launchTimestamp));
        } else {
            input.setNextLaunch(new Date());
        }

        Attributes<String, Object> arguments = new Attributes<>();
        if (options != null) {
            arguments.putAll(options);
        }
        arguments.put("identityName", targetIdentityName);

        input.setAttributes(arguments);

        RequestManager.addRequest(context, input);
    }

    /**
     * The name of the request definition associated with this executor
     */
    @SuppressWarnings("unused")
    public static final String REQUEST_DEFINITION = "IDW Identity Refresh Request";

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(IdentityRefreshExecutor.class);

    /**
     * Invokes the Identitizer to refresh the Identity with the given parameters.
     * The Request invoking this handler must at least supply an 'identityName'
     * in its attributes. All other Request attributes will be interpreted as
     * arguments to the Identitizer.
     *
     * @param context The IIQ context for this thread
     * @param request The request to execute
     * @param attributes Unknown, some extra attributes?
     * @throws RequestPermanentException if the request fails in a way that cannot be retried
     * @throws RequestTemporaryException if the request fails in a way that can be retried (e.g., temporary connection failure)
     */
    @Override
    public void execute(SailPointContext context, Request request, Attributes<String, Object> attributes) throws RequestPermanentException, RequestTemporaryException {
        String identityName = Util.otoa(Util.get(request.getAttributes(), "identityName"));
        if (Util.isNullOrEmpty(identityName)) {
            throw new RequestPermanentException("An identity name is required for a " + this.getClass().getName() + " request");
        }
        try {
            Identity identity = context.getObject(Identity.class, identityName);
            if (identity == null) {
                throw new RequestPermanentException("Identity " + identityName + " does not exist or is not accessible");
            }

            Attributes<String, Object> refreshArguments = new Attributes<>();
            if (request.getAttributes() != null) {
                refreshArguments.putAll(request.getAttributes());
                refreshArguments.remove("identityName");
            }

            if (refreshArguments.isEmpty()) {
                refreshArguments.put(Identitizer.ARG_PROMOTE_ATTRIBUTES, "true");
                refreshArguments.put(Identitizer.ARG_CORRELATE_ENTITLEMENTS, "true");
                refreshArguments.put(Identitizer.ARG_PROVISION_IF_CHANGED, "true");
                refreshArguments.put(Identitizer.ARG_PROCESS_TRIGGERS, "true");
                refreshArguments.put(Identitizer.ARG_REFRESH_MANAGER_STATUS, "true");
            }

            // Always do this, so that we don't silently fail to refresh the user
            // if there is a pending workflow already running.
            refreshArguments.put(Identitizer.ARG_NO_CHECK_PENDING_WORKFLOW, "true");

            if (log.isInfoEnabled()) {
                log.info("Executing requested refresh for identity = " + identity + ", arguments = " + refreshArguments);
            }

            Identitizer identitizer = new Identitizer(context, refreshArguments);
            identitizer.refresh(identity);
        } catch(GeneralException e) {
            log.error("Caught an exception processing an identity refresh on " + identityName, e);
            throw new RequestPermanentException(e);
        }
    }
}
