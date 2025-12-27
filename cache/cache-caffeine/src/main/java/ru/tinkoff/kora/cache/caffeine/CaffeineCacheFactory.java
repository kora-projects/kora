package ru.tinkoff.kora.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;

public interface CaffeineCacheFactory {

    <K, V> Cache<K, V> build(String name, CaffeineCacheConfig config);
}
