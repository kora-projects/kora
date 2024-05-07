package ru.tinkoff.kora.http.client.common.declarative;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class HttpClientOperationTelemetryConfig implements TelemetryConfig {
    private final OperationLogConfig logging;
    private final OperationMetricConfig metrics;
    private final OperationTracingConfig tracing;

    public HttpClientOperationTelemetryConfig(TelemetryConfig client, TelemetryConfig operation) {
        this.logging = new OperationLogConfig(client.logging(), operation.logging());
        this.metrics = new OperationMetricConfig(client.metrics(), operation.metrics());
        this.tracing = new OperationTracingConfig(client.tracing(), operation.tracing());
    }

    @Override
    public LogConfig logging() {
        return this.logging;
    }

    @Override
    public TracingConfig tracing() {
        return this.tracing;
    }

    @Override
    public MetricsConfig metrics() {
        return this.metrics;
    }

    private static class OperationLogConfig implements LogConfig {
        private final LogConfig client;
        private final LogConfig operation;

        private OperationLogConfig(LogConfig client, LogConfig operation) {
            this.client = Objects.requireNonNull(client);
            this.operation = Objects.requireNonNull(operation);
        }

        @Nullable
        @Override
        public Boolean enabled() {
            if (this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.client.enabled();
        }
    }

    private static class OperationMetricConfig implements MetricsConfig {
        private final MetricsConfig client;
        private final MetricsConfig operation;

        private OperationMetricConfig(MetricsConfig client, MetricsConfig operation) {
            this.client = Objects.requireNonNull(client);
            this.operation = Objects.requireNonNull(operation);
        }

        @Nullable
        @Override
        public Boolean enabled() {
            if (this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.client.enabled();
        }

        @Override
        public double[] slo() {
            if (this.operation.slo() != null) {
                return this.operation.slo();
            }
            return this.client.slo();
        }
    }

    private static class OperationTracingConfig implements TracingConfig {
        private final TracingConfig client;
        private final TracingConfig operation;

        private OperationTracingConfig(TracingConfig client, TracingConfig operation) {
            this.client = client;
            this.operation = operation;
        }

        @Nullable
        @Override
        public Boolean enabled() {
            if (this.operation.enabled() != null) {
                return this.operation.enabled();
            }
            return this.client.enabled();
        }
    }
}
