package ru.tinkoff.kora.cache.redis.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;

import java.util.Collection;
import java.util.Map;

public interface RedisCacheObservation extends Observation {
    void observeKey(Object key);

    void observeValue(Object value);

    void observeKeys(Collection<?> keys);

    void observeValues(Map<?, ?> keyToValue);
}
