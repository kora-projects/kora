package io.koraframework.cache.caffeine.telemetry;

import io.koraframework.common.telemetry.Observation;

import java.util.Collection;
import java.util.Map;

public interface CaffeineCacheObservation extends Observation {

    void observeKey(Object key);

    void observeValue(Object value);

    void observeKeys(Collection<?> keys);

    void observeValues(Map<?, ?> keyToValue);
}
