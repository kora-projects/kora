package ru.tinkoff.kora.cache.redis.client;

import ru.tinkoff.kora.common.DefaultComponent;
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

    default LettuceCommander lettuceCommander(LettuceClientFactory factory, LettuceClientConfig config) {
        return new DefaultLettuceCommander(factory.build(config));
    }

    @DefaultComponent
    default SyncRedisClient lettuceCacheRedisClient(LettuceCommander commands) {
        return new LettuceSyncRedisClient(commands);
    }

    @DefaultComponent
    default ReactiveRedisClient lettuceReactiveCacheRedisClient(LettuceCommander commands) {
        return new LettuceReactiveRedisClient(commands);
    }
}
