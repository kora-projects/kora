package ru.tinkoff.kora.redis.jedis;

import redis.clients.jedis.UnifiedJedis;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

public interface JedisModule {

    default JedisConfig jedisConfig(Config config, ConfigValueExtractor<JedisConfig> extractor) {
        var value = config.get("jedis");
        return extractor.extract(value);
    }

    default UnifiedJedis jedisClient(JedisConfig config) {
        return JedisFactory.build(config);
    }
}
