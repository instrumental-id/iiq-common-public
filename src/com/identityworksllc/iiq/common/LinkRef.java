package com.identityworksllc.iiq.common;

import sailpoint.api.SailPointContext;
import sailpoint.object.Link;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implements a type-safe Link reference object, containing the application
 * name and native identity of the Link.
 */
public final class LinkRef extends Pair<String, String> {

    /**
     * Constructs a LinkRef object with the specified application name and native identity.
     * @param applicationName the name of the application
     * @param nativeIdentity the native identity of the link
     */
    public LinkRef(String applicationName, String nativeIdentity) {
        super(applicationName, nativeIdentity);
    }

    /**
     * Constructs a LinkRef object from a map containing application name and native identity.
     * @param map a map containing the application name and native identity
     */
    public LinkRef(Map<String, ?> map) {
        this((String) map.get("applicationName"), (String) map.get("nativeIdentity"));
    }

    /**
     * Constructs a LinkRef object from a {@link Link} object.
     * @param link the Link object
     */
    public LinkRef(Link link) {
        this(link.getApplicationName(), link.getNativeIdentity());
    }

    /**
     * Returns true if this LinkRef is equal to another object.
     * @param o the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LinkRef)) return false;
        LinkRef linkRef = (LinkRef) o;
        return getFirst().equals(linkRef.getFirst()) && getSecond().equals(linkRef.getSecond());
    }

    /**
     * Gets the application name.
     * @return the application name
     */
    public String getApplicationName() {
        return getFirst();
    }

    /**
     * Gets the native identity.
     * @return the native identity
     */
    public String getNativeIdentity() {
        return getSecond();
    }

    /**
     * Returns the hash code of this LinkRef.
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(getFirst(), getSecond());
    }

    /**
     * Resolves the link reference to a Link object.
     *
     * @param context the SailPointContext
     * @return the resolved Link object
     * @throws GeneralException if an error occurs during resolution
     */
    public Link resolve(SailPointContext context) throws GeneralException {
        return IdentityLinkUtil.getUniqueLink(context, getApplicationName(), getNativeIdentity());
    }

    /**
     * Converts the LinkRef object to a map representation.
     *
     * @return a map containing the application name and native identity
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("applicationName", getApplicationName());
        map.put("nativeIdentity", getNativeIdentity());
        return map;
    }

    /**
     * Converts the LinkRef object to a string representation.
     * @return a string representation of the LinkRef object
     */
    @Override
    public String toString() {
        return "LinkRef{" +
                "applicationName='" + getApplicationName() + '\'' +
                ", nativeIdentity='" + getNativeIdentity() + '\'' +
                '}';
    }
}
