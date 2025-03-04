package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.RedisClusterURIUtil;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.cache.redis.RedisCacheModule;
import ru.tinkoff.kora.redis.lettuce.LettuceConfig;
import ru.tinkoff.kora.redis.lettuce.LettuceModule;

import java.net.URI;
import java.util.List;

public interface LettuceCacheModule extends RedisCacheModule, LettuceModule {

    default RedisCacheAsyncClient lettuceRedisCacheAsyncClient(AbstractRedisClient redisClient,
                                                               RedisClusterAsyncCommands<byte[], byte[]> lettuceCommands,
                                                               LettuceConfig lettuceConfig) {
        if (redisClient instanceof io.lettuce.core.RedisClient rc) {
            final Integer database = lettuceConfig.database();
            final String user = lettuceConfig.user();
            final String password = lettuceConfig.password();

            final List<RedisURI> redisURIs = lettuceConfig.uri().stream()
                .flatMap(uri -> RedisClusterURIUtil.toRedisURIs(URI.create(uri)).stream())
                .map(redisURI -> {
                    RedisURI.Builder builder = RedisURI.builder(redisURI);
                    if (database != null) {
                        builder = builder.withDatabase(database);
                    }
                    if (user != null && password != null) {
                        builder = builder.withAuthentication(user, password);
                    } else if (password != null) {
                        builder = builder.withPassword(((CharSequence) password));
                    }

                    return builder
                        .withTimeout(lettuceConfig.commandTimeout())
                        .build();
                })
                .toList();

            var redisURI = redisURIs.size() == 1 ? redisURIs.get(0) : null;
            return new LettuceSingleCacheAsyncClient(rc, lettuceCommands, redisURI);
        } else if (redisClient instanceof RedisClusterClient rcc) {
            return new LettuceClusterCacheAsyncClient(rcc, lettuceCommands);
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }

    default RedisCacheClient lettuceRedisCacheSyncClient(RedisCacheAsyncClient redisCacheAsyncClient) {
        return new LettuceCacheSyncClient(redisCacheAsyncClient);
    }
}
