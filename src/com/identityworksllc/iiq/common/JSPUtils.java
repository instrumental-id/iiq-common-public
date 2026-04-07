package com.identityworksllc.iiq.common;

import java.util.*;

import javax.servlet.http.HttpSession;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.logging.LogFactory;

import com.identityworksllc.iiq.common.logging.SLogger;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.QuickLink;
import sailpoint.service.MessageDTO;
import sailpoint.service.quicklink.QuickLinkLauncher;
import sailpoint.service.quicklink.QuickLinkLauncher.QuickLinkLaunchResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

/**
 * Utilities for the JSP-based quicklink launcher service
 */
public class JSPUtils {

    /**
     * A value holder for the details of a quicklink launch attempt
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    @ToString
    public static final class QuicklinkLaunchKey {
        /**
         * the Identity ID or name of the launcher
         */
        private String launcher;

        /**
         * The name of the quicklink to launch
         */
        private String quicklinkName;

        /**
         * the Identity ID or name of the target
         */
        private String target;

        /**
         * The timestamp of the launch key generation
         */
        private long timestamp;

        /**
         * Checks if the launch key has expired
         * @return true if the key has expired, false otherwise
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > timestamp + EXPIRATION_DURATION_MS;
        }
    }

    /**
     * The duration in milliseconds before a quicklink launch key expires (3 minutes)
     */
    private static final long EXPIRATION_DURATION_MS = 1000L * 60 * 3;

    /**
     * The logger for this class
     */
    private static final SLogger log = new SLogger(LogFactory.getLog(JSPUtils.class));

    /**
     * Checks whether the given launcher has access to launch the quicklink against
     * the given target.
     *
     * @param context The IIQ context
     * @param targetIdentity The target of the quicklink launch
     * @param launcherIdentity The launcher of the quicklink
     * @param quicklinkName The name of the quicklink to launch
     * @param ql The QuickLink object
     * @throws UnauthorizedAccessException if the access is not allowed
     * @throws GeneralException if an error occurs during the access check
     */
    private static void checkAccess(SailPointContext context, Identity targetIdentity, Identity launcherIdentity, String quicklinkName, QuickLink ql) throws GeneralException {
        AuthUtilities.QuickLinkAccessType qlAccessType;
        if (targetIdentity.getName().equals(launcherIdentity.getName())) {
            qlAccessType = AuthUtilities.QuickLinkAccessType.SELF;
        } else {
            qlAccessType = AuthUtilities.QuickLinkAccessType.OTHER;
        }
        AuthUtilities authUtils = new AuthUtilities(context);

        if (!authUtils.canAccessQuicklink(launcherIdentity, targetIdentity, ql, qlAccessType)) {
            log.error("User {0} cannot access QuickLink '{1}' for target {2}", launcherIdentity.getDisplayableName(), quicklinkName, targetIdentity.getDisplayableName());
            throw new UnauthorizedAccessException("User " + launcherIdentity.getName() + " cannot access QuickLink '" + quicklinkName + "' for target " + targetIdentity.getName());
        }
    }

    /**
     * Gets the input key for the quicklink launcher. You should expose
     * a plugin endpoint that produces this key.
     *
     * @param context The IIQ context
     * @param targetIdentity The target of the quicklink launch
     * @param launcherIdentity The launcher of the quicklink
     * @param quicklinkName The name of the quicklink to launch
     * @return An encrypted JSON string containing the configuration
     * @throws GeneralException if encryption goes wrong
     */
    public static String getLaunchKey(SailPointContext context, Identity targetIdentity, Identity launcherIdentity, String quicklinkName) throws GeneralException {
        QuickLink ql = context.getObject(QuickLink.class, quicklinkName);

        checkAccess(context, targetIdentity, launcherIdentity, quicklinkName, ql);

        QuicklinkLaunchKey key = new QuicklinkLaunchKey();
        key.setLauncher(launcherIdentity.getName());
        key.setTarget(targetIdentity.getName());
        key.setQuicklinkName(quicklinkName);
        key.setTimestamp(System.currentTimeMillis());

        log.info("Generating launch key for [launcher = {0}, target = {1}, quicklink = {2}]", launcherIdentity.getDisplayableName(), targetIdentity.getDisplayableName(), quicklinkName);

        Utilities.withPrivateContext((privateContext) -> {
            AuditEvent ae = new AuditEvent();
            ae.setAction("iiqcJspQuicklinkKeyGen");
            ae.setSource(launcherIdentity.getName());
            ae.setTarget(targetIdentity.getName());
            ae.setString1(quicklinkName);

            ae.setAttribute("launcher", launcherIdentity.getDisplayableName());
            ae.setAttribute("target", targetIdentity.getDisplayableName());
        });

        String json = JsonHelper.toJson(key);
        return context.encrypt(json);
    }

    /**
     * Gets the input key for the quicklink launcher. You should expose
     * a plugin endpoint that produces this key.
     *
     * @param context The IIQ context
     * @param target The target of the quicklink launch
     * @param launcher The launcher of the quicklink
     * @param quicklinkName The name of the quicklink to launch
     * @return An encrypted JSON string containing the configuration
     * @throws GeneralException if encryption goes wrong
     */
    public static String getLaunchKey(SailPointContext context, String target, String launcher, String quicklinkName) throws GeneralException {
        if (Util.isNullOrEmpty(quicklinkName)) {
            throw new GeneralException("Quicklink name is required");
        }

        if (Util.isNullOrEmpty(target)) {
            throw new GeneralException("Target is required");
        }

        if (Util.isNullOrEmpty(launcher)) {
            throw new GeneralException("Launcher is required");
        }

        Identity launcherIdentity = context.getObject(Identity.class, launcher);
        Identity targetIdentity = context.getObject(Identity.class, target);

        if (launcherIdentity == null) {
            throw new GeneralException("Launcher identity does not exist");
        }
        if (targetIdentity == null) {
            throw new GeneralException("Target identity does not exist");
        }

        return getLaunchKey(context, targetIdentity, launcherIdentity, quicklinkName);
    }
    /**
     * The IIQ context to use for this utility instance
     */
    private final SailPointContext context;
    /**
     * The HTTP session associated with this utility instance
     */
    private final HttpSession session;

    /**
     * Constructs a new JSPUtils with an HttpSession and the default threadlocal context
     * @param session The HTTP session
     * @throws GeneralException if getting the current context fails
     */
    public JSPUtils(HttpSession session) throws GeneralException {
        this(SailPointFactory.getCurrentContext(), session);
    }

    /**
     * Constructs a new JSPUtils with the specified HttpSession and context
     * @param context The IIQ context to use for this utility instance
     * @param session The HTTP session
     */
    public JSPUtils(SailPointContext context, HttpSession session) {
        this.context = context;
        this.session = session;
    }

    /**
     * Launches a quicklink for the QL, target, and launcher specified in the encrypted
     * key value. If the quicklink launch produces a form work item, the user will be
     * redirected to the commonWorkItem page.
     *
     * @param encryptedKey An encrypted configuration key, from {@link #getLaunchKey(SailPointContext, String, String, String)}
     * @return The redirect URL
     * @throws GeneralException if decryption or quicklink launch fails
     */
    public String launchQuicklink(String encryptedKey) throws GeneralException {
        Configuration systemConfig = Configuration.getSystemConfig();

        String baseUrl = (String) systemConfig.get("serverRootPath");
        String redirect = baseUrl + "/home.jsf";

        if (Util.isNullOrEmpty(encryptedKey)) {
            log.error("Encrypted quicklink config is null or empty");
            return redirect;
        }

        String decryptedKey = context.decrypt(encryptedKey);
        if (log.isDebugEnabled()) {
            log.debug("Decrypted quicklink config: {0}", decryptedKey);
        }
        if (!decryptedKey.startsWith("{")) {
            log.error("Invalid format for input to quicklink launcher");
            return redirect;
        }

        QuicklinkLaunchKey launchKey = JsonHelper.fromJson(QuicklinkLaunchKey.class, decryptedKey);

        if (log.isDebugEnabled()) {
            log.debug("Decrypted quicklink launch key: {0}", launchKey);
        }

        String launcher = launchKey.getLauncher();
        String quicklinkName = launchKey.getQuicklinkName();
        String target = launchKey.getTarget();

        long timestampLong = launchKey.getTimestamp();
        long expiration = timestampLong + (1000L * 60 * 3);

        if (System.currentTimeMillis() > expiration) {
            Date date = new Date(expiration);
            log.error("Quicklink launch key expired at {0}", date);
            return redirect;
        }

        if (Util.isNullOrEmpty(target)) {
            target = context.getUserName();
        }

        QuickLink ql = context.getObject(QuickLink.class, quicklinkName);
        if (ql == null) {
            log.error("No such quicklink in JSP launch: {0}", quicklinkName);
            return redirect;
        }

        Identity launcherIdentity = context.getObject(Identity.class, launcher);
        Identity targetIdentity = context.getObject(Identity.class, target);

        if (targetIdentity == null || launcherIdentity == null) {
            log.error("Either target or launcher identity does not exist. Input values: {0} {1}", target, launcher);
            return redirect;
        }

        log.debug("Attempting QuickLink launch: name = {0}, launcher = {1}, target = {2}", quicklinkName, launcherIdentity.getDisplayableName(), targetIdentity.getDisplayableName());

        checkAccess(context, targetIdentity, launcherIdentity, quicklinkName, ql);

        Utilities.withPrivateContext((privateContext) -> {
            AuditEvent ae = new AuditEvent();
            ae.setAction("iiqcJspQuicklinkLaunch");
            ae.setSource(launcherIdentity.getName());
            ae.setTarget(targetIdentity.getName());
            ae.setString1(quicklinkName);

            ae.setAttribute("launcher", launcherIdentity.getDisplayableName());
            ae.setAttribute("target", targetIdentity.getDisplayableName());

            privateContext.saveObject(ae);
            privateContext.commitTransaction();
        });

        QuickLinkLauncher api = new QuickLinkLauncher(context, launcherIdentity);

        // QuickLinkLauncher populates this object with output in launchWorkflow()
        Map<String, Object> sessionMap = new HashMap<>();

        List<String> ids = new ArrayList<>();
        ids.add(targetIdentity.getId());
        QuickLinkLaunchResult result = api.launchWorkflow(ql, targetIdentity.getId(), ids, sessionMap);

        if (sessionMap.containsKey("workItemFwdNextPage")) {
            // Copy the QuickLink output into the HTTP session
            for (String key : sessionMap.keySet()) {
                this.session.setAttribute(key, sessionMap.get(key));
            }
            redirect = baseUrl + "/workitem/commonWorkItem.jsf#/commonWorkItem/session";

            if (result.getMessages() != null && !result.getMessages().isEmpty()) {
                for(MessageDTO msg : result.getMessages()) {
                    log.info("Message from quicklink {0} invoked by {1}: {2}", launcherIdentity.getDisplayableName(), ql.getName(), msg.getMessageOrKey());
                }
            }
        }

        return redirect;

    }

}
