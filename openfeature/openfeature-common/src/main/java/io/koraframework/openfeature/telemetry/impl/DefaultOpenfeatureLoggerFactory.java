package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.HookContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOpenfeatureLoggerFactory {

    public static final DefaultOpenfeatureLoggerFactory INSTANCE = new DefaultOpenfeatureLoggerFactory();

    public DefaultOpenfeatureLogger create(DefaultOpenfeatureTelemetry.TelemetryContext context) {
        return new DefaultOpenfeatureLogger(context, LoggerFactory.getLogger(context.clientCanonicalName() + ".evaluation"));
    }

    public static class DefaultOpenfeatureLogger {

        protected final DefaultOpenfeatureTelemetry.TelemetryContext context;
        protected final Logger logger;

        public DefaultOpenfeatureLogger(DefaultOpenfeatureTelemetry.TelemetryContext context, Logger logger) {
            this.context = context;
            this.logger = logger;
        }

        public void logEvaluation(HookContext<?> evaluation) {
            this.logger.atDebug()
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .addKeyValue("flagKey", evaluation.getFlagKey())
                .addKeyValue("flagType", evaluation.getType())
                .addKeyValue("providerName", evaluation.getProviderMetadata().getName())
                .log("OpenFeature evaluation started");
        }

        public void logResult(HookContext<?> evaluation, FlagEvaluationDetails<?> result, long processingTimeNanos) {
            this.logger.atInfo()
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .addKeyValue("flagKey", evaluation.getFlagKey())
                .addKeyValue("providerName", evaluation.getProviderMetadata().getName())
                .addKeyValue("variant", result.getVariant())
                .addKeyValue("reason", result.getReason())
                .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                .log("OpenFeature evaluation completed");
        }

        public void logError(HookContext<?> evaluation,
                             @Nullable FlagEvaluationDetails<?> result,
                             @Nullable Throwable exception,
                             long processingTimeNanos) {
            this.logger.atWarn()
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .addKeyValue("flagKey", evaluation.getFlagKey())
                .addKeyValue("providerName", evaluation.getProviderMetadata().getName())
                .addKeyValue("errorType", DefaultOpenfeatureMetricsFactory.errorType(result, exception))
                .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                .log("OpenFeature evaluation failed");
        }
    }
}
