package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import sailpoint.api.ObjectUtil;
import sailpoint.object.SailPointObject;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectSummary extends RestObject {

    private Map<String, Object> attributes;
    private String displayName;
    private String id;
    private String name;
    private String type;

    public ObjectSummary() {
        attributes = new HashMap<>();
    }

    public ObjectSummary(SailPointObject spo) {
        this();
        this.id = spo.getId();
        this.name = spo.getName();
        this.type = ObjectUtil.getTheRealClass(spo).getSimpleName();
        try {
            Method getDisplayName = spo.getClass().getMethod("getDisplayName");
            if (getDisplayName != null) {
                this.displayName = (String)getDisplayName.invoke(spo);
            }
        } catch(Exception e) {
            /* Do nothing, this might be fine */
        }
    }

    public void addAttribute(String name, String value) {
    	if (attributes == null) {
    		attributes = new HashMap<>();   		
    	}
    	attributes.put(name, value);
    }

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

}
