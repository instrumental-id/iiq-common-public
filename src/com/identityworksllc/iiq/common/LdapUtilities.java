package com.identityworksllc.iiq.common;

import sailpoint.tools.Util;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import java.util.List;

/**
 * Utilities for dealing with LDAP DNs and other similar concepts
 */
public class LdapUtilities {
    /**
     * Extracts the name from an LDAP formatted group name. For example, if given CN=AD Group Name,OU=Groups,OU=Security,DC=client,DC=example,DC=com, this method would return "AD Group Name".
     *
     * @param groupDN The group DN
     * @return the group name
     * @throws InvalidNameException if this is not an LDAP name
     */
    public static String ldapCleanGroupName(String groupDN) throws InvalidNameException {
        String groupName = groupDN;
        // Handle goofy OIM format if needed
        if (groupName.contains("~")) {
            groupName = groupName.substring(groupName.indexOf("~") + 1);
        }
        LdapName parser = new LdapName(groupName);
        String firstElem = parser.get(parser.size() - 1);
        if (firstElem.length() > 0 && firstElem.contains("=")) {
            return firstElem.substring(firstElem.indexOf("=") + 1);
        }
        throw new IllegalArgumentException("Group name " + groupName + " does not appear to be in LDAP format");
    }

    /**
     * Returns true if the given list of DNs contains a matching DN by RDN.
     * This is useful for searching a list of AD groups (e.g., user entitlements)
     * for a given value, without having to worry about differing domain suffixes
     * across dev, test, and prod.
     *
     * Equivalent to {@link #ldapContains(List, String, int)} with a depth of 1.
     *
     * @param container A list of candidate DNs
     * @param seeking The DN (whole or partial) to match
     * @return True if the list contains a matching DN, false if not
     */
    public static boolean ldapContains(List<String> container, String seeking) {
        return ldapContains(container, seeking, 1);
    }

    /**
     * Returns true if the given list of DNs contains a value matching the given
     * 'seeking' DN, up to the given depth.
     *
     * @param container A list of candidate DNs
     * @param seeking The DN (whole or partial) to match
     * @param depth The depth of search
     * @return True if the list contains a matching DN, false if not
     */
    public static boolean ldapContains(List<String> container, String seeking, int depth) {
        for(String dn : Util.safeIterable(container)) {
            if (ldapMatches(dn, seeking, depth)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given a list of possible matching DNs (the container), finds the first
     * one that matches the RDN of the 'seeking' string.
     *
     * @param container A list of candidate DNs
     * @param seeking The DN we are seeking to match
     * @return The DN matching the search, or null if none is found
     */
    public static String ldapGetMatch(List<String> container, String seeking) {
        return ldapGetMatch(container, seeking, 1);
    }

    /**
     * Given a list of possible matching DNs (the container), finds the first
     * one that matches the 'seeking' string up to the given depth.
     *
     * @param container A list of candidate DNs
     * @param seeking The DN we are seeking
     * @param depth The number of RDN components to match
     * @return The DN matching the search, or null if none is found
     */
    public static String ldapGetMatch(List<String> container, String seeking, int depth) {
        for(String dn : Util.safeIterable(container)) {
            if (ldapMatches(dn, seeking, depth)) {
                return dn;
            }
        }
        return null;
    }

    /**
     * Extracts the first N RDNs from an LDAP formatted DN. For example,
     * if given CN=AD Group Name,OU=Groups,OU=Security,DC=client,DC=example,DC=com,
     * and a size of 1, this method would return "CN=AD Group Name". A size of 2
     * would produce "CN=AD Group Name,OU=Groups".
     *
     * @param dn The object's distinguishedName
     * @param size The number of RDN elements to return
     * @return the first 'size' RDNs of the DN
     * @throws InvalidNameException if this is not an LDAP name
     */
    public static String ldapGetRdn(String dn, int size) throws InvalidNameException {
        LdapName parser = new LdapName(dn);
        return ldapGetRdn(parser, size);
    }

    /**
     * Extracts the first N RDNs from an LDAP formatted DN. For example,
     * if given CN=AD Group Name,OU=Groups,OU=Security,DC=client,DC=example,DC=com,
     * and a size of 1, this method would return "CN=AD Group Name". A size of 2
     * would produce "CN=AD Group Name,OU=Groups".
     *
     * @param name The already-parsed LdapName object
     * @param size The number of RDN elements to return
     * @return the first 'size' RDNs of the DN
     * @throws InvalidNameException if this is not an LDAP name
     */
    public static String ldapGetRdn(LdapName name, int size) throws InvalidNameException {
        StringBuilder builder = new StringBuilder();
        for(int i = 1; i <= size && i <= name.size(); i++) {
            if (builder.length() > 0) {
                builder.append(",");
            }

            // LDAP names are sorted like a file path, with the RDN last
            builder.append(name.get(name.size() - i));
        }
        return builder.toString();
    }

    /**
     * Returns true if the first element of the given LDAP name matches the value provided.
     *
     * Equivalent to ldapMatches(name, otherName, 1).
     *
     * @param name The LDAP name to check
     * @param otherName The other name to compare against
     * @return True if the names are LDAP DNs and equal ignoring case, otherwise false
     */
    public static boolean ldapMatches(String name, String otherName) {
        return ldapMatches(name, otherName, 1);
    }

    /**
     * Returns true if the given objects match by comparing them as LDAP DNs up
     * to the depth specified. For example, the following two DNs will match at
     * a depth of 1, but not a depth of 2.
     *
     * CN=Group Name,OU=Groups,DC=test,DC=example,DC=com
     * cn=group name,OU=Administrators,DC=test,DC=example,DC=com
     *
     * This is primarily useful with AD environments where the group names will
     * have a suffix varying by domain.
     *
     * @param name The first LDAP name
     * @param otherName The second LDAP name
     * @param depth The number of DN elements to search for a match (with 1 being the RDN only)
     * @return True if the names are indeed LDAP DNs and equal ignoring case, false otherwise
     */
    public static boolean ldapMatches(String name, String otherName, int depth) {
        if (Util.nullSafeCaseInsensitiveEq(name, otherName)) {
            return true;
        }
        try {
            return Util.nullSafeCaseInsensitiveEq(ldapGetRdn(name, depth), ldapGetRdn(otherName, depth));
        } catch(Exception e) {
            return false;
        }
    }

    /**
     * Private utility constructor
     */
    private LdapUtilities() {

    }
}
