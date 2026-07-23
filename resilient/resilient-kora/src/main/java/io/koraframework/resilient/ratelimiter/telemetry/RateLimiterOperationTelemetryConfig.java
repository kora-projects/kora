package io.koraframework.resilient.ratelimiter.telemetry;

import io.koraframework.resilient.ratelimiter.RateLimiterConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public final class RateLimiterOperationTelemetryConfig implements RateLimiterTelemetryConfig {

    private final RateLimiterLoggingConfig logging;
    private final RateLimiterMetricsConfig metrics;
    private final RateLimiterTracingConfig tracing;

    public RateLimiterOperationTelemetryConfig(RateLimiterTelemetryConfig global, RateLimiterConfig.@Nullable TelemetryConfig operation) {
        this.logging = new OperationLoggingConfig(global.logging(), operation == null ? null : operation.logging());
        this.metrics = new OperationMetricsConfig(global.metrics(), operation == null ? null : operation.metrics());
        this.tracing = new OperationTracingConfig(global.tracing(), operation == null ? null : operation.tracing());
    }

    @Override
    public RateLimiterLoggingConfig logging() {
        return this.logging;
    }

    @Override
    public RateLimiterMetricsConfig metrics() {
        return this.metrics;
    }

    @Override
    public RateLimiterTracingConfig tracing() {
        return this.tracing;
    }

    private record OperationLoggingConfig(io.koraframework.telemetry.common.TelemetryConfig.LoggingConfig global,
                                          RateLimiterConfig.TelemetryConfig.@Nullable LoggingConfig operation) implements RateLimiterLoggingConfig {
        @Override
        public boolean enabled() {
            if (this.operation != null && this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.global.enabled();
        }
    }

    private record OperationMetricsConfig(io.koraframework.telemetry.common.TelemetryConfig.MetricsConfig global,
                                          RateLimiterConfig.TelemetryConfig.@Nullable MetricsConfig operation) implements RateLimiterMetricsConfig {
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
                                          RateLimiterConfig.TelemetryConfig.@Nullable TracingConfig operation) implements RateLimiterTracingConfig {
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
