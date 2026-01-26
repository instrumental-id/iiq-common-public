package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.Metered;
import com.identityworksllc.iiq.common.ThingAccessUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.identityworksllc.iiq.common.access.DelegatedAccessConstants.*;

/**
 * A default adapter for delegated access checks.
 *
 * This class implements the OOTB Java {@link Function} interface to allow it to be
 * shared across plugins without relying on reflection.
 *
 * The input contains various arguments for the access check. In particular, it contains
 * an 'action' and an optional 'name'. These will be concatenated with a ':' to form the
 * purpose string. For example, 'view:field:myField', where 'view:field' is the action and
 * 'myField' is the name.
 *
 * Certain actions can bypass the DA checks, deferring to a default ThingAccessUtils
 * checks. This is particularly useful if the particular thing has good inline configuration
 * that doesn't need to be shared or nested.
 *
 * The output is a {@link Boolean} indicating whether the access is DENIED -
 * i.e., true = deny, false = allow.
 */
public class DelegatedAccessAdapter implements Function<Map<String, Object>, Boolean> {

    private static final Log logger = LogFactory.getLog(DelegatedAccessAdapter.class);

    /**
     * Applies the delegated access check to the input. The input is expected to contain
     * the configuration arguments in {@link DelegatedAccessConstants}. The output
     * indicates whether the access check DENIES access (true = deny).
     *
     * @param input the map containing configuration arguments
     * @return true if the access check is DENIED, false if the access check is ALLOWED
     */
    @Override
    public Boolean apply(Map<String, Object> input) {
        SailPointContext context = (SailPointContext) input.get(INPUT_CONTEXT);
        BasePluginResource pluginContext = (BasePluginResource) input.get(INPUT_PLUGIN_RESOURCE);
        Identity target = (Identity) input.get(INPUT_TARGET);

        String action = (String) input.get(INPUT_ACTION);
        String name = (String) input.get(INPUT_THING_NAME);

        try {
            return Metered.meter("DelegatedAccessAdapter.apply", () -> {
                List<String> bypassActions = new ArrayList<>();

                Configuration delegatedAccessConfig = DelegatedAccessController.getDelegatedAccessConfig(context);
                if (delegatedAccessConfig.get(CONFIG_BYPASS_ACTIONS) instanceof List) {
                    bypassActions.addAll(Util.otol(delegatedAccessConfig.get(CONFIG_BYPASS_ACTIONS)));
                }

                if (bypassActions.contains(action)) {
                    // We will just use the default ThingAccessUtils for this
                    Object restrictions = input.get(INPUT_CONFIG);
                    if (restrictions instanceof Map) {
                        boolean outcome = ThingAccessUtils.checkThingAccess(pluginContext, target, name, (Map<String, Object>) restrictions);

                        // Yeah I know, but the output is inverted and may change type later
                        return outcome ? OUTCOME_ALLOWED : OUTCOME_DENIED;
                    }
                }

                DelegatedAccessController da = new DelegatedAccessController(pluginContext);

                String actionToken = action;
                if (Util.isNotNullOrEmpty(name)) {
                    actionToken = actionToken + TOKEN_DIVIDER + name;
                }

                if (da.canSeeIdentity(target, actionToken)) {
                    return OUTCOME_ALLOWED;
                } else {
                    return OUTCOME_DENIED;
                }
            });
        } catch(GeneralException e) {
            logger.error("Caught an error verifying access to " + action + " " + name, e);
            return OUTCOME_DENIED;
        }
    }
}
