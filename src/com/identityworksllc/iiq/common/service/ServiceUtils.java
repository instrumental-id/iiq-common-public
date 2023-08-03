package com.identityworksllc.iiq.common.service;

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
     * Utilities for services
     * @param context The context of this service
     * @param target The target of this service
     * @param lastStart The last start timestamp of this service
     * @throws GeneralException
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
}
