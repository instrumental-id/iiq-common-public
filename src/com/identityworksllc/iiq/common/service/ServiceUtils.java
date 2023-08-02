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
        Attributes<String, Object> attributes = target.getAttributes();
        if (attributes == null) {
            attributes = new Attributes<>();
            target.setAttributes(attributes);
        }
        attributes.put("lastStart", lastStart);
        attributes.put("lastStop", System.currentTimeMillis());
        attributes.put("lastHost", Util.getHostName());
        context.saveObject(target);
        context.commitTransaction();
    }
}
