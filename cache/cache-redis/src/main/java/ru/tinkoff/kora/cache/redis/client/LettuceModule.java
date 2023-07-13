package ru.tinkoff.kora.cache.redis.client;

import io.lettuce.core.AbstractRedisClient;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

public interface LettuceModule {

    default LettuceClientConfig lettuceConfig(Config config, ConfigValueExtractor<LettuceClientConfig> extractor) {
        var value = config.get("lettuce");
        return extractor.extract(value);
    }

    default LettuceClientFactory lettuceClientFactory() {
        return new LettuceClientFactory();
    }

    default AbstractRedisClient lettuceRedisClient(LettuceClientFactory factory, LettuceClientConfig config) {
        return factory.build(config);
    }

    default LettuceCommander lettuceCommander(AbstractRedisClient redisClient) {
        return new DefaultLettuceCommander(redisClient);
    }

    default SyncRedisClient lettuceCacheRedisClient(LettuceCommander commands) {
        return new LettuceSyncRedisClient(commands);
    }

    default ReactiveRedisClient lettuceReactiveCacheRedisClient(LettuceCommander commands) {
        return new LettuceReactiveRedisClient(commands);
    }
}
