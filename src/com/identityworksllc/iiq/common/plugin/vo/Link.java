package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An object implementing the HATEOAS REST standard's link concept
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Link {

    /**
     * The external URL linking this object to another object
     */
    private String href;

    /**
     * The type of relationship (e.g., self, parent, user)
     */
    private String rel;

    @JsonProperty
    public String getHref() {
        return href;
    }

    @JsonProperty
    public String getRel() {
        return rel;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public void setRel(String rel) {
        this.rel = rel;
    }

}
