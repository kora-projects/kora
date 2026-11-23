package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.HookContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopOpenfeatureLoggerFactory extends DefaultOpenfeatureLoggerFactory {

    public static final NoopOpenfeatureLoggerFactory INSTANCE = new NoopOpenfeatureLoggerFactory();

    private NoopOpenfeatureLoggerFactory() {}

    @Override
    public DefaultOpenfeatureLogger create(DefaultOpenfeatureTelemetry.TelemetryContext context) {
        return NoopOpenfeatureLogger.INSTANCE;
    }

    public static final class NoopOpenfeatureLogger extends DefaultOpenfeatureLogger {

        public static final NoopOpenfeatureLogger INSTANCE = new NoopOpenfeatureLogger();

        private NoopOpenfeatureLogger() {
            super(DefaultOpenfeatureTelemetry.TelemetryContext.EMPTY, NOPLogger.NOP_LOGGER);
        }

        @Override
        public void logEvaluation(HookContext<?> evaluation) {}

        @Override
        public void logResult(HookContext<?> evaluation, FlagEvaluationDetails<?> result, long processingTimeNanos) {}

        @Override
        public void logError(HookContext<?> evaluation, @Nullable FlagEvaluationDetails<?> result,
                             @Nullable Throwable exception, long processingTimeNanos) {}
    }
}
