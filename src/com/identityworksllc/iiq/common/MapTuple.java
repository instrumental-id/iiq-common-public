package com.identityworksllc.iiq.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

/**
 * An extension of HashMap that can be produced by {@link MapTupleBuilder}. Values
 * can be accessed using either {@link #get(Object)} or {@link #getAt(int)},
 * referencing by index or by key.
 *
 * This class also implements the first six 'tuple' getters, such as getFirst,
 * getSecond, etc, which are equivalent to calling getAt(0), getAt(1), etc.
 *
 */
public final class MapTuple extends HashMap<String, Object> implements Serializable {
    /**
     * The list of keys, used to determine the values by index
     */
    private final List<String> keys;

    MapTuple(List<String> keys) {
        this.keys = keys;
    }

    public <T> T get(String key, Class<T> expectedClass) {
        Object result = get(key);
        if (result != null) {
            return expectedClass.cast(result);
        } else {
            return null;
        }
    }

    public Object getAt(int index) {
        if (index > keys.size()) {
            return null;
        }
        return get(keys.get(index));
    }

    public <T> T getAt(int index, Class<T> expectedClass) {
        Object result = getAt(index);
        if (result != null) {
            return expectedClass.cast(getAt(index));
        } else {
            return null;
        }
    }

    public Object getFifth() {
        return getAt(4);
    }

    public Object getFirst() {
        return getAt(0);
    }

    public Object getFourth() {
        return getAt(3);
    }

    public Object getSecond() {
        return getAt(1);
    }

    public Object getSixth() {
        return getAt(5);
    }

    public Object getThird() {
        return getAt(2);
    }
}
