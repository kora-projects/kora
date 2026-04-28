package io.koraframework.redis.jedis;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.redis.jedis.telemetry.JedisTelemetryConfig;

@ConfigValueExtractor
public interface JedisClientConfig {

    /**
     * Gets the telemetry configuration for the Jedis client
     *
     * @return the telemetry configuration
     */
    JedisTelemetryConfig telemetry();
}
