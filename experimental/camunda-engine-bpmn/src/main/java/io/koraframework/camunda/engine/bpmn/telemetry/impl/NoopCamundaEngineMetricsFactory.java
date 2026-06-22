package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import org.jspecify.annotations.Nullable;

public final class NoopCamundaEngineMetricsFactory extends DefaultCamundaEngineMetricsFactory {

    public static final NoopCamundaEngineMetricsFactory INSTANCE = new NoopCamundaEngineMetricsFactory();

    private NoopCamundaEngineMetricsFactory() {}

    @Override
    public DefaultCamundaEngineMetrics create(DefaultCamundaEngineTelemetry.TelemetryContext context, String javaDelegateName) {
        return NoopCamundaEngineMetrics.INSTANCE;
    }

    public static final class NoopCamundaEngineMetrics extends DefaultCamundaEngineMetrics {

        public static final NoopCamundaEngineMetrics INSTANCE = new NoopCamundaEngineMetrics();

        private NoopCamundaEngineMetrics() {
            super(null, "none");
        }

        @Override
        public void record(@Nullable Throwable throwable, long processingTimeNanos) {

        }
    }
}
