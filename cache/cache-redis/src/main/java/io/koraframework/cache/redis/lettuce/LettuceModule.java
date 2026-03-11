package io.koraframework.cache.redis.lettuce;

import io.lettuce.core.cluster.RedisClusterClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.EventLoopGroup;
import org.jspecify.annotations.Nullable;
import io.koraframework.cache.redis.RedisCacheClient;
import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

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
                                                @Nullable MeterRegistry meterRegistry,
                                                @Nullable LettuceConfigurator lettuceConfigurator,
                                                @Nullable EventLoopGroup eventLoopGroup) {
        var redisClient = factory.build(config, meterRegistry, lettuceConfigurator, eventLoopGroup);
        if (redisClient instanceof io.lettuce.core.RedisClient rc) {
            return new LettuceRedisCacheClient(rc, factory, config);
        } else if (redisClient instanceof RedisClusterClient rcc) {
            return new LettuceClusterRedisCacheClient(rcc, config);
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }
}
