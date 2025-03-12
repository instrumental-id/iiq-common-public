package com.identityworksllc.iiq.common;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SamenessTests {
    @Test
    public void isEmptyWithNull() {
        assertTrue(Sameness.isEmpty(null));
    }

    @Test
    public void isEmptyWithEmptyString() {
        assertTrue(Sameness.isEmpty(""));
    }

    @Test
    public void isEmptyWithNonEmptyString() {
        assertFalse(Sameness.isEmpty("non-empty"));
    }

    @Test
    public void isEmptyWithZeroNumber() {
        assertTrue(Sameness.isEmpty(0));
    }

    @Test
    public void isEmptyWithNonZeroNumber() {
        assertFalse(Sameness.isEmpty(1));
    }

    @Test
    public void isEmptyWithEmptyCollection() {
        assertTrue(Sameness.isEmpty(Collections.emptyList()));
    }

    @Test
    public void isEmptyWithNonEmptyCollection() {
        assertFalse(Sameness.isEmpty(Collections.singletonList("item")));
    }

    @Test
    public void isEmptyWithEmptyMap() {
        assertTrue(Sameness.isEmpty(Collections.emptyMap()));
    }

    @Test
    public void isEmptyWithNonEmptyMap() {
        assertFalse(Sameness.isEmpty(Collections.singletonMap("key", "value")));
    }

    @Test
    public void isSameWithNullValues() {
        assertTrue(Sameness.isSame(null, null, false));
    }

    @Test
    public void isSameWithEmptyStringAndNull() {
        assertTrue(Sameness.isSame("", null, false));
    }

    @Test
    public void isSameWithBooleanValues() {
        assertTrue(Sameness.isSame(true, true, false));
        assertFalse(Sameness.isSame(true, false, false));
    }

    @Test
    public void isSameWithStringValues() {
        assertTrue(Sameness.isSame("test", "test", false));
        assertFalse(Sameness.isSame("test", "Test", false));
        assertTrue(Sameness.isSame("test", "Test", true));
    }

    @Test
    public void isSameWithDateAndLong() {
        Date date = new Date(1000L);
        assertTrue(Sameness.isSame(date, 1000L, false));
        assertTrue(Sameness.isSame(1000L, date, false));
    }

    @Test
    public void isSameWithCollections() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("c", "b", "a");
        assertTrue(Sameness.isSame(list1, list2, false));
    }

    @Test
    public void isSameWithCollectionsIgnoreCase() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("C", "B", "A");
        assertTrue(Sameness.isSame(list1, list2, true));
    }

    @Test
    public void isSameWithStringAndCollection() {
        List<String> list = Collections.singletonList("test");
        assertTrue(Sameness.isSame("test", list, false));
        assertTrue(Sameness.isSame(list, "test", false));
    }

    @Test
    public void isSameWithStringAndCollectionIgnoreCase() {
        List<String> list = Collections.singletonList("Test");
        assertTrue(Sameness.isSame("test", list, true));
        assertTrue(Sameness.isSame(list, "test", true));
    }
}
