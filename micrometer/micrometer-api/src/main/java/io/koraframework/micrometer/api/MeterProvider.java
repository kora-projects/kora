package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;

@FunctionalInterface
public interface MeterProvider<T extends Meter> {

    T get(Tags tags);
}
