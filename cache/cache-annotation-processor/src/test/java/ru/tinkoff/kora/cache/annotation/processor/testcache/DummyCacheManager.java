package ru.tinkoff.kora.cache.annotation.processor.testcache;

import ru.tinkoff.kora.cache.CacheManager;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class DummyCacheManager<K, V> implements CacheManager<K, V> {

    private final Map<String, DummyCache<K, V>> cacheMap = new HashMap<>();

    @Override
    public DummyCache<K, V> getCache(@Nonnull String name) {
        return cacheMap.computeIfAbsent(name, k -> new DummyCache<>(name));
    }

    public void reset() {
        cacheMap.values().forEach(DummyCache::reset);
    }
}
