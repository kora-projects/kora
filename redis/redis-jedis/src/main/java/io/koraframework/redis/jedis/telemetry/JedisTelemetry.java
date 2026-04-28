package io.koraframework.redis.jedis.telemetry;

import redis.clients.jedis.CommandObject;

public interface JedisTelemetry {

    /**
     * Creates an observation for a Jedis command execution
     *
     * @param commandObject the Redis command object being executed
     * @return the observation for the command execution
     */
    <T> JedisObservation observe(CommandObject<T> commandObject);
}
