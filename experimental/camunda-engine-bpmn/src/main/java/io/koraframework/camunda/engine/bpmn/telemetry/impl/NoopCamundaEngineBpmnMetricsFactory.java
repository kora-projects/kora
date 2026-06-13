package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import org.jspecify.annotations.Nullable;

public final class NoopCamundaEngineBpmnMetricsFactory extends DefaultCamundaEngineBpmnMetricsFactory {

    public static final NoopCamundaEngineBpmnMetricsFactory INSTANCE = new NoopCamundaEngineBpmnMetricsFactory();

    private NoopCamundaEngineBpmnMetricsFactory() {}

    @Override
    public DefaultCamundaEngineBpmnMetrics create(DefaultCamundaEngineBpmnTelemetry.TelemetryContext context, String javaDelegateName) {
        return NoopCamundaEngineBpmnMetrics.INSTANCE;
    }

    public static final class NoopCamundaEngineBpmnMetrics extends DefaultCamundaEngineBpmnMetrics {

        public static final NoopCamundaEngineBpmnMetrics INSTANCE = new NoopCamundaEngineBpmnMetrics();

        private NoopCamundaEngineBpmnMetrics() {
            super(null, "none");
        }

        @Override
        public void record(@Nullable Throwable throwable, long processingTimeNanos) {

        }
    }
}
