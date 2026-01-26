package com.identityworksllc.iiq.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A singleton global object with an internal InheritableThreadLocal, allowing
 * sharing of cached objects within a single thread. Objects will be garbage
 * collected when the thread itself is cleaned up, so this should either not
 * be used or routinely clear()'d in long-running threads.
 *
 * Since the ThreadLocal is inheritable, child threads will automatically inherit
 * any stored values.
 *
 * This class exposes the ConcurrentMap interface and can be used as such.
 */
@SuppressWarnings("unused")
public final class ThreadStorage implements DelegatedConcurrentMap<String, Object>, TypeFriendlyMap<String, Object> {
    /**
     * The lock object used for singleton construction
     */
    private static final Object _LOCK = new Object();

    /**
     * The singleton object
     */
    private static ThreadStorage _SINGLETON;

    /**
     * Retrieves the singleton thread storage object, constructing a new one if needed
     * @return The singleton thread storage object
     */
    public static ThreadStorage get() {
        if (_SINGLETON == null) {
            synchronized (_LOCK) {
                if (_SINGLETON == null) {
                    _SINGLETON = new ThreadStorage();
                }
            }
        }
        return _SINGLETON;
    }

    /**
     * The InheritableThreadLocal object that stores the local Map data. It will
     * be initialized with an empty ConcurrentHashMap on first use in a given thread.
     */
    private final ThreadLocal<ConcurrentMap<String, Object>> threadLocal;

    /**
     * Private constructor to enforce singleton
     */
    private ThreadStorage() {
        threadLocal = InheritableThreadLocal.withInitial(ConcurrentHashMap::new);
    }

    /**
     * Retrieves the delegate, in this case the ThreadLocal map
     * @return The delegated map object
     */
    @Override
    public ConcurrentMap<String, Object> getDelegate() {
        return threadLocal.get();
    }
}
