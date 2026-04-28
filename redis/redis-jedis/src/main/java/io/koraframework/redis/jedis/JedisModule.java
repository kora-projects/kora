package io.koraframework.redis.jedis;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.util.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.redis.jedis.telemetry.DefaultJedisMetricsFactory;
import io.koraframework.redis.jedis.telemetry.DefaultJedisTelemetryFactory;
import io.koraframework.redis.jedis.telemetry.JedisTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.*;

public interface JedisModule {

    default JedisConfig jedisConfig(Config config, ConfigValueExtractor<JedisConfig> extractor) {
        var value = config.get("jedis");
        return extractor.extract(value);
    }

    @DefaultComponent
    default JedisTelemetryFactory jedisClientTelemetryFactory(@Nullable Tracer tracer,
                                                              @Nullable MeterRegistry meterRegistry,
                                                              @Nullable DefaultJedisMetricsFactory metricsFactory) {
        return new DefaultJedisTelemetryFactory(tracer, meterRegistry, metricsFactory);
    }

    @DefaultComponent
    default JedisFactory jedisFactory(@Nullable Configurer<DefaultJedisClientConfig.Builder> configConfigurer,
                                      @Nullable Configurer<ConnectionPoolConfig> poolConfigConfigurer,
                                      @Nullable Configurer<RedisClient.Builder> standaloneConfigurer,
                                      @Nullable Configurer<RedisClusterClient.Builder> clusterConfigurer) {
        return new JedisFactory(configConfigurer, poolConfigConfigurer, standaloneConfigurer, clusterConfigurer);
    }

    @DefaultComponent
    default UnifiedJedis jedisClient(JedisFactory jedisFactory,
                                     JedisConfig config) {
        return jedisFactory.build(config);
    }
}
