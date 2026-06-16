package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import org.jspecify.annotations.Nullable;

public final class NoopZeebeWorkerMetricsFactory extends DefaultZeebeWorkerMetricsFactory {

    public static final NoopZeebeWorkerMetricsFactory INSTANCE = new NoopZeebeWorkerMetricsFactory();

    private NoopZeebeWorkerMetricsFactory() {}

    @Override
    public DefaultZeebeWorkerMetrics create(DefaultZeebeWorkerTelemetry.TelemetryContext context) {
        return NoopZeebeWorkerMetrics.INSTANCE;
    }

    public static final class NoopZeebeWorkerMetrics extends DefaultZeebeWorkerMetrics {

        public static final NoopZeebeWorkerMetrics INSTANCE = new NoopZeebeWorkerMetrics();

        private NoopZeebeWorkerMetrics() {
            super(DefaultZeebeWorkerTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void record(ActivatedJob job, @Nullable Throwable error, boolean failedByUser, long processingTimeNanos) {

        }
    }
}
