package ru.tinkoff.kora.cache;

import java.util.function.Function;

/**
 * Contract for converting method arguments {@link CacheKey} into the final key that will be used in Cache implementation.
 */
public interface CacheKeyMapper<K, C> extends Function<C, K> {

}
