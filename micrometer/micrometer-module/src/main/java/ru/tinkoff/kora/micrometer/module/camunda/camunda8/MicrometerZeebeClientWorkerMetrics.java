package ru.tinkoff.kora.micrometer.module.camunda.camunda8;

import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import java.util.List;

public final class MicrometerZeebeClientWorkerMetrics implements JobWorkerMetrics {

    private final Counter activated;
    private final Counter handled;

    public MicrometerZeebeClientWorkerMetrics(MeterRegistry registry, String jobType) {
        this.activated = registry.counter("zeebe.client.worker.job",
            List.of(
                Tag.of("action", "activated"),
                Tag.of("type", jobType)
            ));

        this.handled = registry.counter("zeebe.client.worker.job",
            List.of(
                Tag.of("action", "handled"),
                Tag.of("type", jobType)
            ));
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
