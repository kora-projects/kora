package io.koraframework.cache.redis.lettuce;

import io.koraframework.cache.redis.RedisCacheClient;
import io.koraframework.common.DefaultComponent;
import io.koraframework.redis.lettuce.LettuceConfig;
import io.koraframework.redis.lettuce.LettuceFactory;
import io.koraframework.redis.lettuce.LettuceModule;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;

import java.util.List;

public interface LettuceCacheModule extends LettuceModule {

    @DefaultComponent
    default RedisCacheClient lettuceRedisCacheClient(AbstractRedisClient redisClient,
                                                     LettuceFactory factory,
                                                     LettuceConfig config) {
        if (redisClient instanceof RedisClient rc) {
            final List<RedisURI> redisURIs = factory.buildRedisURI(config);
            var redisURI = redisURIs.size() == 1 ? redisURIs.getFirst() : null;
            return new LettuceStandaloneCacheClient(rc, redisURI);
        } else if (redisClient instanceof RedisClusterClient rcc) {
            return new LettuceClusterCacheClient(rcc);
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }
}
