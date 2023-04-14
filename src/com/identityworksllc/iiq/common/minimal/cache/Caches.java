package com.identityworksllc.iiq.common.minimal.cache;

import sailpoint.object.Configuration;
import sailpoint.object.Custom;

import java.util.concurrent.TimeUnit;

/**
 * Static implementations of specific commonly-used caches
 */
public class Caches {
    /**
     * A static configuration cache map
     */
    private static final CacheMap<String, Configuration> configurationCacheMap = getConfigurationCache();

    /**
     * A custom configuration cache map
     */
    private static final CacheMap<String, Custom> customCacheMap = getCustomCache();

    /**
     * Gets the Configuration object (cached or newly loaded) that corresponds to the given name or ID
     * @param nameOrId The name or ID
     * @return the Configuration object
     */
    public static Configuration getConfiguration(String nameOrId) {
        return configurationCacheMap.get(nameOrId);
    }

    /**
     * A 60-second cache to hold Configuration objects
     * @return a CacheMap that holds configuration objects
     */
    public static CacheMap<String, Configuration> getConfigurationCache() {
        return new CacheMap<>(60, TimeUnit.SECONDS, new SailPointObjectCacheGenerator<>(Configuration.class));
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
     * A 60-second cache to hold Custom objects
     * @return a CacheMap that holds Custom objects
     */
    public static CacheMap<String, Custom> getCustomCache() {
        return new CacheMap<>(60, TimeUnit.SECONDS, new SailPointObjectCacheGenerator<>(Custom.class));
    }

    /**
     * Private constructor to prevent instantiation
     */
    private Caches() {
        /* This class cannot be constructed */
        throw new UnsupportedOperationException();
    }
}
