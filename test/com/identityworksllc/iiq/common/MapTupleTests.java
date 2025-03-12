package com.identityworksllc.iiq.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MapTupleTests {
    @Test
    public void constructorThrowsExceptionForDuplicateKeys() {
        assertThrows(IllegalArgumentException.class,
                () ->
                MapTupleBuilder.withKeys("key1", "key1"));
    }

    @Test
    public void constructorThrowsExceptionForEmptyKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> MapTupleBuilder.withKeys());
    }

    @Test
    public void constructorThrowsExceptionForNullKeyInList() {
        assertThrows(NullPointerException.class,
                () -> MapTupleBuilder.withKeys(Arrays.asList("key1", null, "key2")));
    }

    @Test
    public void constructorThrowsExceptionForNullKeys() {
        assertThrows(NullPointerException.class,
                () -> MapTupleBuilder.withKeys((String[]) null));
    }

    @Test
    public void getAtReturnsCorrectValueByIndex() {
        List<String> keys = Arrays.asList("first", "second", "third");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("first", "value1");
        mapTuple.put("second", "value2");
        mapTuple.put("third", "value3");
        assertEquals("value1", mapTuple.getAt(0));
        assertEquals("value2", mapTuple.getAt(1));
        assertEquals("value3", mapTuple.getAt(2));
    }

    @Test
    public void getAtThrowsForInvalidIndex() {
        List<String> keys = Arrays.asList("first", "second", "third");
        MapTuple mapTuple = new MapTuple(keys);
        assertThrows(IndexOutOfBoundsException.class, () -> mapTuple.getAt(3));
    }

    @Test
    public void getAtWithClassCastsCorrectly() {
        List<String> keys = Arrays.asList("first", "second");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("first", 123);
        mapTuple.put("second", "value");
        assertEquals(123, mapTuple.getAt(0, Integer.class));
        assertEquals("value", mapTuple.getAt(1, String.class));
    }

    @Test
    public void getFifthReturnsCorrectValue() {
        List<String> keys = Arrays.asList("first", "second", "third", "fourth", "fifth");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("fifth", "value5");
        assertEquals("value5", mapTuple.getFifth());
    }

    @Test
    public void getFirstReturnsCorrectValue() {
        List<String> keys = Arrays.asList("first", "second");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("first", "value1");
        assertEquals("value1", mapTuple.getFirst());
    }

    @Test
    public void getFourthReturnsCorrectValue() {
        List<String> keys = Arrays.asList("first", "second", "third", "fourth");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("fourth", "value4");
        assertEquals("value4", mapTuple.getFourth());
    }

    @Test
    public void getReturnsCorrectValueByKey() {
        List<String> keys = Arrays.asList("first", "second", "third");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("first", "value1");
        mapTuple.put("second", "value2");
        mapTuple.put("third", "value3");
        assertEquals("value1", mapTuple.get("first"));
        assertEquals("value2", mapTuple.get("second"));
        assertEquals("value3", mapTuple.get("third"));
    }

    @Test
    public void getSecondReturnsCorrectValue() {
        List<String> keys = Arrays.asList("first", "second");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("second", "value2");
        assertEquals("value2", mapTuple.getSecond());
    }

    @Test
    public void getSixthReturnsCorrectValue() {
        List<String> keys = Arrays.asList("first", "second", "third", "fourth", "fifth", "sixth");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("sixth", "value6");
        assertEquals("value6", mapTuple.getSixth());
    }

    @Test
    public void getThirdReturnsCorrectValue() {
        List<String> keys = Arrays.asList("first", "second", "third");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("third", "value3");
        assertEquals("value3", mapTuple.getThird());
    }

    @Test
    public void getWithClassCastsCorrectly() {
        List<String> keys = Arrays.asList("first", "second");
        MapTuple mapTuple = new MapTuple(keys);
        mapTuple.put("first", 123);
        mapTuple.put("second", "value");
        assertEquals(123, mapTuple.get("first", Integer.class));
        assertEquals("value", mapTuple.get("second", String.class));
    }

    @Test
    public void ofCreatesMapTupleWithCorrectValues() {
        MapTupleBuilder builder = MapTupleBuilder.withKeys("key1", "key2");
        MapTuple tuple = builder.of("value1", "value2");
        assertEquals("value1", tuple.get("key1"));
        assertEquals("value2", tuple.get("key2"));
    }

    @Test
    public void ofListCreatesMapTupleWithCorrectValues() {
        MapTupleBuilder builder = MapTupleBuilder.withKeys("key1", "key2");
        MapTuple tuple = builder.ofList(Arrays.asList("value1", "value2"));
        assertEquals("value1", tuple.get("key1"));
        assertEquals("value2", tuple.get("key2"));
    }

    @Test
    public void ofListThrowsExceptionForMismatchedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    MapTupleBuilder builder = MapTupleBuilder.withKeys("key1", "key2");
                    builder.ofList(Collections.singletonList("value1"));
                });
    }

    @Test
    public void ofThrowsExceptionForMismatchedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    MapTupleBuilder builder = MapTupleBuilder.withKeys("key1", "key2");
                    builder.of("value1");
                });
    }
}
