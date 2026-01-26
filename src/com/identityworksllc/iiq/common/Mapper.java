package com.identityworksllc.iiq.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.transformer.IdentityTransformer;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements conversion utilities to extract a Map equivalent to a number of
 * common IIQ objects. These can be passed as arguments to a Rule, for example,
 * or used via MapMatcher.
 */
public class Mapper {

    /**
     * Returns the default ObjectMapper instance.
     * TOOD caching and cloning of the ObjectMapper?
     * @return The ObjectMapper instance
     */
    private static ObjectMapper getMapper() {
        return new ObjectMapper();
    }

    /**
     * A static version of the utility implemented via {@link Mappable}
     * @param anything Whatever object is needed
     * @return The object converted to a Map
     * @throws GeneralException if anything fails
     */
    public static Map<String, Object> objectToMap(Object anything) throws GeneralException {
        if (anything == null) {
            return new HashMap<>();
        }

        ObjectMapper mapper = getMapper();
        mapper.addMixIn(anything.getClass(), Mappable.FilterMixin.class);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            String json = mapper.writeValueAsString(anything);
            MapType javaType = mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
            return mapper.readValue(json, javaType);
        } catch(JsonProcessingException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Transforms a Map with path-type keys (e.g., 'link.attributes[a]') to a
     * model map that has nested keys and values.
     *
     * @param input The input map
     * @return The output map
     * @throws GeneralException if any failures occur
     */
    public static Map<String, Object> pathMapToModelMap(Map<String, Object> input) throws GeneralException {
        Map<String, Object> output = new HashMap<>();

        if (input != null) {
            for (String key : input.keySet()) {
                MapUtil.put(output, key, input.get(key));
            }
        }

        return output;
    }

    /**
     * Transforms the given SailPointObject into a Map. If the object is one of
     * the other types handled by this class, forwards to that method. Otherwise,
     * returns the attributes.
     *
     * @param context The context, potentially used to load attributes
     * @param spo The input object
     * @return The resulting Map-ified object
     * @throws GeneralException if any failures occur
     */
    public static Map<String, Object> toMap(SailPointContext context, Object spo) throws GeneralException {
        Map<String, Object> result = new HashMap<>();
        if (spo instanceof Link) {
            result.putAll(toMap((Link)spo));
        } else if (spo instanceof Identity) {
            result.putAll(toMap(context, (Identity)spo));
        } else if (spo instanceof Application) {
            result.putAll(toMap((Application)spo));
        } else if (spo instanceof ProvisioningPlan) {
            result.putAll(toMap((ProvisioningPlan)spo));
        } else if (spo instanceof ProvisioningPlan.AbstractRequest) {
            result.putAll(toMap((ProvisioningPlan.AbstractRequest)spo));
        } else if (spo instanceof ManagedAttribute) {
            result.putAll(toMap((ManagedAttribute)spo));
        } else {
            Attributes<String, Object> attrs = Utilities.getAttributes(spo);
            if (attrs != null) {
                result.putAll(attrs);
            }
        }
        return result;
    }

    public static Map<String, Object> toMap(AttributeAssignment aa) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", aa.getName());
        map.put("application.id", aa.getApplicationId());
        map.put("application.name", aa.getApplicationName());
        map.put("nativeIdentity", aa.getNativeIdentity());
        map.put("type", Utilities.safeString(aa.getType()));
        map.put("value", aa.getStringValue());
        return map;
    }

    public static Map<String, Object> toMap(Application application) {
        Map<String, Object> map = new HashMap<>();
        if (application.getAttributes() != null) {
            map.putAll(application.getAttributes());
        }
        map.put("schemas", application.getSchemas());
        map.put("accountSchema", application.getAccountSchema());
        map.put("name", application.getName());
        map.put("id", application.getId());
        map.put("connector", application.getConnector());
        map.put("features", application.getFeaturesString());
        map.put("beforeProvisioningRule", application.getBeforeProvisioningRule());
        map.put("afterProvisioningRule", application.getAfterProvisioningRule());
        map.put("formPath", application.getFormPath());
        if (application.getProxy() != null) {
            map.put("proxy", toMap(application.getProxy()));
        }
        return map;
    }

    public static Map<String, Object> toMap(ProvisioningPlan.AbstractRequest request) {
        return request.toMap();
    }

    public static Map<String, Object> toMap(ProvisioningPlan plan) {
        return plan.toMap();
    }

    public static Map<String, Object> toMap(SailPointContext context, Identity identity) throws GeneralException {
        IdentityTransformer transformer = new IdentityTransformer(context);
        transformer.setLinkExpand(true);
        return transformer.toMap(identity);
    }

    public static Map<String, Object> toMap(RoleAssignment ra) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", ra.getRoleName());
        map.put("source", ra.getSource());
        map.put("comments", ra.getComments());
        map.put("assigner", ra.getAssigner());
        map.put("assignmentId", ra.getAssignmentId());
        map.put("target.application.id", Utilities.safeStream(ra.getTargets()).map(RoleTarget::getApplicationId).collect(Collectors.toList()));
        map.put("target.application.name", Utilities.safeStream(ra.getTargets()).map(RoleTarget::getApplicationName).collect(Collectors.toList()));
        map.put("target.nativeIdentity", Utilities.safeStream(ra.getTargets()).map(RoleTarget::getNativeIdentity).collect(Collectors.toList()));
        map.put("target.displayName", Utilities.safeStream(ra.getTargets()).map(RoleTarget::getDisplayName).collect(Collectors.toList()));
        return map;
    }

    public static Map<String, Object> toMap(RoleDetection rd) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", rd.getRoleName());
        map.put("assignmentIds", rd.getAssignmentIdList());
        map.put("target.application.id", Utilities.safeStream(rd.getTargets()).map(RoleTarget::getApplicationId).collect(Collectors.toList()));
        map.put("target.application.name", Utilities.safeStream(rd.getTargets()).map(RoleTarget::getApplicationName).collect(Collectors.toList()));
        map.put("target.nativeIdentity", Utilities.safeStream(rd.getTargets()).map(RoleTarget::getNativeIdentity).collect(Collectors.toList()));
        map.put("target.displayName", Utilities.safeStream(rd.getTargets()).map(RoleTarget::getDisplayName).collect(Collectors.toList()));
        return map;
    }

    public static Map<String, Object> toMap(Link owner, ManagedAttribute ma) {
        Map<String, Object> map = new HashMap<>();
        if (ma.getAttributes() != null) {
            map.putAll(ma.getAttributes());
        }
        if (owner.getAttributes() != null) {
            Utilities.safeStream(owner.getAttributes().getKeys()).forEach(k -> {
                map.put("link." + k, owner.getAttributes().get(k));
            });
        }
        map.put("link.instance", owner.getInstance());
        map.put("link.nativeIdentity", owner.getNativeIdentity());
        map.put("value", ma.getValue());
        map.put("displayName", ma.getDisplayName());
        map.put("application.name", ma.getApplication().getName());
        map.put("application.id", ma.getApplicationId());
        map.put("attribute", ma.getAttribute());
        return map;
    }


    public static Map<String, Object> toMap(ManagedAttribute ma) {
        Map<String, Object> map = new HashMap<>();
        if (ma.getAttributes() != null) {
            map.putAll(ma.getAttributes());
        }
        map.put("value", ma.getValue());
        map.put("displayName", ma.getDisplayName());
        map.put("application.name", ma.getApplication().getName());
        map.put("application.id", ma.getApplicationId());
        map.put("attribute", ma.getAttribute());
        return map;
    }

    public static Map<String, Object> toMap(Link link) {
        Map<String, Object> map = new HashMap<>();
        if (link.getAttributes() != null) {
            map.putAll(link.getAttributes());
        }
        map.put("nativeIdentity", link.getNativeIdentity());
        map.put("displayName", link.getDisplayableName());
        map.put("instance", link.getInstance());
        map.put("identity.name", link.getIdentity().getName());
        map.put("identity.id", link.getIdentity().getId());
        map.put("application.name", link.getApplicationName());
        map.put("application.id", link.getApplicationId());
        return map;
    }

}
