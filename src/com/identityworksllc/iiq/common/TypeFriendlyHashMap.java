package com.identityworksllc.iiq.common;

import java.util.HashMap;
import java.util.Map;

/**
 * A minor extension to {@link HashMap} to include the {@link TypeFriendlyMap} methods
 * @param <K> The key type
 * @param <V> The value type
 */
public class TypeFriendlyHashMap<K, V> extends HashMap<K, V> implements TypeFriendlyMap<K, V> {
    public TypeFriendlyHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public TypeFriendlyHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public TypeFriendlyHashMap() {
    }

    public TypeFriendlyHashMap(Map<? extends K, ? extends V> m) {
        super(m);
    }
}
