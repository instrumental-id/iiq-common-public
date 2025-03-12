package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.cache.CacheMap;
import com.identityworksllc.iiq.common.cache.VersionedCacheEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class CacheMapTests {
    @Test
    public void clearRemovesAllEntries() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        cacheMap.put("key1", "value1");
        cacheMap.put("key2", "value2");
        cacheMap.clear();
        assertTrue(cacheMap.isEmpty());
    }

    @Test
    public void containsKeyReturnsFalseForNonExistingKey() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        assertFalse(cacheMap.containsKey("key1"));
    }

    @Test
    public void containsKeyReturnsTrueForExistingKey() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        cacheMap.put("key1", "value1");
        assertTrue(cacheMap.containsKey("key1"));
    }

    @Test
    public void getReturnsNullForExpiredEntry() throws InterruptedException {
        CacheMap<String, String> cacheMap = new CacheMap<>(1, TimeUnit.SECONDS);
        cacheMap.put("key1", "value1");
        Thread.sleep(2000);
        assertNull(cacheMap.get("key1"));
        assertFalse(cacheMap.containsKey("key1"));
        assertEquals(0, cacheMap.size());
    }

    @Test
    public void isExpiredReturnsTrueForChangedPluginVersion() {
        try (MockedStatic<Utilities> utilities = Mockito.mockStatic(Utilities.class)) {
            utilities.when(Utilities::getPluginVersionInt).thenReturn(2);
            VersionedCacheEntry<String> entry = new VersionedCacheEntry<>("value", 1, TimeUnit.HOURS);
            utilities.when(Utilities::getPluginVersionInt).thenReturn(3);
            assertTrue(entry.isExpired());
        }
    }

    @Test
    public void keySetReturnsAllNonExpiredKeys() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        cacheMap.put("key1", "value1");
        cacheMap.put("key2", "value2");
        Set<String> keys = cacheMap.keySet();
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
    }

    @Test
    public void putAddsNewEntry() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        cacheMap.put("key1", "value1");
        assertEquals("value1", cacheMap.get("key1"));
    }

    @Test
    public void putAllAddsAllEntries() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        cacheMap.putAll(map);
        assertEquals("value1", cacheMap.get("key1"));
        assertEquals("value2", cacheMap.get("key2"));
    }

    @Test
    public void removeDeletesEntry() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        cacheMap.put("key1", "value1");
        cacheMap.remove("key1");
        assertNull(cacheMap.get("key1"));
    }

    @Test
    public void sizeReturnsCorrectCount() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        cacheMap.put("key1", "value1");
        cacheMap.put("key2", "value2");
        assertEquals(2, cacheMap.size());
    }

    @Test
    public void sizeReturnsZeroForExpiredEntry() throws InterruptedException {
        CacheMap<String, String> cacheMap = new CacheMap<>(1, TimeUnit.SECONDS);
        cacheMap.put("key1", "value1");
        Thread.sleep(2000);
        assertEquals(0, cacheMap.size());
    }

    @Test
    public void valuesReturnsAllNonExpiredValues() {
        CacheMap<String, String> cacheMap = new CacheMap<>();
        cacheMap.put("key1", "value1");
        cacheMap.put("key2", "value2");
        Collection<String> values = cacheMap.values();
        assertTrue(values.contains("value1"));
        assertTrue(values.contains("value2"));
    }
}
