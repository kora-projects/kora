package ru.tinkoff.kora.cache.caffeine;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;

public interface CaffeineCacheModule {

    @DefaultComponent
    default CaffeineCacheFactory caffeineCacheFactory(@Nullable MeterRegistry meterRegistry) {
        return new DefaultCaffeineCacheFactory(meterRegistry);
    }

}
