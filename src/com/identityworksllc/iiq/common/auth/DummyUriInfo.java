package com.identityworksllc.iiq.common.auth;

import org.glassfish.jersey.uri.internal.JerseyUriBuilder;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * A dummy UriInfo implementation so that the BaseListResource that all plugin
 * resources depend on doesn't crash. This class obviously is not expected to do
 * anything other than simply exist.
 */
public class DummyUriInfo implements UriInfo {
    @Override
    public URI getAbsolutePath() {
        return null;
    }

    @Override
    public UriBuilder getAbsolutePathBuilder() {
        return new JerseyUriBuilder();
    }

    @Override
    public URI getBaseUri() {
        return null;
    }

    @Override
    public UriBuilder getBaseUriBuilder() {
        return new JerseyUriBuilder();
    }

    @Override
    public List<Object> getMatchedResources() {
        return null;
    }

    @Override
    public List<String> getMatchedURIs() {
        return null;
    }

    @Override
    public List<String> getMatchedURIs(boolean decode) {
        return null;
    }

    @Override
    public String getPath() {
        return null;
    }

    @Override
    public String getPath(boolean decode) {
        return null;
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters() {
        return new MultivaluedHashMap<>();
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters(boolean decode) {
        return new MultivaluedHashMap<>();
    }

    @Override
    public List<PathSegment> getPathSegments() {
        return new ArrayList<>();
    }

    @Override
    public List<PathSegment> getPathSegments(boolean decode) {
        return new ArrayList<>();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() {
        return new MultivaluedHashMap<>();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
        return new MultivaluedHashMap<>();
    }

    @Override
    public URI getRequestUri() {
        return null;
    }

    @Override
    public UriBuilder getRequestUriBuilder() {
        return null;
    }

    @Override
    public URI relativize(URI uri) {
        return null;
    }

    @Override
    public URI resolve(URI uri) {
        return null;
    }
}
