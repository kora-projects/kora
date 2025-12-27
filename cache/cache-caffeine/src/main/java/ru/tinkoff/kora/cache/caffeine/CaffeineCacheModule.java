package ru.tinkoff.kora.cache.caffeine;

import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;

public interface CaffeineCacheModule {

    @DefaultComponent
    default CaffeineCacheFactory caffeineCacheFactory(@Nullable MeterRegistry meterRegistry) {
        return new DefaultCaffeineCacheFactory(meterRegistry);
    }

}
