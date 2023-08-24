package ru.tinkoff.kora.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;

public interface CaffeineCacheMetricCollector {

    void register(String cacheName, Cache<?, ?> cache);
}
