package io.koraframework.cache.caffeine;

import io.koraframework.common.DefaultComponent;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;

public interface CaffeineCacheModule {

    @DefaultComponent
    default CaffeineCacheFactory caffeineCacheFactory(@Nullable MeterRegistry meterRegistry) {
        return new DefaultCaffeineCacheFactory(meterRegistry);
    }

}
