package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.HookContext;
import org.jspecify.annotations.Nullable;

public final class NoopOpenfeatureMetricsFactory extends DefaultOpenfeatureMetricsFactory {

    public static final NoopOpenfeatureMetricsFactory INSTANCE = new NoopOpenfeatureMetricsFactory();

    private NoopOpenfeatureMetricsFactory() {}

    @Override
    public DefaultOpenfeatureMetrics create(DefaultOpenfeatureTelemetry.TelemetryContext context) {
        return NoopOpenfeatureMetrics.INSTANCE;
    }

    public static final class NoopOpenfeatureMetrics extends DefaultOpenfeatureMetrics {

        public static final NoopOpenfeatureMetrics INSTANCE = new NoopOpenfeatureMetrics();

        private NoopOpenfeatureMetrics() {
            super(DefaultOpenfeatureTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordFailure(HookContext<?> evaluation, @Nullable FlagEvaluationDetails<?> result,
                                  @Nullable Throwable exception, long processingTimeNanos) {}

        @Override
        public void recordSuccess(HookContext<?> evaluation, FlagEvaluationDetails<?> result, long processingTimeNanos) {}
    }
}
