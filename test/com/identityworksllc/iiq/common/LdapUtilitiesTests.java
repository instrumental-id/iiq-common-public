package com.identityworksllc.iiq.common;

import org.junit.jupiter.api.Test;

import javax.naming.InvalidNameException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LdapUtilitiesTests {
    @Test
    public void ldapCleanGroupNameExtractsCorrectName() throws InvalidNameException {
        String groupDN = "CN=AD Group Name,OU=Groups,OU=Security,DC=client,DC=example,DC=com";
        assertEquals("AD Group Name", LdapUtilities.ldapCleanGroupName(groupDN));
    }

    @Test
    public void ldapCleanGroupNameHandlesOIMFormat() throws InvalidNameException {
        String groupDN = "OIM~CN=AD Group Name,OU=Groups,OU=Security,DC=client,DC=example,DC=com";
        assertEquals("AD Group Name", LdapUtilities.ldapCleanGroupName(groupDN));
    }

    @Test
    public void ldapCleanGroupNameThrowsExceptionForInvalidFormat() throws InvalidNameException {
        String groupDN = "InvalidGroupName";
        assertThrows(InvalidNameException.class,
                () -> LdapUtilities.ldapCleanGroupName(groupDN));
    }

    @Test
    public void ldapContainsReturnsTrueForMatchingDN() {
        List<String> container = Arrays.asList("CN=Group1,OU=Groups,DC=example,DC=com", "CN=Group2,OU=Groups,DC=example,DC=com");
        assertTrue(LdapUtilities.ldapContains(container, "CN=Group1"));
    }

    @Test
    public void ldapContainsReturnsFalseForNonMatchingDN() {
        List<String> container = Arrays.asList("CN=Group1,OU=Groups,DC=example,DC=com", "CN=Group2,OU=Groups,DC=example,DC=com");
        assertFalse(LdapUtilities.ldapContains(container, "CN=Group3"));
    }

    @Test
    public void ldapGetMatchReturnsCorrectDN() {
        List<String> container = Arrays.asList("CN=Group1,OU=Groups,DC=example,DC=com", "CN=Group2,OU=Groups,DC=example,DC=com");
        assertEquals("CN=Group1,OU=Groups,DC=example,DC=com", LdapUtilities.ldapGetMatch(container, "CN=Group1"));
    }

    @Test
    public void ldapGetMatchReturnsNullForNoMatch() {
        List<String> container = Arrays.asList("CN=Group1,OU=Groups,DC=example,DC=com", "CN=Group2,OU=Groups,DC=example,DC=com");
        assertNull(LdapUtilities.ldapGetMatch(container, "CN=Group3"));
    }

    @Test
    public void ldapGetRdnExtractsCorrectRdn() throws InvalidNameException {
        String dn = "CN=AD Group Name,OU=Groups,OU=Security,DC=client,DC=example,DC=com";
        assertEquals("CN=AD Group Name", LdapUtilities.ldapGetRdn(dn, 1));
    }

    @Test
    public void ldapGetRdnExtractsMultipleRdns() throws InvalidNameException {
        String dn = "CN=AD Group Name,OU=Groups,OU=Security,DC=client,DC=example,DC=com";
        assertEquals("CN=AD Group Name,OU=Groups", LdapUtilities.ldapGetRdn(dn, 2));
    }

    @Test
    public void ldapMatchesReturnsTrueForMatchingRdn() {
        String dn1 = "CN=Group1,OU=Groups,DC=example,DC=com";
        String dn2 = "CN=Group1,OU=OtherGroups,DC=example,DC=com";
        assertTrue(LdapUtilities.ldapMatches(dn1, dn2, 1));
    }

    @Test
    public void ldapMatchesReturnsFalseForNonMatchingRdn() {
        String dn1 = "CN=Group1,OU=Groups,DC=example,DC=com";
        String dn2 = "CN=Group2,OU=Groups,DC=example,DC=com";
        assertFalse(LdapUtilities.ldapMatches(dn1, dn2, 1));
    }
}
