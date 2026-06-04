package ru.tinkoff.kora.micrometer.module.camunda.zeebe.job;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.ArrayList;
import java.util.List;

public final class MicrometerZeebeClientWorkerJobMetrics implements JobWorkerMetrics {

    private final Counter activated;
    private final Counter handled;

    public MicrometerZeebeClientWorkerJobMetrics(MeterRegistry registry, String jobType, TelemetryConfig.MetricsConfig config) {
        var activatedTags = new ArrayList<Tag>();
        activatedTags.add(Tag.of("action", "activated"));
        activatedTags.add(Tag.of("type", jobType));
        config.tags().forEach((k, v) -> activatedTags.add(Tag.of(k, v)));

        this.activated = registry.counter("zeebe.client.worker.job", activatedTags);

        var handledTags = new ArrayList<Tag>();
        handledTags.add(Tag.of("action", "handled"));
        handledTags.add(Tag.of("type", jobType));
        config.tags().forEach((k, v) -> handledTags.add(Tag.of(k, v)));

        this.handled = registry.counter("zeebe.client.worker.job", handledTags);
    }

    @Override
    public void jobActivated(int count) {
        this.activated.increment(count);
    }

    @Override
    public void jobHandled(int count) {
        this.handled.increment(count);
    }
}
