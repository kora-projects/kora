package io.koraframework.resilient.bulkhead.telemetry;

import io.koraframework.resilient.bulkhead.BulkheadConfig;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class BulkheadOperationTelemetryConfig implements BulkheadTelemetryConfig {

    private final BulkheadLoggingConfig logging;
    private final BulkheadMetricsConfig metrics;
    private final BulkheadTracingConfig tracing;

    public BulkheadOperationTelemetryConfig(BulkheadTelemetryConfig global, BulkheadConfig.@Nullable TelemetryConfig operation) {
        this.logging = new OperationLoggingConfig(global.logging(), operation == null ? null : operation.logging());
        this.metrics = new OperationMetricsConfig(global.metrics(), operation == null ? null : operation.metrics());
        this.tracing = new OperationTracingConfig(global.tracing(), operation == null ? null : operation.tracing());
    }

    @Override
    public BulkheadLoggingConfig logging() {
        return this.logging;
    }

    @Override
    public BulkheadMetricsConfig metrics() {
        return this.metrics;
    }

    @Override
    public BulkheadTracingConfig tracing() {
        return this.tracing;
    }

    private record OperationLoggingConfig(
        io.koraframework.telemetry.common.TelemetryConfig.LoggingConfig global,
        BulkheadConfig.TelemetryConfig.@Nullable LoggingConfig operation
    ) implements BulkheadLoggingConfig {

        @Override
        public boolean enabled() {
            if (this.operation != null && this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.global.enabled();
        }
    }

    private record OperationMetricsConfig(
        io.koraframework.telemetry.common.TelemetryConfig.MetricsConfig global,
        BulkheadConfig.TelemetryConfig.@Nullable MetricsConfig operation
    ) implements BulkheadMetricsConfig {

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

    private record OperationTracingConfig(
        io.koraframework.telemetry.common.TelemetryConfig.TracingConfig global,
        BulkheadConfig.TelemetryConfig.@Nullable TracingConfig operation
    ) implements BulkheadTracingConfig {

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
