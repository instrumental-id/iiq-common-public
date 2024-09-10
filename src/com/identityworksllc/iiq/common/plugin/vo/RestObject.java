package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract REST object implementing the <a href="https://spring.io/understanding/HATEOAS">HATEOAS</a> standard.
 *
 * This is one of the output types allowed by default in {@link com.identityworksllc.iiq.common.plugin.BaseCommonPluginResource},
 * as extending this class indicates that you have considered its implications as an API response.
 */
public abstract class RestObject {
    /**
     * The list of external URL links to other resources
     */
    private List<Link> links;

    /**
     * Basic constructor to initialize the links list
     */
    public RestObject() {
        links = new ArrayList<>();
    }

    /**
     * Non-REST method to add a new Link to this object for return
     * @param rel The link type
     * @param href The link URL
     */
    public void addLink(String rel, String href) {
        Link l = new Link();
        l.setRel(rel);
        l.setHref(href);
        links.add(l);
    }

    /**
     * The list of links
     * @return The list of links
     */
    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Link> getLinks() {
        return links;
    }

    /**
     * Sets the list of links to the given one
     * @param links The list of links to add
     */
    public void setLinks(List<Link> links) {
        this.links = links;
    }
}
