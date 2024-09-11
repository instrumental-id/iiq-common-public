package com.identityworksllc.iiq.common;

import java.util.concurrent.ConcurrentMap;

public class TypeFriendlyDelegatedConcurrentMap<K, V> extends TypeFriendlyDelegatedMap<K, V> implements DelegatedConcurrentMap<K, V> {
    public TypeFriendlyDelegatedConcurrentMap(ConcurrentMap<K, V> input) {
        super(input);
    }

    @Override
    public ConcurrentMap<K, V> getDelegate() {
        return (ConcurrentMap<K, V>) super.getDelegate();
    }
}
