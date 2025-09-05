package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.cluster.RedisClusterClient;
import io.netty.channel.EventLoopGroup;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.cache.redis.lettuce.telemetry.CommandLatencyRecorderFactory;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

public interface LettuceModule {

    default LettuceClientConfig lettuceConfig(Config config, ConfigValueExtractor<LettuceClientConfig> extractor) {
        var value = config.get("lettuce");
        return extractor.extract(value);
    }

    @DefaultComponent
    default LettuceClientFactory lettuceClientFactory() {
        return new LettuceClientFactory();
    }

    @DefaultComponent
    default RedisCacheClient lettuceRedisClient(LettuceClientFactory factory,
                                                LettuceClientConfig config,
                                                @Nullable CommandLatencyRecorderFactory recorderFactory,
                                                @Nullable LettuceConfigurator lettuceConfigurator,
                                                @Nullable EventLoopGroup eventLoopGroup) {
        var redisClient = factory.build(config, recorderFactory, lettuceConfigurator, eventLoopGroup);
        if (redisClient instanceof io.lettuce.core.RedisClient rc) {
            return new LettuceRedisCacheClient(rc, factory, config);
        } else if (redisClient instanceof RedisClusterClient rcc) {
            return new LettuceClusterRedisCacheClient(rcc);
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }
}
