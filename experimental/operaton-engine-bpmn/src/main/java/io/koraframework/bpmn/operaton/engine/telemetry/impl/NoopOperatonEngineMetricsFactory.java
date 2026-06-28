package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import org.jspecify.annotations.Nullable;

public final class NoopOperatonEngineMetricsFactory extends DefaultOperatonEngineMetricsFactory {

    public static final NoopOperatonEngineMetricsFactory INSTANCE = new NoopOperatonEngineMetricsFactory();

    private NoopOperatonEngineMetricsFactory() {}

    @Override
    public DefaultOperatonEngineMetrics create(DefaultOperatonEngineTelemetry.TelemetryContext context, String javaDelegateName) {
        return NoopOperatonEngineMetrics.INSTANCE;
    }

    public static final class NoopOperatonEngineMetrics extends DefaultOperatonEngineMetrics {

        public static final NoopOperatonEngineMetrics INSTANCE = new NoopOperatonEngineMetrics();

        private NoopOperatonEngineMetrics() {
            super(null, "none");
        }

        @Override
        public void record(@Nullable Throwable throwable, long processingTimeNanos) {

        }
    }
}
