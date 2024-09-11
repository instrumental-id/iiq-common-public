package com.identityworksllc.iiq.common;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Basic override map class that exists entirely to include the TypeFriendlyMap default methods
 * @param <K> The key type
 * @param <V> The value type
 */
@SuppressWarnings("unused")
public class TypeFriendlyConcurrentHashMap<K, V> extends ConcurrentSkipListMap<K, V> implements TypeFriendlyMap<K, V> {

    /**
     * @see ConcurrentSkipListMap
     */
    public TypeFriendlyConcurrentHashMap() {
        super();
    }

    /**
     * @see ConcurrentSkipListMap
     */
    public TypeFriendlyConcurrentHashMap(Map<K, V> input) {
        super(input);
    }
}
