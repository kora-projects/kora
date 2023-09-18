package ru.tinkoff.kora.cache.caffeine;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.Cache;

import java.util.Map;

public interface CaffeineCache<K, V> extends Cache<K, V> {

    /**
     * @return all values and keys
     */
    @Nonnull
    Map<K, V> getAll();
}
