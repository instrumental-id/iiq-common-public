package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.cache.CacheMap;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An advanced Global Storage class that expands on IIQ's CustomGlobal
 * object. It allows simple get/set operations into a static global
 * concurrent map, but also provides a mechanism for ThreadLocal and
 * expiring values.
 */
@SuppressWarnings("unused")
public final class TempStorage implements DelegatedMap<String, Object>, TypeFriendlyMap<String, Object> {
    /**
     * The background daemon thread to clean up the temporary map every X minutes.
     * The {@link CacheMap} class only invalidates keys on certain operations, so
     * this thread will make sure invalidation happens routinely to avoid memory
     * leaks.
     *
     * This thread will be run with low priority and daemon status, meaning it will
     * not interrupt a JVM shutdown.
     */
    private static class CleanupThread extends Thread {
        /**
         * The map to clean up
         */
        private final TempStorage globalStorage;

        /**
         * Constructor that initiates the storage
         */
        private CleanupThread(TempStorage storage) {
            this.globalStorage = storage;
        }

        /**
         * Invokes the {@link TempStorage#internalCleanup()} method every 30 seconds
         */
        @Override
        public void run() {
            boolean done = false;
            while(!this.isInterrupted() && !done) {
                try {
                    Thread.sleep(30000L);
                } catch(InterruptedException e) {
                    done = true;
                }
                if (!this.isInterrupted() && !done) {
                    globalStorage.internalCleanup();
                }
            }
        }
    }

    /**
     * Singleton lock
     */
    private static final Object _LOCK = new Object();

    /**
     * The singleton object
     */
    private static TempStorage _SINGLETON;

    /**
     * Gets the singleton object, creating a new one if it does not exist.
     * @return The singleton TempStorage object
     */
    public static TempStorage get() {
        if (_SINGLETON == null) {
            synchronized (_LOCK) {
                if (_SINGLETON == null) {
                    _SINGLETON = new TempStorage();
                }
            }
        }
        return _SINGLETON;
    }

    /**
     * The cleanup daemon thread
     */
    private final Thread cleanupDaemon;

    /**
     * The internal timed cache with a default timeout of 5 minutes
     */
    private final CacheMap<String, Object> timedStorage;

    /**
     * Private constructor which also starts up the background thread
     */
    private TempStorage() {
        this.timedStorage = new CacheMap<>(5, TimeUnit.MINUTES);

        this.cleanupDaemon = new CleanupThread(this);
        this.cleanupDaemon.setDaemon(true);
        this.cleanupDaemon.setName("IDW IIQCommon Global TempStorage Cleanup Thread");
        this.cleanupDaemon.setPriority(Thread.MIN_PRIORITY);
        this.cleanupDaemon.start();
    }

    /**
     * Gets the internal CacheMap, required by DelegatedMap.
     * @return The internal cache map
     */
    @Override
    public Map<String, Object> getDelegate() {
        return timedStorage;
    }

    /**
     * Invokes a cleanup on the internal map
     */
    private void internalCleanup() {
        timedStorage.invalidateRecords();
    }

    /**
     * Returns true if the background cleanup thread is running. Note that like
     * {@link Thread#isAlive()}, this method may not return true for a couple of
     * seconds after the thread is initially started.
     *
     * @return True if the background thread is running
     */
    public boolean isCleanupDaemonRunning() {
        return this.cleanupDaemon.isAlive();
    }

    /**
     * Stops the background thread by interrupting it
     */
    public void stop() {
        this.cleanupDaemon.interrupt();
    }
}
