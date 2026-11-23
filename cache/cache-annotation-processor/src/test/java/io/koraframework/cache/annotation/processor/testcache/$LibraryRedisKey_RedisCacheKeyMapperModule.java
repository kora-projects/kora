package io.koraframework.cache.annotation.processor.testcache;

import io.koraframework.cache.redis.mapper.RedisCacheKeyMapper;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Module;

/**
 * Stands for the key mapper module generated in the library's own compilation, so it is on the classpath of the app.
 */
@Module
public interface $LibraryRedisKey_RedisCacheKeyMapperModule {
    @DefaultComponent
    default RedisCacheKeyMapper<LibraryRedisKey> libraryRedisKey_RedisKeyMapper(RedisCacheKeyMapper<String> keyMapper1, RedisCacheKeyMapper<String> keyMapper2) {
        return key -> keyMapper2.apply(key.id());
    }
}
