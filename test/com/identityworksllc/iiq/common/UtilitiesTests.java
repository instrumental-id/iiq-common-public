package com.identityworksllc.iiq.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for the {@link Utilities} class
 */
public class UtilitiesTests {

    @Test
    public void testGlobalSingleton() {
        String hi = Utilities.computeGlobalSingleton("hi.key", () -> new String("hi"));

        Assertions.assertEquals("hi", hi);

        // Should NOT compute this time
        String hi2 = Utilities.computeGlobalSingleton("hi.key", () -> new String("hi2"));

        Assertions.assertEquals("hi", hi2);

        Assertions.assertSame(hi, hi2);
    }

    @Test
    public void testSafeMapCast() {
        Map<Object, Object> values = new HashMap<>();
        values.put(new ArrayList<>(), "value");
        values.put("value", new ArrayList<>());

        Assertions.assertFalse(Utilities.mapConformsToType(values, String.class, Object.class));
        Assertions.assertNull(Utilities.safeMapCast(values, String.class, Object.class));
    }

    @Test
    public void testSafeUtils() {
        Assertions.assertNotNull(Utilities.safeStream((List<Object>) null));
        Assertions.assertNotNull(Utilities.safeTrim(null));

        Assertions.assertNull(Utilities.safeCast(new ArrayList<>(), String.class));

        Assertions.assertEquals(0, Utilities.safeSize((Object[]) null));
        Assertions.assertEquals(0, Utilities.safeSize((Collection<?>) null));
        Assertions.assertEquals(Long.MIN_VALUE, Utilities.safeDateTimestamp(null));
        Assertions.assertNull(Utilities.safeSubstring(null, 0, 0));

        Assertions.assertEquals("abc", Utilities.safeSubstring("abc", 0, 10));
        Assertions.assertEquals("abc", Utilities.safeSubstring("abc", -5, 10));
        Assertions.assertEquals("", Utilities.safeSubstring("abc", 5, 1));
    }
}
