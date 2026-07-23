package io.koraframework.resilient.timeout.telemetry;

import io.koraframework.resilient.timeout.TimeoutConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public final class TimeoutOperationTelemetryConfig implements TimeoutTelemetryConfig {

    private final TimeoutLoggingConfig logging;
    private final TimeoutMetricsConfig metrics;
    private final TimeoutTracingConfig tracing;

    public TimeoutOperationTelemetryConfig(TimeoutTelemetryConfig global, TimeoutConfig.@Nullable TelemetryConfig operation) {
        this.logging = new OperationLoggingConfig(global.logging(), operation == null ? null : operation.logging());
        this.metrics = new OperationMetricsConfig(global.metrics(), operation == null ? null : operation.metrics());
        this.tracing = new OperationTracingConfig(global.tracing(), operation == null ? null : operation.tracing());
    }

    @Override
    public TimeoutLoggingConfig logging() {
        return this.logging;
    }

    @Override
    public TimeoutMetricsConfig metrics() {
        return this.metrics;
    }

    @Override
    public TimeoutTracingConfig tracing() {
        return this.tracing;
    }

    private record OperationLoggingConfig(io.koraframework.telemetry.common.TelemetryConfig.LoggingConfig global,
                                          TimeoutConfig.TelemetryConfig.@Nullable LoggingConfig operation) implements TimeoutLoggingConfig {
        @Override
        public boolean enabled() {
            if (this.operation != null && this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.global.enabled();
        }
    }

    private record OperationMetricsConfig(io.koraframework.telemetry.common.TelemetryConfig.MetricsConfig global,
                                          TimeoutConfig.TelemetryConfig.@Nullable MetricsConfig operation) implements TimeoutMetricsConfig {
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
                                          TimeoutConfig.TelemetryConfig.@Nullable TracingConfig operation) implements TimeoutTracingConfig {
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
