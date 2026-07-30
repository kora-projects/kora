package io.koraframework.resilient.retry.telemetry;

import io.koraframework.resilient.retry.RetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public final class RetryOperationTelemetryConfig implements RetryTelemetryConfig {

    private final RetryLoggingConfig logging;
    private final RetryMetricsConfig metrics;
    private final RetryTracingConfig tracing;

    public RetryOperationTelemetryConfig(RetryTelemetryConfig global, RetryConfig.@Nullable TelemetryConfig operation) {
        this.logging = new OperationLoggingConfig(global.logging(), operation == null ? null : operation.logging());
        this.metrics = new OperationMetricsConfig(global.metrics(), operation == null ? null : operation.metrics());
        this.tracing = new OperationTracingConfig(global.tracing(), operation == null ? null : operation.tracing());
    }

    @Override
    public RetryLoggingConfig logging() {
        return this.logging;
    }

    @Override
    public RetryMetricsConfig metrics() {
        return this.metrics;
    }

    @Override
    public RetryTracingConfig tracing() {
        return this.tracing;
    }

    private record OperationLoggingConfig(io.koraframework.telemetry.common.TelemetryConfig.LoggingConfig global,
                                          RetryConfig.TelemetryConfig.@Nullable LoggingConfig operation) implements RetryLoggingConfig {
        @Override
        public boolean enabled() {
            if (this.operation != null && this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.global.enabled();
        }
    }

    private record OperationMetricsConfig(io.koraframework.telemetry.common.TelemetryConfig.MetricsConfig global,
                                          RetryConfig.TelemetryConfig.@Nullable MetricsConfig operation) implements RetryMetricsConfig {
        @Override
        public boolean enabled() {
            if (this.operation != null && this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.global.enabled();
        }

        @Override
        public Duration[] slo() {
            if (this.operation != null && this.operation.slo() != null) {
                return this.operation.slo();
            }
            return this.global.slo();
        }

        @Override
        public Map<String, String> tags() {
            if (this.operation != null && this.operation.tags() != null) {
                return this.operation.tags();
            }
            return this.global.tags();
        }
    }

    private record OperationTracingConfig(io.koraframework.telemetry.common.TelemetryConfig.TracingConfig global,
                                          RetryConfig.TelemetryConfig.@Nullable TracingConfig operation) implements RetryTracingConfig {
        @Override
        public boolean enabled() {
            if (this.operation != null && this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.global.enabled();
        }

        @Override
        public Map<String, String> attributes() {
            if (this.operation != null && this.operation.attributes() != null) {
                return this.operation.attributes();
            }
            return this.global.attributes();
        }
    }
}
