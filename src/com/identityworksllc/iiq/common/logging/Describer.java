package com.identityworksllc.iiq.common.logging;

import sailpoint.object.Link;
import sailpoint.tools.Util;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.VelocityUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Describes objects in a human-readable format. This is intended
 * to be used by both IIQCommon internal code and customer code.
 */
public class Describer {
    /**
     * The logger for this class
     */
    private static final SLogger log = new SLogger(Describer.class);

    public static String describe(Link link) {
        Configuration sysConfig = Configuration.getSystemConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> describerConfig = (Map<String, Object>) sysConfig.get("IIQCommon.Describer.Config");

        var defaultDisplay = "Link[application =" + link.getApplicationName() + ", nativeIdentity = " + link.getNativeIdentity() + "]";

        if (Util.isEmpty(describerConfig) || !(describerConfig.get("Link") instanceof String)) {
            return defaultDisplay;
        } else {
            var template = describerConfig.get("Link").toString();

            Map<String, Object> model = new HashMap<>();
            model.put("link", link);
            model.put("nativeIdentity", link.getNativeIdentity());
            model.put("applicationName", link.getApplicationName());
            model.putAll(link.getAttributes());

            try {
                var result = VelocityUtil.secureRender(template, model, Locale.getDefault(), TimeZone.getDefault());
                if (Util.isNullOrEmpty(result)) {
                    log.warn("Describer template returned null or empty for {0}", defaultDisplay);
                    return defaultDisplay;
                } else {
                    return result;
                }
            } catch(GeneralException e) {
                log.error("Error rendering describer template for " + defaultDisplay, e);
                return defaultDisplay;
            }
        }
    }

    /**
     * Gets the human-readable description of the given Identity.
     * @param identity The Identity to describe
     * @return The human-readable description
     */
    public static String describe(Identity identity) {
        Configuration sysConfig = Configuration.getSystemConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> describerConfig = (Map<String, Object>) sysConfig.get("IIQCommon.Describer.Config");

        var defaultDisplay = identity.getDisplayableName() + " (" + identity.getName() + ")";

        if (Util.isEmpty(describerConfig) || !(describerConfig.get("Identity") instanceof String)) {
            return defaultDisplay;
        } else {
            var template = describerConfig.get("Identity").toString();

            Map<String, Object> model = new HashMap<>();
            model.put("identity", identity);
            model.put("displayName", identity.getDisplayableName());
            model.put("name", identity.getName());
            model.putAll(identity.getAttributes());

            try {
                var result = VelocityUtil.secureRender(template, model, Locale.getDefault(), TimeZone.getDefault());
                if (Util.isNullOrEmpty(result)) {
                    log.warn("Describer template returned null or empty for {0}", defaultDisplay);
                    return defaultDisplay;
                } else {
                    return result;
                }
            } catch(GeneralException e) {
                log.error("Error rendering describer template for " + defaultDisplay, e);
                return defaultDisplay;
            }
        }
    }
}
