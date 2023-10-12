package ru.tinkoff.kora.cache.redis.lettuce;

import ru.tinkoff.kora.cache.redis.RedisCacheClient;
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

    @DefaultComponent
    default RedisCacheClient lettuceRedisClient(LettuceClientFactory factory, LettuceClientConfig config) {
        return new LettuceRedisCacheClient(factory.build(config));
    }
}
