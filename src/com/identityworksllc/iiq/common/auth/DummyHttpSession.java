package com.identityworksllc.iiq.common.auth;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A dummy HttpSession implementation for use in mock HttpServletRequest instances.
 * This class provides a basic implementation of HttpSession that allows getting and
 * setting session attributes, which is necessary for IIQ's BaseResource to retrieve
 * the logged-in user from the session.
 */
public class DummyHttpSession implements HttpSession {
    /**
     * The session attributes, the main reason we have a dummy session at all
     */
    private final Map<String, Object> attributes;
    private final long creationTime;
    private final long lastAccessedTime;
    private final String sessionId;

    /**
     * Constructs a new DummyHttpSession with a generated session ID
     */
    public DummyHttpSession() {
        this.sessionId = "DUMMY_SESSION_" + System.nanoTime();
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = System.currentTimeMillis();
        this.attributes = new HashMap<>();
    }

    /**
     * Get a value from the session by name.
     */
    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * Get the underlying attribute map for convenient access to session state.
     *
     * @return The map of session attributes
     */
    public Map<String, Object> getAttributeMap() {
        return attributes;
    }

    /**
     * Get all attribute names in the session.
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    /**
     * Get the creation time of this session.
     */
    @Override
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Get the session ID.
     */
    @Override
    public String getId() {
        return sessionId;
    }

    /**
     * Get the last accessed time of this session.
     */
    @Override
    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    /**
     * Get the max inactive interval (not enforced in this dummy implementation).
     */
    @Override
    public int getMaxInactiveInterval() {
        return 0;
    }

    /**
     * Get the servlet context (not needed for this dummy implementation).
     */
    @Override
    public ServletContext getServletContext() {
        return null;
    }

    /**
     * Get the session context (not needed for this dummy implementation).
     */
    @Override
    @Deprecated
    public HttpSessionContext getSessionContext() {
        return null;
    }

    /**
     * Get a value by name from the session
     */
    @Override
    public Object getValue(String name) {
        return getAttribute(name);
    }

    /**
     * Get all attribute names
     */
    @Override
    public String[] getValueNames() {
        return attributes.keySet().toArray(new String[0]);
    }

    /**
     * Invalidate the session by clearing the attributes
     */
    @Override
    public void invalidate() {
        attributes.clear();
    }

    /**
     * Check if this session is new (not relevant for this dummy implementation).
     */
    @Override
    public boolean isNew() {
        return false;
    }

    /**
     * Put a value in the session (deprecated method, delegates to setAttribute).
     */
    @Override
    @Deprecated
    public void putValue(String name, Object value) {
        setAttribute(name, value);
    }

    /**
     * Remove a value from the session.
     */
    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    /**
     * Remove a value from the session (deprecated method, delegates to removeAttribute).
     */
    @Override
    public void removeValue(String name) {
        removeAttribute(name);
    }

    /**
     * Set a value in the session. This is the primary method used to store
     * session attributes like the logged-in user.
     */
    @Override
    public void setAttribute(String name, Object value) {
        if (name != null) {
            attributes.put(name, value);
        }
    }

    /**
     * Set the max inactive interval (not enforced in this dummy implementation).
     */
    @Override
    public void setMaxInactiveInterval(int interval) {
        // Not implemented
    }
}

