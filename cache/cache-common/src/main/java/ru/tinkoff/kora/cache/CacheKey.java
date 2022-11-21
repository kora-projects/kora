package ru.tinkoff.kora.cache;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Represent CacheKey interface that is used by the implementation that represents method arguments as key for Cache
 *
 * @see #toString() generates string where all values are separated with '-' according to contract for CacheKey
 */
public interface CacheKey {

    /**
     * @return cache key values arguments that are subjected for Cache key
     */
    @Nonnull
    List<Object> values();
}
