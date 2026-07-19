package io.koraframework.resilient.circuitbreaker.telemetry;

import io.koraframework.resilient.circuitbreaker.CircuitBreakerConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public final class CircuitBreakerOperationTelemetryConfig implements CircuitBreakerTelemetryConfig {

    private final CircuitBreakerLoggingConfig logging;
    private final CircuitBreakerMetricsConfig metrics;
    private final CircuitBreakerTracingConfig tracing;

    public CircuitBreakerOperationTelemetryConfig(CircuitBreakerTelemetryConfig global, CircuitBreakerConfig.@Nullable TelemetryConfig operation) {
        this.logging = new OperationLoggingConfig(global.logging(), operation == null ? null : operation.logging());
        this.metrics = new OperationMetricsConfig(global.metrics(), operation == null ? null : operation.metrics());
        this.tracing = new OperationTracingConfig(global.tracing(), operation == null ? null : operation.tracing());
    }

    @Override
    public CircuitBreakerLoggingConfig logging() {
        return this.logging;
    }

    @Override
    public CircuitBreakerMetricsConfig metrics() {
        return this.metrics;
    }

    @Override
    public CircuitBreakerTracingConfig tracing() {
        return this.tracing;
    }

    private record OperationLoggingConfig(io.koraframework.telemetry.common.TelemetryConfig.LoggingConfig global,
                                          CircuitBreakerConfig.TelemetryConfig.@Nullable LoggingConfig operation) implements CircuitBreakerLoggingConfig {
        @Override
        public boolean enabled() {
            if (this.operation != null && this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.global.enabled();
        }
    }

    private record OperationMetricsConfig(io.koraframework.telemetry.common.TelemetryConfig.MetricsConfig global,
                                          CircuitBreakerConfig.TelemetryConfig.@Nullable MetricsConfig operation) implements CircuitBreakerMetricsConfig {
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
                                          CircuitBreakerConfig.TelemetryConfig.@Nullable TracingConfig operation) implements CircuitBreakerTracingConfig {
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
