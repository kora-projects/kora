package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.AbstractRedisClient;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

public interface LettuceModule {

    default LettuceConfig lettuceConfig(Config config, ConfigValueExtractor<LettuceConfig> extractor) {
        var value = config.get("lettuce");
        return extractor.extract(value);
    }

    default AbstractRedisClient lettuceClient(LettuceConfig config) {
        return LettuceFactory.build(config);
    }
}
