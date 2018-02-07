package ru.mail.polis;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import ru.mail.polis.gt.LFUCache;

public class CacheTest {
    private static LFUCache cache;

    private byte[] d1 = "DATA1".getBytes(),
                   d2 = "DATA2".getBytes(),
                   d3 = "DATA3".getBytes(),
                   d4 = "DATA4".getBytes();

    @Before
    public void init() {
        cache = new LFUCache(3);
    }

    @Test
    public void cacheHit() {
        cache.addCacheEntry("1", d1);
        cache.addCacheEntry("2", d2);
        cache.addCacheEntry("3", d3);

        byte[] outcome = cache.getCacheEntry("2");

        assertEquals(outcome, d2);
        assertEquals(cache.getCacheEntry("4"), null);
    }

    @Test
    public void rightDelete() {
        cache.addCacheEntry("1", d1);
        cache.addCacheEntry("2", d2);
        cache.addCacheEntry("3", d3);

        for (int i = 0; i < 100; i++) {
            cache.getCacheEntry("1");
            cache.getCacheEntry("2");
            cache.getCacheEntry("3");
        }
        cache.getCacheEntry("1");
        cache.getCacheEntry("1");
        cache.getCacheEntry("3");

        cache.addCacheEntry("4", d4);

        assertEquals(cache.getCacheEntry("2"), null);
        assertEquals(cache.getCacheEntry("1"), d1);
        assertEquals(cache.getCacheEntry("3"), d3);
        assertEquals(cache.getCacheEntry("4"), d4);
    }

}
