package io.koraframework.redis.jedis.telemetry;

import redis.clients.jedis.CommandObject;

public final class NoopJedisTelemetry implements JedisTelemetry {

    public static final NoopJedisTelemetry INSTANCE = new NoopJedisTelemetry();

    private NoopJedisTelemetry() { }

    @Override
    public <T> JedisObservation observe(CommandObject<T> commandObject) {
        return NoopJedisObservation.INSTANCE;
    }
}
