package com.identityworksllc.iiq.common;

import sailpoint.server.Environment;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A container for a single value that is associated with the plugin cache
 * version. If the plugin cache is updated, the version increments, and the
 * provided {@link Supplier} will be invoked to get a new value on the next
 * call to {@link #get()}.
 *
 * This allows consistent references to plugin-provided classes, even if the
 * plugin is updated and a new version of the class is hot-deployed.
 *
 * Your {@link Supplier} code should always assume that the plugin cache has
 * been refreshed since the last invocation and never cache plugin objects.
 *
 * The object type T, of course, cannot be the actual implementation
 * in the plugin classloader, as that class will have become invalid when the
 * plugin cache was reset. The type T should be either a JDK class or a custom
 * interface implemented at the webapp layer.
 *
 * @param <T> the type of the value held in this reference
 */
public class VersionedReference<T> implements Supplier<T> {
    /**
     * The supplier that provides the initial value for this ThreadLocal.
     */
    private final Supplier<? extends T> supplier;

    /**
     * A container for the current value
     */
    private final AtomicReference<T> value;

    /**
     * The version of the plugin cache, used to determine if the threadlocal needs
     * to be replaced.
     */
    private int version;

    /**
     * Creates a new VersionedThreadLocal with the given supplier. The initial
     * value will not be calculated until the first call to {@link #get()}.
     *
     * @param supplier the initial value supplier
     */
    public VersionedReference(Supplier<? extends T> supplier) {
        this.supplier = supplier;
        this.value = new AtomicReference<>();
        this.version = -1;
    }

    /**
     * Returns the current value in this reference. If the plugin version has
     * changed since the value was last set, the supplier will be called to get
     * a new value.
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
                value.set(supplier.get());
            }
        }
        return value.get();
    }

}
