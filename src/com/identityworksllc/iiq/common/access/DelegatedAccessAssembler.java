package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.ConfigurationMerger;
import com.identityworksllc.iiq.common.Metered;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.Map;

import static com.identityworksllc.iiq.common.access.DelegatedAccessController.getDelegatedAccessConfig;

/**
 * Assembles the controls (Common Security assertions) that are present on the
 * given purpose string, or any of its parent substrings.
 *
 * Additionally, any controls in the special key 'global' will be added.
 *
 * More specific assertions will override less specific ones.
 */
public class DelegatedAccessAssembler {

    private final SailPointContext context;

    public DelegatedAccessAssembler(SailPointContext context) {
        this.context = context;
    }

    /**
     * Assembles the thing controls for the given target and purpose
     *
     * @param purpose The input purpose
     * @return The assembled / merged set of controls for the given purpose
     * @throws GeneralException if any failures occur assembling controls
     */
    public Map<String, Object> assembleControls(String purpose) throws GeneralException {
        return Metered.meter("DelegatedAccessAssembler.assembleControls", () -> {
            Configuration delegatedAccessConfig = getDelegatedAccessConfig(context);

            Map<String, Object> controls = new HashMap<>();

            if (delegatedAccessConfig.get("global") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> globalControls = (Map<String, Object>) delegatedAccessConfig.get("global");

                controls.putAll(globalControls);
            }

            if (Util.isNotNullOrEmpty(purpose)) {
                String[] purposePieces = purpose.split(DelegatedAccessConstants.TOKEN_DIVIDER);
                String assembledPurpose = "";
                // Loop over each substring of the purpose, such as a, a:b, a:b:c, etc.
                // TODO support wildcards, such as a:*:c
                for(String purposePiece : purposePieces) {
                    if (!assembledPurpose.isEmpty()) {
                        assembledPurpose += DelegatedAccessConstants.TOKEN_DIVIDER;
                    }
                    assembledPurpose += purposePiece;
                    if (delegatedAccessConfig.get(assembledPurpose) instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> purposeControls = (Map<String, Object>) delegatedAccessConfig.get(assembledPurpose);

                        // TODO handle anyOf / allOf nested values
                        controls = ConfigurationMerger.mergeConfigurations(controls, purposeControls);
                    }
                }
            }
            return controls;
        });
    }
}
