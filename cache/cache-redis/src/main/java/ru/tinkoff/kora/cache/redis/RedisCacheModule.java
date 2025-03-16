package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.cache.redis.lettuce.LettuceModule;

/**
 * This module is no longer maintained, it was replaced with new one.
 * <p>
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce AND LettuceCacheModule
 * <p>
 * Check documentation for more information
 */
@Deprecated
public interface RedisCacheModule extends RedisCacheMapperModule, LettuceModule {

}
