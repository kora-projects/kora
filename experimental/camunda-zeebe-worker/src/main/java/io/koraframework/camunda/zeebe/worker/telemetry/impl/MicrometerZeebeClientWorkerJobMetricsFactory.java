package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.client.api.worker.JobWorkerMetrics;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeClientWorkerMetricsFactory;
import io.koraframework.telemetry.common.TelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

public final class MicrometerZeebeClientWorkerJobMetricsFactory implements ZeebeClientWorkerMetricsFactory {

    @Nullable
    private final MeterRegistry meterRegistry;

    public MicrometerZeebeClientWorkerJobMetricsFactory(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Nullable
    @Override
    public JobWorkerMetrics get(String jobType, TelemetryConfig.MetricsConfig config) {
        if (config.enabled() && this.meterRegistry != null) {
            var tags = new ArrayList<Tag>(config.tags().size() + 1);
            for (var entry : config.tags().entrySet()) {
                tags.add(Tag.of(entry.getKey(), entry.getValue()));
            }
            tags.add(Tag.of("type", jobType));
            return JobWorkerMetrics.micrometer()
                .withMeterRegistry(this.meterRegistry)
                .withTags(tags)
                .build();
        } else {
            return null;
        }
    }
}
