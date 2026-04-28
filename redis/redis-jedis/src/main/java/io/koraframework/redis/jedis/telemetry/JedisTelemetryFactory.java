package io.koraframework.redis.jedis.telemetry;

public interface JedisTelemetryFactory {

    JedisTelemetry get(JedisTelemetryConfig telemetryConfig);
}
