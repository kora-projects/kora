package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.cache.redis.lettuce.LettuceModule;

/**
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce AND LettuceCacheModule
 */
@Deprecated
public interface RedisCacheModule extends RedisCacheMapperModule, LettuceModule {

}
