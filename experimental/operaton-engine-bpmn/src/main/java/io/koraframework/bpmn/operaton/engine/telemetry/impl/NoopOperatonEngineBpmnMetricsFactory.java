package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import org.jspecify.annotations.Nullable;

public final class NoopOperatonEngineBpmnMetricsFactory extends DefaultOperatonEngineBpmnMetricsFactory {

    public static final NoopOperatonEngineBpmnMetricsFactory INSTANCE = new NoopOperatonEngineBpmnMetricsFactory();

    private NoopOperatonEngineBpmnMetricsFactory() {}

    @Override
    public DefaultOperatonEngineBpmnMetrics create(DefaultOperatonEngineBpmnTelemetry.TelemetryContext context, String javaDelegateName) {
        return NoopOperatonEngineBpmnMetrics.INSTANCE;
    }

    public static final class NoopOperatonEngineBpmnMetrics extends DefaultOperatonEngineBpmnMetrics {

        public static final NoopOperatonEngineBpmnMetrics INSTANCE = new NoopOperatonEngineBpmnMetrics();

        private NoopOperatonEngineBpmnMetrics() {
            super(null, "none");
        }

        @Override
        public void record(@Nullable Throwable throwable, long processingTimeNanos) {

        }
    }
}
