package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import sailpoint.api.ObjectUtil;
import sailpoint.object.SailPointObject;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * A default example implementation of an object summary VO object
 */
@JsonAutoDetect
public class ObjectSummary extends RestObject {

    /**
     * Extra attributes for this object
     */
    private Map<String, Object> attributes;

    /**
     * The object's display name
     */
    private String displayName;

    /**
     * The object ID
     */
    private String id;

    /**
     * The object name
     */
    private String name;

    /**
     * The object type (e.g., a class or short name like Identity)
     */
    private String type;

    /**
     * Creates a new ObjectSummary with empty details
     */
    public ObjectSummary() {
        attributes = new HashMap<>();
    }

    /**
     * Constructs a new summary of the given SailPointObject
     * @param spo The SailPointObject to copy
     */
    public ObjectSummary(SailPointObject spo) {
        this();
        this.id = spo.getId();
        this.name = spo.getName();
        this.type = ObjectUtil.getTheRealClass(spo).getSimpleName();
        try {
            Method getDisplayName = spo.getClass().getMethod("getDisplayName");
            this.displayName = (String) getDisplayName.invoke(spo);
        } catch(Exception ignored) {
            /* Do nothing, this might be fine */
        }
    }

    /**
     * Adds a new attribute to this object summary
     * @param name The name of the attribute
     * @param value The string value of the attribute
     */
    public void addAttribute(String name, String value) {
    	if (attributes == null) {
    		attributes = new HashMap<>();   		
    	}
    	attributes.put(name, value);
    }

    /**
     * Adds a new attribute to this object summary
     * @param name The name of the attribute
     * @param value The list value of the attribute
     */
    public void addAttribute(String name, List<?> value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(name, value);
    }

    @JsonProperty
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @JsonProperty
    public String getDisplayName() {
        return displayName;
    }

    @JsonProperty
    public String getId() {
        return id;
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    @JsonProperty
    public String getType() {
        return type;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", ObjectSummary.class.getSimpleName() + "[", "]");
        if ((attributes) != null) {
            joiner.add("attributes=" + attributes);
        }
        if ((displayName) != null) {
            joiner.add("displayName='" + displayName + "'");
        }
        if ((id) != null) {
            joiner.add("id='" + id + "'");
        }
        if ((name) != null) {
            joiner.add("name='" + name + "'");
        }
        if ((type) != null) {
            joiner.add("type='" + type + "'");
        }
        return joiner.toString();
    }
}
