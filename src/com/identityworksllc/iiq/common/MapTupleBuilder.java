package com.identityworksllc.iiq.common;

import java.util.*;

/**
 * A builder for MapTuple objects. These are objects that function as
 * tuples, allowing access by either index or by key. To use this class,
 * construct a new instance at the top of your operation, such as:
 *
 * MapTupleBuilder mtb = new MapTupleBuilder("identity", "accountName", "date");
 *
 * Then, for each item, such as from a ResultSet, do this:
 *
 * MapTuple tuple = mtb.of(resultSet.getString("identity"), resultSet.getString("name"), resultSet.getDate("created"));
 *
 * In this example, the value for 'identity' can be accessed any of these ways:
 *
 * tuple.get("identity");
 * tuple.getAt(0);
 * tuple.getFirst();
 *
 */
public class MapTupleBuilder {

    /**
     * Gets the list of keys
     */
    private final List<String> keys;

    /**
     * Constructs a new MapTupleBuilder, with the given list of keys.
     * This constructor is private. One of the withKeys() methods should
     * be used.
     *
     * Keys cannot be null and cannot be duplicated.
     *
     * @param keysList The list of keys
     */
    private MapTupleBuilder(List<String> keysList) {
        for(String k : Objects.requireNonNull(keysList)) {
            Objects.requireNonNull(k, "Key in keys list cannot be null");
        }

        Set<String> duplicatesRemoved = new HashSet<>(keysList);
        if (duplicatesRemoved.size() != keysList.size()) {
            throw new IllegalArgumentException("Duplicate keys are not allowed in MapTuple");
        }

        this.keys = Collections.unmodifiableList(keysList);
    }

    /**
     * Constructs a new MapTupleBuilder with the keys in the list.
     * @param keys At least one key to use in the resulting tuples
     * @return A builder with the given keys
     */
    public static MapTupleBuilder withKeys(String... keys) {
        if (keys.length == 0) {
            throw new IllegalArgumentException("You must specify at least one key");
        }
        List<String> keysList = new ArrayList<>(Arrays.asList(keys));
        return new MapTupleBuilder(keysList);
    }

    /**
     * Constructs a new MapTupleBuilder with the keys in the list.
     * @param keys A list containing at least one non-null key to use the resulting tuples
     * @return A builder with the given keys
     */
    public static MapTupleBuilder withKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("You must specify at least one key");
        }
        return new MapTupleBuilder(keys);
    }

    /**
     * Constructs a {@link MapTuple} pairing this builder's keys with the given
     * values, both in order. The number of values must match the number of keys.
     *
     * @param values The values to add
     * @return The resulting map
     */
    public MapTuple of(Object... values) {
        return ofList(Arrays.asList(values));
    }

    /**
     * Constructs a {@link MapTuple} pairing this builder's keys with the given
     * values, both in order. The number of values must match the number of keys.
     *
     * @param values The values to add
     * @return The resulting map
     */
    public MapTuple ofList(List<Object> values) {
        if (values.size() != keys.size()) {
            throw new IllegalArgumentException("MapTuple argument mismatch: got " + values.size() + " values, expected " + keys.size());
        }
        MapTuple result = new MapTuple(this.keys);
        for(int i = 0; i < values.size(); i++) {
            result.put(keys.get(i), values.get(i));
        }
        return result;
    }

}
