package ru.tinkoff.kora.cache.caffeine;

import ru.tinkoff.kora.cache.Cache;

import java.util.Map;

public interface CaffeineCache<K, V> extends Cache<K, V> {

    /**
     * @return all values and keys
     */
    Map<K, V> getAll();
}
