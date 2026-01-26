package com.identityworksllc.iiq.common.access;

import java.util.Map;

/**
 * Constants for the DelegatedAccess classes
 */
public class DelegatedAccessConstants {
    public static final String ACCESS_ACCOUNT = "account";
    public static final String ACCESS_BUTTON = "button";
    public static final String ACCESS_EDIT = "edit";
    public static final String ACCESS_FIELD = "field";
    public static final String ACCESS_VIEW = "view";
    public static final String ACCESS_VIEW_ACCOUNT = "view:account";
    public static final String ACCESS_VIEW_ACCOUNT_FIELD = "view:account:field";
    public static final String ACCESS_VIEW_FIELD = "view:field";

    /**
     * The audit event for an access check
     */
    public static final String AUDIT_DA_CHECK = "daCanSeeIdentity";

    /**
     * Indicates that particular actions (purposes) should be bypassed, invoking
     * their native security checks instead of the DA check.
     */
    public static final String CONFIG_BYPASS_ACTIONS = "_bypassActions";

    /**
     * The System Config entry indicating the access check cache time
     */
    public static final String CONFIG_DA_CACHE_TIMEOUT = "IIQCommon.DelegatedAccessController.CacheTimeoutMillis";

    /**
     * The System Config entry indicating the name of the Delegated Access config
     */
    public static final String CONFIG_DELEGATED_ACCESS = "IIQCommon.DelegatedAccessController.Configuration";

    /**
     * The action being performed, whose access is being checked
     */
    public static final String INPUT_ACTION = "action";

    /**
     * The configuration object for the DA check; probably a Common Security map
     */
    public static final String INPUT_CONFIG = "config";

    /**
     * The IIQ context
     */
    public static final String INPUT_CONTEXT = "context";

    /**
     * The plugin resource, or a fake one, used for Identity-level security checking
     */
    public static final String INPUT_PLUGIN_RESOURCE = "pluginResource";

    /**
     * The target Identity object, if one exists
     */
    public static final String INPUT_TARGET = "target";

    /**
     * The target thing name
     */
    public static final String INPUT_THING_NAME = "name";

    /**
     * A constant indicating the return value from {@link DelegatedAccessAdapter#apply(Map)}
     * when the access is allowed.
     */
    public static final boolean OUTCOME_ALLOWED = false;

    /**
     * A constant indicating the return value from {@link DelegatedAccessAdapter#apply(Map)}
     * when the access is NOT allowed.
     */
    public static final boolean OUTCOME_DENIED = true;

    /**
     * The divider between all tokens
     */
    public static final String TOKEN_DIVIDER = ":";

    private DelegatedAccessConstants() {}
}
