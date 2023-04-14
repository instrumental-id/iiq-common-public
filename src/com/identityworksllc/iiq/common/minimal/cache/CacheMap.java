package com.identityworksllc.iiq.common.minimal.cache;

import sailpoint.tools.Util;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Implements a Cache that exposes itself as a regular Map. Cached entries will be removed
 * from the map only when an operation would touch that entry, including all bulk
 * operations (e.g. {@link #isEmpty()}).
 *
 * If you serialize this class, it will be replaced with a HashMap<K,V> snapshot in
 * the serialization stream via {@link #writeReplace()}
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public class CacheMap<K, V> implements Map<K, V>, Serializable {

	/**
	 * The cache map entry class that will be returned by getEntry()
	 */
	protected class CacheMapEntry implements java.util.Map.Entry<K, V> {

		/**
		 * The key
		 */
		private final K key;

		/**
		 * The value
		 */
		private V value;

		/**
		 * Constructs a new map entry
		 *
		 * @param theKey The key value
		 * @param theValue The value associated with the key
		 */
		public CacheMapEntry(K theKey, V theValue) {
			this.key = theKey;
			this.value = theValue;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Map.Entry)) return false;
			Map.Entry<K, V> that = (Map.Entry<K, V>) o;
			return Objects.equals(key, that.getKey()) && Objects.equals(value, that.getValue());
		}

		/**
		 * @see java.util.Map.Entry#getKey()
		 */
		@Override
		public K getKey() {
			return key;
		}

		/**
		 * @see java.util.Map.Entry#getValue()
		 */
		@Override
		public V getValue() {
			return value;
		}

		@Override
		public int hashCode() {
			return Objects.hash(key, value);
		}

		/**
		 * @see java.util.Map.Entry#setValue(java.lang.Object)
		 */
		@Override
		public V setValue(V val) {
			CacheMap.this.put(key, val);
			V existing = value;
			this.value = val;
			return existing;
		}
	}

	/**
	 * The wrapper entry set for this map type, which will be returned by entrySet().
	 */
	protected class CacheMapEntrySet extends AbstractSet<CacheMapEntry> {
		@Override
		public Iterator<CacheMapEntry> iterator() {
			return internalMap.entrySet()
					.stream()
					.filter(e -> !e.getValue().isExpired())
					.map(e -> new CacheMapEntry(e.getKey(), e.getValue().getValue()))
					.iterator();
		}

		@Override
		public int size() {
			return CacheMap.this.size();
		}
	}

	/**
	 * Serialization UID
	 */
	private static final long serialVersionUID = 8575644570548892660L;

	/**
	 * Creates a before expiration hook
	 */
	private BiConsumer<K, V> beforeExpirationHook;

	/**
	 * The expiration time for a new entry in seconds
	 */
	private final long expirationTimeSeconds;
	
	/**
	 * The internal map associated with this cache containing keys to cache entries
	 */
	private final ConcurrentMap<K, CacheEntry<? extends V>> internalMap;

	/**
	 * A class used to generate new values for get() if they don't exist already
	 */
	private final CacheGenerator<? extends V> valueGenerator;

	/**
	 * Constructs a new cache map with the default expiration time of 10 minutes
	 */
	public CacheMap() {
		this(10, TimeUnit.MINUTES, (CacheGenerator<? extends V>) null);
	}

	/**
	 * Constructs a new empty cache map with the given expiration time in the given units. The time will be converted internally to seconds.
	 *
	 * @param amount The amount of the given time unit before expiration
	 * @param type The time unit
	 */
	public CacheMap(int amount, TimeUnit type) {
		this(amount, type, (CacheGenerator<? extends V>) null);
	}

	/**
	 * Constructs a new empty cache map with the given expiratino time in the given units.
	 * Additionally, copies all values from the given Map into this CacheMap, setting
	 * their expiration as though they had just been inserted.
	 *
	 * @param amount The amount of the given time unit before expiration
	 * @param type The time unit
	 * @param input An arbitrary Map to copy into this CacheMap
	 */
	public CacheMap(int amount, TimeUnit type, Map<? extends K, ? extends V> input) {
		this(amount, type, (CacheGenerator<? extends V>) null);

		if (input != null) {
			this.putAll(input);
		}
	}

	/**
	 * Constructs a new empty cache map with the given expiration time in the given units.
	 * The time will be converted internally to seconds if the result is less than one
	 * second, then it will default to one second.
	 *
	 * @param amount The amount of the given time unit before expiration
	 * @param type The time unit
	 * @param generator The value generator to use if get() does not match a key (pass null for none)
	 */
	public CacheMap(int amount, TimeUnit type, CacheGenerator<? extends V> generator) {
		this.internalMap = new ConcurrentHashMap<>();

		long secondsTime = TimeUnit.SECONDS.convert(amount, type);
		if (secondsTime < 1) {
			secondsTime = 1;
		}

		this.expirationTimeSeconds = secondsTime;

		if (this.expirationTimeSeconds > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Cache time cannot exceed " + Integer.MAX_VALUE + " seconds");
		}
		this.valueGenerator = generator;
		this.beforeExpirationHook = this::beforeExpiration;
	}

	/**
	 * The default initial before expiration hook. By default, this does nothing, but subclasses
	 * can override it to do whatever they'd like.
	 *
	 * @param key The key being expired
 	 * @param value The value being expired
	 */
	protected void beforeExpiration(K key, V value) {
		/* Do nothing by default */
	}

	/**
	 * Caches the given value with the default expiration seconds
	 *
	 * @param value The value to cache
	 * @return A cache entry representing this value
	 */
	protected CacheEntry<V> cache(V value) {
		return new CacheEntry<>(value, getNewExpirationDate());
	}

	/**
	 * @see java.util.Map#clear()
	 */
	@Override
	public void clear() {
		internalMap.clear();
	}

	/**
	 * Returns true if the map contains the given key and it is not expired. If the map
	 * does not contain the key, and a value generator is defined, it will be invoked,
	 * causing this method to always return true.
	 *
	 * @see java.util.Map#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(Object key) {
		if (!internalMap.containsKey(key)) {
			if (valueGenerator != null) {
				V newValue = valueGenerator.getValue(key);
				put((K) key, newValue);
			}
		}
		return internalMap.containsKey(key) && !internalMap.get(key).isExpired();
	}

	/**
	 * @see java.util.Map#containsValue(java.lang.Object)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean containsValue(Object value) {
		invalidateRecords();
		CacheEntry<V> val = cache((V) value);
		return internalMap.containsValue(val);
	}

	/**
	 * @see java.util.Map#entrySet()
	 */
	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return Collections.unmodifiableSet(new CacheMapEntrySet());
	}

	/**
	 * @see java.util.Map#get(java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		if (!containsKey(key)) {
			if (valueGenerator != null) {
				V newValue = valueGenerator.getValue(key);
				put((K) key, newValue);
			}
		}
		CacheEntry<? extends V> val = internalMap.get(key);
		if (val == null || val.isExpired()) {
			internalMap.remove(key);
			return null;
		}
		return val.getValue();
	}

	/**
	 * Returns a new Date object offset from the current date by the default expiration duration in seconds
	 *
	 * @return The date of expiration starting now
	 */
	protected Date getNewExpirationDate() {
		return Util.incrementDateBySeconds(new Date(), (int)expirationTimeSeconds);
	}

	/**
	 * Return the number of seconds remaining for the cache entry of the
	 * given key. If the key is not present or the cache entry has expired,
	 * this method returns 0.0.
	 *
	 * @param key The key to check
	 * @return The number of seconds remaining
	 */
	public double getSecondsRemaining(K key) {
		CacheEntry<? extends V> cacheEntry = internalMap.get(key);
		if (cacheEntry == null || cacheEntry.isExpired()) {
			return 0.0;
		}

		double time = (cacheEntry.getExpiration() - System.currentTimeMillis()) / 1000.0;
		if (time < 0.01) {
			time = 0.0;
		}
		return time;
	}

	/**
	 * Invalidate all records in the internal storage that have expired
	 */
	public void invalidateRecords() {
		Set<K> toRemove = new HashSet<K>();
		for (K key : internalMap.keySet()) {
			if (internalMap.get(key).isExpired()) {
				toRemove.add(key);
			}
		}
		for (K key : toRemove) {
			this.beforeExpirationHook.accept(key, internalMap.get(key).getValue());
			internalMap.remove(key);
		}
	}

	/**
	 * @see java.util.Map#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		invalidateRecords();
		return internalMap.isEmpty();
	}

	/**
	 * @see java.util.Map#keySet()
	 */
	@Override
	public Set<K> keySet() {
		invalidateRecords();
		return internalMap.keySet();
	}

	/**
	 * @see java.util.Map#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V put(K key, V value) {
		CacheEntry<? extends V> val = internalMap.put(key, cache(value));
		if (val == null || val.isExpired()) {
			return null;
		}
		return val.getValue();
	}

	/**
	 * @see java.util.Map#putAll(java.util.Map)
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (K key : m.keySet()) {
			put(key, m.get(key));
		}
	}

	/**
	 * Puts all cache entries from another CacheMap into this one. They will be copied
	 * as CacheEntry objects, thus retaining their expiration timestamps. Expired entries
	 * will not be copied.
	 *
	 * @param other The other CacheMap object
	 */
	public void putAll(CacheMap<? extends K, ? extends V> other) {
		for (K key : other.keySet()) {
			CacheEntry<? extends V> entry = other.internalMap.get(key);
			if (entry != null && !entry.isExpired()) {
				internalMap.put(key, entry);
			}
		}
	}

	/**
	 * @see java.util.Map#remove(java.lang.Object)
	 */
	@Override
	public V remove(Object key) {
		CacheEntry<? extends V> val = internalMap.remove(key);
		if (val == null || val.isExpired()) {
			return null;
		}
		return val.getValue();
	}

	/**
	 * @see java.util.Map#size()
	 */
	@Override
	public int size() {
		invalidateRecords();
		return internalMap.size();
	}

	/**
	 * Returns a snapshot of this cache at this moment in time as a java.util.HashMap. The
	 * snapshot will not be updated to reflect any future expirations of cache data.
	 *
	 * @return A snapshot of this cache
	 */
	public HashMap<K, V> snapshot() {
		invalidateRecords();
		HashMap<K, V> replacement = new HashMap<>();
		for( K key : keySet() ) {
			replacement.put(key, get(key));
		}
		return replacement;
	}

	/**
	 * @see java.util.Map#values()
	 */
	@Override
	public Collection<V> values() {
		invalidateRecords();
		List<V> values = new ArrayList<V>();
		for (CacheEntry<? extends V> val : internalMap.values()) {
			if (val != null && !val.isExpired()) {
				values.add(val.getValue());
			}
		}
		return values;
	}

	/**
	 * Adds the given hook function as a pre-expiration hook. It will be chained to any
	 * existing hooks using {@link BiConsumer#andThen(BiConsumer)}.
	 *
	 * @param hook The new hook
	 * @return This object, for chaining or static construction
	 */
	public CacheMap<K, V> withBeforeExpirationHook(BiConsumer<K, V> hook) {
		if (hook == null) {
			throw new IllegalArgumentException("Cannot pass a null hook");
		}
		if (this.beforeExpirationHook == null) {
			this.beforeExpirationHook = hook;
		} else {
			this.beforeExpirationHook = this.beforeExpirationHook.andThen(hook);
		}

		return this;
	}
	
	/**
	 * This method, called on serialization, will replace this cache with a static HashMap. This will allow this class to be used in remote EJB calls, etc.
	 * 
	 * See here: https://docs.oracle.com/javase/6/docs/platform/serialization/spec/output.html#5324
	 * 
	 * @return A static HashMap based on this class
	 * @throws ObjectStreamException if a failure occurs (part of the Serializable spec)
	 */
	@SuppressWarnings("unused")
	private Object writeReplace() throws ObjectStreamException {
		return snapshot();
	}
}
