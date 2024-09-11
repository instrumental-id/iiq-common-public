package com.identityworksllc.iiq.common;

import java.io.ObjectStreamException;
import java.util.Map;

/**
 * The TypeFriendlyDelegatedMap class combines {@link DelegatedMap} and {@link TypeFriendlyMap}
 * in a single decorator class. Wrapping your existing {@link Map} into this class automatically
 * creates
 *
 * @param <K>
 * @param <V>
 */
public class TypeFriendlyDelegatedMap<K, V> implements DelegatedMap<K, V>, TypeFriendlyMap<K, V> {

    private final Map<K, V> delegate;

    /**
     * Wraps an existing Map in a {@link TypeFriendlyMap}.
     *
     * @param input The map to wrap with {@link TypeFriendlyMap} functions
     */
    public TypeFriendlyDelegatedMap(Map<K, V> input) {
        this.delegate = input;
    }

    /**
     * Returns the Map that is wrapped by this one
     * @return The map to which all calls should be delegated
     */
    @Override
    public Map<K, V> getDelegate() {
        return delegate;
    }

    /**
     * During serialization, replaces this object in the output stream with its
     * delegate.
     * @return The delegate object
     * @throws ObjectStreamException If any failures occur (they won't)
     */
    @SuppressWarnings("unused")
    public Object writeReplace() throws ObjectStreamException {
        return delegate;
    }
}
