package ru.tinkoff.kora.cache.testcache;

import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.CacheManager;
import ru.tinkoff.kora.cache.LoadableCache;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class DummyCacheManager<K, V> implements CacheManager<K, V> {

    private final Map<String, Cache<K, V>> cacheMap = new HashMap<>();

    @Override
    public Cache<K, V> getCache(@Nonnull String name) {
        return cacheMap.computeIfAbsent(name, k -> new DummyCache<>(name));
    }

    public void reset() {
        cacheMap.clear();
    }
}
