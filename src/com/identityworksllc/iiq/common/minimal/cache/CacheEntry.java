package com.identityworksllc.iiq.common.minimal.cache;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache entry for use with the CacheMap class or other purposes. This class allows you
 * to store a dated object of any type. The entry will be considered expired when the
 * current date is after the expiration date.
 *
 * Instances of this class are {@link Serializable} if the contained type T is serializable.
 *
 * @param <T> The type of the object to store
 */
public final class CacheEntry<T> implements Serializable, Supplier<Optional<T>> {

	/**
	 * Serialization UID
	 */
	private static final long serialVersionUID = 2L;

	/**
	 * Returns either the entry (if it is not null and not expired), or invokes the Supplier
	 * to generate a new entry if it is expired.
	 *
	 * @param entry The existing entry (which can be null)
	 * @param valueSupplier A function to calculate a new value
	 * @return The value supplier
	 * @param <T> The type of thing that is cached
	 */
	public static <T> CacheEntry<T> computeIfExpired(CacheEntry<T> entry, Supplier<CacheEntry<T>> valueSupplier) {
		if (entry == null || entry.isExpired()) {
			return valueSupplier.get();
		} else {
			return entry;
		}
	}
	/**
	 * Expiration millisecond timestamp for this entry
	 */
	private final long expiration;

	/**
	 * The object type
	 */
	private final T value;

	/**
	 * Construct a new cache entry that expires a specific amount of time in the future
	 * @param entryValue The entry value
	 * @param futureTimeAmount The amount of time in the future (from right now) to expire it
	 * @param timeUnit The units in which the amount of time is specified
	 */
	public CacheEntry(T entryValue, long futureTimeAmount, TimeUnit timeUnit) {
		this(entryValue, System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(futureTimeAmount, timeUnit));
	}

	/**
	 * Construct a new cache entry that expires on a specific date
	 * @param entryValue The value of this cache entry
	 * @param entryExpiration The expiration date of this cache entry
	 */
	public CacheEntry(T entryValue, Date entryExpiration) {
		this(entryValue, entryExpiration.getTime());
	}

	/**
	 * Construct a new cache entry that expires at a specific Unix timestamp (in milliseconds)
	 * @param entryValue The value of this cache entry
	 * @param entryExpirationTimestampMillis The expiration date of this cache entry
	 */
	public CacheEntry(T entryValue, long entryExpirationTimestampMillis) {
		this.value = entryValue;
		this.expiration = entryExpirationTimestampMillis;
	}

	/**
	 * @see Object#equals(Object)
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CacheEntry<?> that = (CacheEntry<?>) o;
		return Objects.equals(value, that.value);
	}

	/**
	 * If the entry is expired, returns {@link Optional#empty()}.
	 * If the entry is not expired, returns {@link Optional#ofNullable(Object)}.
	 *
	 * @return An optional containing a non-expired entry value
	 */
	@Override
	public Optional<T> get() {
		if (isExpired()) {
			return Optional.empty();
		} else {
			return Optional.ofNullable(value);
		}
	}

	/**
	 * Returns the expiration instant, in Unix timestamp millis
	 * @return The expiration timestamp
	 */
	public long getExpiration() {
		return expiration;
	}

	/**
	 * Gets the value associated with this cache entry
	 *
	 * @return The cached value
	 */
	public T getValue() {
		return this.value;
	}

	/**
	 * @see Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	/**
	 * The entry is expired if the current time is after the expiration date
	 *
	 * @return True if expired
	 */
	public boolean isExpired() {
		return System.currentTimeMillis() >= expiration;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", CacheEntry.class.getSimpleName() + "[", "]")
				.add("expiration timestamp=" + expiration)
				.add("value=" + value)
				.toString();
	}
}
