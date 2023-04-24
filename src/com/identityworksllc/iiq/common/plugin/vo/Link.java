package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Link {

    private String href;
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
