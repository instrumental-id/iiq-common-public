package com.identityworksllc.iiq.common.cache;

import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Custom;
import sailpoint.object.SailPointObject;

import java.util.concurrent.TimeUnit;

/**
 * Static implementations of specific commonly-used caches
 */
public class Caches {
    /**
     * A static Bundle cache map
     */
    private static final CacheMap<String, Bundle> bundleCacheMap = createBundleCache();
    /**
     * A static Configuration cache map
     */
    private static final CacheMap<String, Configuration> configurationCacheMap = createConfigurationCache();
    /**
     * A static Custom cache map
     */
    private static final CacheMap<String, Custom> customCacheMap = createCustomCache();

    /**
     * Private constructor to prevent instantiation
     */
    private Caches() {
        /* This class cannot be constructed */
        throw new UnsupportedOperationException();
    }

    /**
     * Clears all items in the Bundle, Configuration, and Custom caches
     */
    public static void clear() {
        bundleCacheMap.clear();
        configurationCacheMap.clear();
        customCacheMap.clear();
    }

    /**
     * Creates a new to hold Bundle objects, with the configured timeout
     * @return a CacheMap that holds Bundle objects
     */
    public static CacheMap<String, Bundle> createBundleCache() {
        return createCache(Bundle.class);
    }

    /**
     * Creates a new instance of a cache to hold the given type. The default cache will
     * expire its entries after 60 seconds, but this can be configured as described in
     * {@link #getDefaultTimeoutSeconds(Class)}.
     *
     * @param type The type of object to store in the cache
     * @return The newly created cache
     * @param <T> The type reference of the SailPointObject to construct
     */
    public static <T extends SailPointObject> CacheMap<String, T> createCache(Class<T> type) {
        return new CacheMap<>(getDefaultTimeoutSeconds(type), TimeUnit.SECONDS, new SailPointObjectCacheGenerator<>(type));
    }

    /**
     * Creates a new to hold Configuration objects, with the configured timeout
     * @return a CacheMap that holds Configuration objects
     */
    public static CacheMap<String, Configuration> createConfigurationCache() {
        return createCache(Configuration.class);
    }

    /**
     * Creates a new to hold Custom objects, with the configured timeout
     * @return a CacheMap that holds Custom objects
     */
    public static CacheMap<String, Custom> createCustomCache() {
        return createCache(Custom.class);
    }

    /**
     * Retrieves a Bundle from the cache, or from the DB if the cache has
     * expired
     * @param nameOrId The name or ID of the Bundle to retrieve
     * @return The resulting Bundle, if one exists with that name
     */
    public static Bundle getBundle(String nameOrId) {
        return bundleCacheMap.get(nameOrId);
    }

    /**
     * Gets the static Bundle cache map
     * @return The static Bundle cache map
     */
    public static CacheMap<String, Bundle> getBundleCache() {
        return bundleCacheMap;
    }

    /**
     * Gets the Configuration object (cached or newly loaded) that corresponds to the given name or ID
     * @param nameOrId The name or ID
     * @return the Configuration object
     */
    public static Configuration getConfiguration(String nameOrId) {
        return configurationCacheMap.get(nameOrId);
    }

    /**
     * Gets the static Configuration cache map created on startup
     * @return The static Configuration cache map
     */
    public static CacheMap<String, Configuration> getConfigurationCache() {
        return configurationCacheMap;
    }

    /**
     * Gets the Custom (cached or newly loaded) that corresponds to the given name or ID
     * @param nameOrId The name or ID
     * @return the Custom object
     */
    public static Custom getCustom(String nameOrId) {
        return customCacheMap.get(nameOrId);
    }

    /**
     * Gets the static Custom cache map created on startup
     * @return The static Custom cache map created on startup
     */
    public static CacheMap<String, Custom> getCustomCacheMap() {
        return customCacheMap;
    }

    /**
     * Returns the configured cache timeout in seconds. This will consult the following
     * configuration keys in SystemConfiguration:
     *
     *  IIQCommon.Caches.(type).Timeout
     *  IIQCommon.Caches.Default.Timeout
     *
     * If none are present, or if both are less than 1, the default of 60 seconds is used.
     *
     * @return The timeout in seconds, with a default of 60
     */
    public static int getDefaultTimeoutSeconds(Class<? extends SailPointObject> type) {
        int timeout = 60;

        Configuration systemConfig = Configuration.getSystemConfig();
        if (systemConfig != null) {
            int configuredTimeoutSpecific = systemConfig.getInt("IIQCommon.Caches." + type.getSimpleName() + ".Timeout");
            if (configuredTimeoutSpecific > 0) {
                timeout = configuredTimeoutSpecific;
            } else {
                int configuredTimeout = systemConfig.getInt("IIQCommon.Caches.Default.Timeout");
                if (configuredTimeout > 0) {
                    timeout = configuredTimeout;
                }
            }
        }

        return timeout;
    }
}
