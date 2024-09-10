package com.identityworksllc.iiq.common.service;

import com.identityworksllc.iiq.common.plugin.CommonPluginUtils;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.ServiceDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Utilities for services
 */
public class ServiceUtils {
    /**
     * Stores the last start and stop timestamps on the given ServiceDefinition. This is
     * most useful with a single-server service, implemented using either {@link com.identityworksllc.iiq.common.plugin.SingleServerService}
     * or {@link com.identityworksllc.iiq.common.plugin.CommonPluginUtils#singleServerExecute(SailPointContext, ServiceDefinition, CommonPluginUtils.SingleServerExecute)}.
     *
     * The time of this method's invocation will be used as the 'last stop' timestamp.
     *
     * @param context The context of this service
     * @param target The target of this service
     * @param lastStart The last start timestamp of this service
     * @throws GeneralException when something goes wrong persisting the ServiceDefinition change
     */
    public static void storeTimestamps(SailPointContext context, ServiceDefinition target, long lastStart) throws GeneralException {
        ServiceDefinition reloaded = context.getObjectById(ServiceDefinition.class, target.getId());
        Attributes<String, Object> attributes = reloaded.getAttributes();
        if (attributes == null) {
            attributes = new Attributes<>();
            reloaded.setAttributes(attributes);
        }
        attributes.put("lastStart", lastStart);
        attributes.put("lastStop", System.currentTimeMillis());
        attributes.put("lastHost", Util.getHostName());
        context.saveObject(reloaded);
        context.commitTransaction();
    }

    /**
     * Private utility constructor
     */
    private ServiceUtils() {

    }
}
