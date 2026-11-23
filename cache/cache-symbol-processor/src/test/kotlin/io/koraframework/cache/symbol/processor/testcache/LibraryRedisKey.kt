package io.koraframework.cache.symbol.processor.testcache

import io.koraframework.cache.redis.mapper.RedisCacheKeyMapper
import io.koraframework.common.annotation.DefaultComponent
import io.koraframework.common.annotation.Module

/**
 * Key type of a library that already ships [`$LibraryRedisKey_RedisCacheKeyMapperModule`].
 */
data class LibraryRedisKey(val tenant: String, val id: String)

/**
 * Stands for the key mapper module generated in the library's own compilation, so it is on the classpath of the app.
 */
@Module
interface `$LibraryRedisKey_RedisCacheKeyMapperModule` {
    @DefaultComponent
    fun libraryRedisKey_RedisKeyMapper(keyMapper1: RedisCacheKeyMapper<String>, keyMapper2: RedisCacheKeyMapper<String>): RedisCacheKeyMapper<LibraryRedisKey> =
        RedisCacheKeyMapper { key -> keyMapper2.apply(key!!.id) }
}
