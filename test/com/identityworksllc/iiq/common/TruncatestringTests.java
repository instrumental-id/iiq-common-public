package com.identityworksllc.iiq.common;

import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class TruncatestringTests {
    @Test
    public void testTruncateUTF8() throws UnsupportedEncodingException {
        String utf8String = "“Test”";

        System.out.println(utf8String.length());
        System.out.println(utf8String.getBytes(StandardCharsets.UTF_16).length);

        String truncated = Utilities.truncateStringToBytes(utf8String, 6, StandardCharsets.UTF_8);
        System.out.println(truncated);
        System.out.println(truncated.length());
    }
}
