package com.identityworksllc.iiq.common;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Lazily initializes an instance of T in a thread-safe manner on the
 * first call to {@link #get()}. The instance of T returned from the initializer
 * must not be associated with any particular session.
 *
 * If an associator is defined, the object will be associated with the current session
 * via a call to {@link Associator#associate(SailPointContext, Object)} each time {@link #get()}
 * is called.
 *
 * @param <T> The type to lazily initialize
 */
public final class Lazy<T> implements Supplier<Maybe<T>> {
    /**
     * Associates the instance of T with the given SailPointContext.
     * @param <T> The type to associate
     */
    @FunctionalInterface
    public interface Associator<T> {
        /**
         * Associates the given instance of T with the provided SailPointContext.
         * @param context The SailPointContext to associate with
         * @param instance The instance of T to associate
         * @throws GeneralException if association fails
         * @return The associated instance of T
         */
        T associate(SailPointContext context, T instance) throws GeneralException;
    }

    /**
     * Initializer functional interface for creating instances of T.
     * @param <T> The type to initialize
     */
    @FunctionalInterface
    public interface Initializer<T> {
        /**
         * Initializes and returns an instance of T.
         * @return The initialized instance of T
         * @throws GeneralException if initialization fails
         */
        T initialize() throws GeneralException;
    }

    /**
     * The associator used to associate instances of T with a SailPointContext.
     */
    private final Associator<T> associator;

    /**
     * The initializer used to create the instance of T
     */
    private final Initializer<T> initializer;

    /**
     * The lazily initialized instance of T, wrapped in an AtomicReference to allow
     * 'null' to be a valid value.
     */
    private AtomicReference<T> instance;

    /**
     * Lock to ensure thread-safe initialization
     */
    private final Lock lock;

    /**
     * Constructs a Lazy instance with the given initializer.
     * @param initializer The initializer to create instances of T
     */
    public Lazy(Initializer<T> initializer) {
        this(initializer, null);
    }

    /**
     * Constructs a Lazy instance with the given initializer and associator.
     * @param initializer The initializer to create instances of T
     * @param associator The associator to associate instances of T with a SailPointContext
     */
    public Lazy(Initializer<T> initializer, Associator<T> associator) {
        this.initializer = Objects.requireNonNull(initializer, "Initializer cannot be null");
        this.associator = associator;
        this.lock = new ReentrantLock();
    }

    /**
     * Returns the lazily initialized instance of T, initializing it if necessary.
     * If an associator is defined, the object will also be associated with the
     * SP context for the current thread.
     *
     * If you use an associator, the instance of T returned from this method may
     * be different each time, as it is associated with the current context.
     *
     * @return The instance of T wrapped in a {@link Maybe}
     */
    public Maybe<T> get() {
        try {
            if (instance == null) {
                try {
                    lock.lockInterruptibly();
                    try {
                        if (instance == null) {
                            instance = new AtomicReference<>(initializer.initialize());
                        }
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GeneralException("Thread was interrupted during lazy initialization", e);
                }
            }
            T object = instance.get();
            if (associator != null) {
                SailPointContext currentContext = SailPointFactory.getCurrentContext();
                if (currentContext != null) {
                    object = associator.associate(currentContext, object);
                }
            }
            return Maybe.of(object);
        } catch(GeneralException e) {
            return Maybe.of(e);
        }
    }
}
