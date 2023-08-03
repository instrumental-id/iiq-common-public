package com.identityworksllc.iiq.common.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A no-op implementation of the single-server service class, used
 * for testing and demonstrations.
 */
public class SmokeTestSingleServerService extends SingleServerService {
    private static final Log log = LogFactory.getLog(SmokeTestSingleServerService.class);

    /**
     * Gets the plugin name (empty for this one)
     * @return an empty string
     */
    @Override
    public String getPluginName() {
        return "";
    }

    @Override
    public void implementation(SailPointContext context) throws GeneralException {
        log.warn(executionCount.get() + ": Running the single-server smoke test service on host " + Util.getHostName());
    }

    @Override
    public int skipExecutionCount() {
        return 3;
    }
}
