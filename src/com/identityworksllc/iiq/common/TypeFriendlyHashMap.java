package com.identityworksllc.iiq.common;

import java.util.HashMap;
import java.util.Map;

/**
 * A minor extension to {@link HashMap} to include the {@link TypeFriendlyMap} methods
 * @param <K> The key type
 * @param <V> The value type
 */
public class TypeFriendlyHashMap<K, V> extends HashMap<K, V> implements TypeFriendlyMap<K, V> {
    /**
     * Constructs a new TypeFriendlyHashMap
     * @param initialCapacity The initial capacity of the map
     * @param loadFactor The load factor of the map
     */
    public TypeFriendlyHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    /**
     * Constructs a new TypeFriendlyHashMap
     * @param initialCapacity The initial capacity of the map
     */
    public TypeFriendlyHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Constructs a new TypeFriendlyHashMap
     */
    public TypeFriendlyHashMap() {
    }

    /**
     * Constructs a new TypeFriendlyHashMap, pre-populating it with a copy of the input map
     * @param m the map to copy
     * @see HashMap#HashMap(Map) 
     */
    public TypeFriendlyHashMap(Map<? extends K, ? extends V> m) {
        super(m);
    }
}
