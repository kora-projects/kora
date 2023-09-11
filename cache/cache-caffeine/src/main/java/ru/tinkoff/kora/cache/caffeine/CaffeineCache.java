package ru.tinkoff.kora.cache.caffeine;

import ru.tinkoff.kora.cache.Cache;

import javax.annotation.Nonnull;
import java.util.Map;

public interface CaffeineCache<K, V> extends Cache<K, V> {

    /**
     * @return all values and keys
     */
    @Nonnull
    Map<K, V> getAll();
}
