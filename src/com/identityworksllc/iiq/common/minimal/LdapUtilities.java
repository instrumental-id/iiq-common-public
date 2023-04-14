package com.identityworksllc.iiq.common.minimal;

import sailpoint.tools.Util;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import java.util.List;

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

    public static boolean ldapContains(List<String> container, String seeking) {
        return ldapContains(container, seeking, 1);
    }

    public static boolean ldapContains(List<String> container, String seeking, int depth) {
        for(String dn : Util.safeIterable(container)) {
            if (ldapMatches(dn, seeking, depth)) {
                return true;
            }
        }
        return false;
    }

    public static String ldapGetMatch(List<String> container, String seeking) {
        return ldapGetMatch(container, seeking, 1);
    }

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
     * to the depth specified.
     *
     * @param name The first LDAP name
     * @param otherName The second LDAP name
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
}
