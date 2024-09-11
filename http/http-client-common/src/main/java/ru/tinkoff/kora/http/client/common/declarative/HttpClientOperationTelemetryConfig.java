package ru.tinkoff.kora.http.client.common.declarative;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientLoggerConfig;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;

import java.util.Objects;
import java.util.Set;

public final class HttpClientOperationTelemetryConfig implements HttpClientTelemetryConfig {
    private final OperationLogConfig logging;
    private final OperationMetricConfig metrics;
    private final OperationTracingConfig tracing;

    public HttpClientOperationTelemetryConfig(HttpClientTelemetryConfig client, HttpClientTelemetryConfig operation) {
        this.logging = new OperationLogConfig(client.logging(), operation.logging());
        this.metrics = new OperationMetricConfig(client.metrics(), operation.metrics());
        this.tracing = new OperationTracingConfig(client.tracing(), operation.tracing());
    }

    @Override
    public HttpClientLoggerConfig logging() {
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

    private static class OperationLogConfig implements HttpClientLoggerConfig {
        private final HttpClientLoggerConfig client;
        private final HttpClientLoggerConfig operation;

        private OperationLogConfig(HttpClientLoggerConfig client, HttpClientLoggerConfig operation) {
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

        @Nullable
        @Override
        public Set<String> maskQueries() {
            if (!this.operation.maskQueries().equals(DEFAULT_MASK_QUERIES)) {
                return this.operation.maskQueries();
            }
            return this.client.maskQueries();
        }

        @Nullable
        @Override
        public Set<String> maskHeaders() {
            if (!this.operation.maskHeaders().equals(DEFAULT_MASK_HEADERS)) {
                return this.operation.maskHeaders();
            }
            return this.operation.maskHeaders();
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
