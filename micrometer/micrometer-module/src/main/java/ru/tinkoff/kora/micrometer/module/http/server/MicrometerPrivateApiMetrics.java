package ru.tinkoff.kora.micrometer.module.http.server;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import ru.tinkoff.kora.http.server.common.telemetry.PrivateApiMetrics;

public final class MicrometerPrivateApiMetrics implements PrivateApiMetrics {
    private final PrometheusMeterRegistry meterRegistry;

    public MicrometerPrivateApiMetrics(PrometheusMeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String scrape() {
        return this.meterRegistry.scrape();
    }
}
