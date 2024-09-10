package com.identityworksllc.iiq.common.cache;

import com.identityworksllc.iiq.common.Utilities;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A plugin version aware extension of {@link CacheEntry}, which will consider
 * itself expired whenever the plugin version has changed from the version at
 * entry creation.
 *
 * Instances of this class are NOT Serializable, because it doesn't make sense to
 * serialize a plugin-versioned object.
 *
 * @param <T> The type stored in this cache entry
 */
public class VersionedCacheEntry<T> extends CacheEntry<T> {
    /**
     * Returns either the current object, if it is already a VersionedCacheEntry, or
     * a newly constructed copy of it.
     *
     * @param other The other object to either return or copy
     * @return A non-null VersionedCacheObject
     * @param <V> The type of the object
     * @throws IllegalArgumentException if the input is null or otherwise invalid
     */
    @SuppressWarnings("unchecked")
    public static <V> VersionedCacheEntry<V> of(CacheEntry<? extends V> other) throws IllegalArgumentException {
        if (other == null) {
            throw new IllegalArgumentException("Cannot construct a VersionedCacheEntry from a null object");
        }

        if (other instanceof VersionedCacheEntry) {
            return (VersionedCacheEntry<V>) other;
        } else {
            return new VersionedCacheEntry<>(other);
        }
    }

    /**
     * The plugin version, set at entry creation
     */
    private final int pluginVersion;

    /**
     * Copy constructor for another cache entry. If the other entry is a VersionedCacheEntry,
     * its pluginVersion will also be copied. Otherwise, the current plugin version will be
     * used.
     *
     * This should be used with caution, because you can construct a versioned object that
     * looks right, but is not.
     *
     * @param entry The entry to copy
     */
    public VersionedCacheEntry(CacheEntry<? extends T> entry) {
        super(Objects.requireNonNull(entry.getValue()), entry.getExpiration());

        if (entry instanceof VersionedCacheEntry) {
            this.pluginVersion = ((VersionedCacheEntry<?>) entry).pluginVersion;
        } else {
            this.pluginVersion = Utilities.getPluginVersionInt();
        }
    }

    /**
     * Constructs a new VersionedCacheEntry expiring at the given time in the future
     * @param entryValue The value being cached
     * @param futureTimeAmount The number of time units in the future to expire this entry
     * @param timeUnit The time unit (e.g., seconds) to calculate the expiration time
     */
    public VersionedCacheEntry(T entryValue, long futureTimeAmount, TimeUnit timeUnit) {
        super(Objects.requireNonNull(entryValue), futureTimeAmount, Objects.requireNonNull(timeUnit));
        this.pluginVersion = Utilities.getPluginVersionInt();
    }

    /**
     * Constructs a new VersionedCacheEntry expiring at the given Date
     * @param entryValue The value being cached
     * @param entryExpiration The
     */
    public VersionedCacheEntry(T entryValue, Date entryExpiration) {
        super(Objects.requireNonNull(entryValue), Objects.requireNonNull(entryExpiration));
        this.pluginVersion = Utilities.getPluginVersionInt();
    }

    /**
     * Constructs a new VersionedCacheEntry expiring at the given epoch millisecond timestamp
     * @param entryValue The value being cached
     * @param entryExpirationTimestampMillis The epoch millisecond timestamp after which this entry is expired
     */
    public VersionedCacheEntry(T entryValue, long entryExpirationTimestampMillis) {
        super(Objects.requireNonNull(entryValue), entryExpirationTimestampMillis);
        this.pluginVersion = Utilities.getPluginVersionInt();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionedCacheEntry)) return false;
        if (!super.equals(o)) return false;
        VersionedCacheEntry<?> that = (VersionedCacheEntry<?>) o;
        return pluginVersion == that.pluginVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pluginVersion);
    }

    /**
     * Marks this object expired if its time has elapsed or if the plugin version
     * has changed since the cache entry was created.
     *
     * @return True if this is expired
     */
    @Override
    public boolean isExpired() {
        int currentPluginVersion = Utilities.getPluginVersionInt();
        return super.isExpired() || (currentPluginVersion != pluginVersion);
    }
}
