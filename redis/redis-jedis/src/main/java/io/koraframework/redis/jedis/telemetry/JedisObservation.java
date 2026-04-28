package io.koraframework.redis.jedis.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface JedisObservation extends Observation {

    void observeResult(Object result);
}
