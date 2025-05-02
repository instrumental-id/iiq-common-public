package com.identityworksllc.iiq.common;

import sailpoint.server.Environment;

import java.util.function.Supplier;

/**
 * A thread-local container that is associated with the plugin cache version. If
 * a new plugin is installed, the version increments, and the entire ThreadLocal
 * will be replaced. This means that running processes relying on plugin-provided
 * classes can retrieve new versions of those objects.
 *
 * Your {@link Supplier} code should always assume that the plugin cache has
 * been refreshed since the last invocation and never cache plugin objects.
 *
 * The object type T, of course, cannot be the actual implementation
 * in the plugin classloader, as that class will have become invalid when the
 * plugin cache was reset. The type T should be either a JDK class or a custom
 * interface implemented at the webapp layer.
 *
 * @param <T> the type of the value held in this ThreadLocal
 */
public class VersionedThreadLocal<T> implements Supplier<T> {

    /**
     * Creates a new VersionedThreadLocal with the given initial value.
     * @param supplier the initial value supplier
     * @return a new VersionedThreadLocal
     * @param <U> the type of the value held in this ThreadLocal
     *
     * @see ThreadLocal#withInitial(Supplier) 
     */
    public static <U> VersionedThreadLocal<U> withInitial(Supplier<? extends U> supplier) {
        return new VersionedThreadLocal<>(supplier);
    }

    /**
     * The supplier that provides the initial value for this ThreadLocal.
     */
    private final Supplier<? extends T> supplier;

    /**
     * The ThreadLocal instance that holds the value. This will be replaced on the
     * next call to {@link #get()} or {@link #set} when the value changes.
     */
    private ThreadLocal<T> threadLocal;

    /**
     * The version of the plugin cache, used to determine if the threadlocal needs
     * to be replaced.
     */
    private int version;

    /**
     * Creates a new VersionedThreadLocal with the default initial value of null.
     */
    public VersionedThreadLocal() {
        this(() -> null);
    }

    /**
     * Creates a new VersionedThreadLocal with the given initial value.
     *
     * @param supplier the initial value supplier
     */
    private VersionedThreadLocal(Supplier<? extends T> supplier) {
        this.supplier = supplier;
        this.threadLocal = ThreadLocal.withInitial(supplier);
        this.version = Environment.getEnvironment().getPluginsCache().getVersion();
    }

    /**
     * Computes the value for this ThreadLocal using the given supplier. If the
     * value is already set, it will be returned. Otherwise, the supplier will be
     * called to compute the value and set it in this ThreadLocal.
     *
     * @param supplier the supplier to compute the value
     * @return the current or computed value
     */
    public T compute(Supplier<? extends T> supplier) {
        T value = get();
        if (value == null) {
            value = supplier.get();
            set(value);
        }
        return value;
    }

    /**
     * Returns the current value in this ThreadLocal. If the version has changed,
     * a new ThreadLocal will be created and the value returned from that.
     *
     * @return the current value in this ThreadLocal
     * @see ThreadLocal#get()
     */
    @Override
    public T get() {
        int currentVersion = Environment.getEnvironment().getPluginsCache().getVersion();
        if (currentVersion != version) {
            synchronized (this) {
                version = currentVersion;
                threadLocal = ThreadLocal.withInitial(supplier);
            }
        }

        return threadLocal.get();
    }

    /**
     * Sets the value in this ThreadLocal. If the version has changed, a new
     * ThreadLocal will be created and the value set in that.
     *
     * @param value the new value to set
     * @see ThreadLocal#set(Object)
     */
    public void set(T value) {
        int currentVersion = Environment.getEnvironment().getPluginsCache().getVersion();
        if (currentVersion != version) {
            synchronized(this) {
                version = currentVersion;
                threadLocal = ThreadLocal.withInitial(supplier);
            }
        }

        threadLocal.set(value);
    }

}
