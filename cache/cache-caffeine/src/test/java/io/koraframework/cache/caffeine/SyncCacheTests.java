package io.koraframework.cache.caffeine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import io.koraframework.cache.caffeine.testdata.DummyCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncCacheTests extends CacheRunner {

    private final DummyCache cache = createCache();

    @BeforeEach
    void reset() {
        cache.invalidateAll();
    }

    @Test
    void getWhenCacheEmpty() {
        // given
        var key = "1";

        // when
        assertNull(cache.get(key));
    }

    @Test
    void getWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.put(key, value);

        // then
        final String fromCache = cache.get(key);
        assertEquals(value, fromCache);
    }

    @Test
    void getWrongKeyWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.put(key, value);

        // then
        final String fromCache = cache.get("2");
        assertNull(fromCache);
    }

    @Test
    void getWhenCacheInvalidate() {
        // given
        var key = "1";
        var value = "1";
        cache.put(key, value);

        // when
        cache.invalidate(key);

        // then
        final String fromCache = cache.get(key);
        assertNull(fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        var key = "1";
        var value = "1";
        cache.put(key, value);

        // when
        cache.invalidateAll();

        // then
        final String fromCache = cache.get(key);
        assertNull(fromCache);
    }

    @Test
    void operationsAreDisabledWhenConfigDisabled() {
        // given
        var disabledCache = createCache(false);

        // when
        assertEquals("1", disabledCache.put("1", "1"));
        assertEquals(Map.of("2", "2"), disabledCache.put(Map.of("2", "2")));

        // then
        assertNull(disabledCache.get("1"));
        assertTrue(disabledCache.get(List.of("1", "2")).isEmpty());
        assertTrue(disabledCache.getAll().isEmpty());

        // when
        assertEquals("3", disabledCache.computeIfAbsent("3", k -> "3"));
        assertEquals(Map.of("4", "4"), disabledCache.computeIfAbsent(Set.of("4"), keys -> Map.of("4", "4")));

        // then
        assertNull(disabledCache.get("3"));
        assertTrue(disabledCache.get(List.of("4")).isEmpty());
    }

    @Test
    void computeIfAbsentRecursiveOnSameCache() {
        // given
        var depth = 150;

        // when
        var result = path(depth);

        // then
        assertTrue(result.startsWith("root/1/2/"));
        assertTrue(result.endsWith("/149/150"));
        for (int id = 0; id <= depth; id++) {
            assertNotNull(cache.get(String.valueOf(id)));
        }
        assertEquals(result, path(depth));
    }

    private String path(int id) {
        return cache.computeIfAbsent(String.valueOf(id), k -> id == 0 ? "root" : path(id - 1) + "/" + id);
    }

    @Test
    void computeIfAbsentNestedOnSameCache() {
        // given "Aa" and "BB" have the same hashCode and land in the same map bin
        var outerKey = "Aa";
        var innerKey = "BB";

        // when
        var outer = cache.computeIfAbsent(outerKey, k -> cache.computeIfAbsent(innerKey, k2 -> "inner") + "-outer");

        // then
        assertEquals("inner-outer", outer);
        assertEquals("inner-outer", cache.get(outerKey));
        assertEquals("inner", cache.get(innerKey));
    }

    @Test
    void computeIfAbsentManyNestedOnSameCache() {
        // when
        var outer = cache.computeIfAbsent(Set.of("1"), keys -> Map.of("1", cache.computeIfAbsent(Set.of("2"), keys2 -> Map.of("2", "2")).get("2") + "1"));

        // then
        assertEquals(Map.of("1", "21"), outer);
        assertEquals("21", cache.get("1"));
        assertEquals("2", cache.get("2"));
    }

    @Test
    void computeIfAbsentDoesNotCacheNull() {
        // when
        assertNull(cache.computeIfAbsent("1", k -> null));

        // then
        assertNull(cache.get("1"));
        assertEquals("1", cache.computeIfAbsent("1", k -> "1"));
        assertEquals("1", cache.computeIfAbsent("1", k -> "2"));
    }

    @Test
    void computeIfAbsentConcurrentLoadsOnce() throws Exception {
        // given
        var threads = 16;
        var loads = new AtomicInteger();
        var start = new CountDownLatch(1);

        // when
        var results = new ArrayList<Future<String>>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return cache.computeIfAbsent("1", k -> {
                        var n = loads.incrementAndGet();
                        sleep(200);
                        return "v" + n;
                    });
                }));
            }
            start.countDown();

            // then
            for (var result : results) {
                assertEquals("v1", result.get(10, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, loads.get());
        assertEquals("v1", cache.get("1"));
    }

    @Test
    void computeIfAbsentConcurrentWaitersGetLoaderException() throws Exception {
        // given
        var error = new IllegalArgumentException("load failed");
        var loading = new CountDownLatch(1);

        // when
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.computeIfAbsent("1", k -> {
                loading.countDown();
                sleep(200);
                throw error;
            }));
            loading.await();
            var second = executor.submit(() -> cache.computeIfAbsent("1", k -> "other"));

            // then
            for (var result : List.of(first, second)) {
                var e = assertThrows(java.util.concurrent.ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
                assertSame(error, e.getCause());
            }
        }
        assertNull(cache.get("1"));
    }

    @Test
    void computeIfAbsentConcurrentWaitersGetLoaderCheckedException() throws Exception {
        // given: a Kotlin @Cacheable function can throw a checked exception through computeIfAbsent
        var error = new java.io.IOException("load failed");
        var loading = new CountDownLatch(1);

        // when
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.computeIfAbsent("1", k -> {
                loading.countDown();
                sleep(200);
                throw SyncCacheTests.<RuntimeException>sneakyThrow(error);
            }));
            loading.await();
            var second = executor.submit(() -> cache.computeIfAbsent("1", k -> "other"));

            // then
            for (var result : List.of(first, second)) {
                var e = assertThrows(java.util.concurrent.ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
                assertSame(error, e.getCause());
            }
        }
        assertNull(cache.get("1"));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }

    @Test
    void computeIfAbsentInvalidateDuringLoadWins() throws Exception {
        // given
        var loading = new CountDownLatch(1);
        var loader = Thread.ofVirtual().start(() -> cache.computeIfAbsent("1", k -> {
            loading.countDown();
            sleep(300);
            return "old";
        }));
        loading.await();

        // when
        cache.invalidate("1");
        loader.join();

        // then
        assertNull(cache.get("1"));
        assertEquals("new", cache.computeIfAbsent("1", k -> "new"));
    }

    @Test
    void computeIfAbsentInvalidateAllDuringLoadWins() throws Exception {
        // given
        var loading = new CountDownLatch(1);
        var loader = Thread.ofVirtual().start(() -> cache.computeIfAbsent("1", k -> {
            loading.countDown();
            sleep(300);
            return "old";
        }));
        loading.await();

        // when
        cache.invalidateAll();
        loader.join();

        // then
        assertNull(cache.get("1"));
    }

    @Test
    void computeIfAbsentPutDuringLoadWins() throws Exception {
        // given
        var loading = new CountDownLatch(1);
        var loader = Thread.ofVirtual().start(() -> cache.computeIfAbsent("1", k -> {
            loading.countDown();
            sleep(300);
            return "old";
        }));
        loading.await();

        // when
        cache.put("1", "new");
        loader.join();

        // then
        assertEquals("new", cache.get("1"));
    }

    @Test
    void computeIfAbsentManyInvalidateDuringLoadWins() throws Exception {
        // given
        var loading = new CountDownLatch(1);
        var loader = Thread.ofVirtual().start(() -> cache.computeIfAbsent(Set.of("1"), keys -> {
            loading.countDown();
            sleep(300);
            return Map.of("1", "old");
        }));
        loading.await();

        // when
        cache.invalidate("1");
        loader.join();

        // then
        assertNull(cache.get("1"));
    }

    @Test
    void computeIfAbsentManyJoinsSingleKeyLoadInFlight() throws Exception {
        // given
        var loads = new AtomicInteger();
        var loading = new CountDownLatch(1);
        var single = Thread.ofVirtual().start(() -> cache.computeIfAbsent("1", k -> {
            loads.incrementAndGet();
            loading.countDown();
            sleep(300);
            return "v";
        }));
        loading.await();

        // when
        var result = cache.computeIfAbsent(Set.of("1", "2"), keys -> {
            loads.incrementAndGet();
            assertEquals(Set.of("2"), keys);
            return Map.of("2", "v2");
        });
        single.join();

        // then
        assertEquals(Map.of("1", "v", "2", "v2"), result);
        assertEquals(2, loads.get());
        assertEquals("v2", cache.get("2"));
    }

    @Test
    void computeIfAbsentSingleKeyJoinsManyLoadInFlight() throws Exception {
        // given
        var loads = new AtomicInteger();
        var loading = new CountDownLatch(1);
        var bulk = Thread.ofVirtual().start(() -> cache.computeIfAbsent(Set.of("1"), keys -> {
            loads.incrementAndGet();
            loading.countDown();
            sleep(300);
            return Map.of("1", "v");
        }));
        loading.await();

        // when
        var result = cache.computeIfAbsent("1", k -> {
            loads.incrementAndGet();
            return "other";
        });
        bulk.join();

        // then
        assertEquals("v", result);
        assertEquals(1, loads.get());
    }

    @Test
    void computeIfAbsentRecursiveOnSameKey() {
        // when
        var result = cache.computeIfAbsent("1", k -> cache.computeIfAbsent("1", k2 -> "inner") + "-outer");

        // then
        assertEquals("inner-outer", result);
        assertEquals("inner-outer", cache.get("1"));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
