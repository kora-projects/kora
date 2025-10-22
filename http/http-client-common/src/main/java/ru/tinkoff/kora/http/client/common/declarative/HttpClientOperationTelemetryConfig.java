package ru.tinkoff.kora.http.client.common.declarative;

import ru.tinkoff.kora.http.client.common.telemetry.HttpClientLoggerConfig;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HttpClientOperationTelemetryConfig implements HttpClientTelemetryConfig {
    private final OperationLogConfig logging;
    private final OperationMetricConfig metrics;
    private final OperationTracingConfig tracing;

    public HttpClientOperationTelemetryConfig(HttpClientTelemetryConfig client, HttpClientOperationConfig.OperationTelemetryConfig operation) {
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
        private final HttpClientOperationConfig.OperationTelemetryConfig.LoggingConfig operation;

        private OperationLogConfig(HttpClientLoggerConfig client, HttpClientOperationConfig.OperationTelemetryConfig.LoggingConfig operation) {
            this.client = Objects.requireNonNull(client);
            this.operation = Objects.requireNonNull(operation);
        }

        @Override
        public boolean enabled() {
            var operation = this.operation.enabled();
            if (operation != null) {
                return operation;
            }
            return this.client.enabled();
        }

        @Override
        public Set<String> maskQueries() {
            return Objects.requireNonNullElse(this.operation.maskQueries(), this.client.maskQueries());
        }

        @Override
        public Set<String> maskHeaders() {
            return Objects.requireNonNullElse(this.operation.maskHeaders(), this.client.maskHeaders());
        }

        @Override
        public String mask() {
            return Objects.requireNonNullElse(this.operation.mask(), this.client.mask());
        }

        @Override
        public boolean pathTemplate() {
            var operation = this.operation.pathTemplate();
            if (operation != null) {
                return operation;
            }
            return this.client.pathTemplate();
        }
    }

    private static class OperationMetricConfig implements MetricsConfig {
        private final MetricsConfig client;
        private final HttpClientOperationConfig.OperationTelemetryConfig.MetricsConfig operation;

        private OperationMetricConfig(MetricsConfig client, HttpClientOperationConfig.OperationTelemetryConfig.MetricsConfig operation) {
            this.client = Objects.requireNonNull(client);
            this.operation = Objects.requireNonNull(operation);
        }

        @Override
        public boolean enabled() {
            var operation = this.operation.enabled();
            if (operation != null) {
                return operation;
            }
            return this.client.enabled();
        }

        @Override
        public Duration[] slo() {
            var operation = this.operation.slo();
            if (operation != null) {
                return operation;
            }
            return this.client.slo();
        }

        @Override
        public Map<String, String> tags() {
            var tags = this.operation.tags();
            if (tags != null) {
                return tags;
            }
            return this.client.tags();
        }
    }

    private static class OperationTracingConfig implements TracingConfig {
        private final TracingConfig client;
        private final HttpClientOperationConfig.OperationTelemetryConfig.TracingConfig operation;

        private OperationTracingConfig(TracingConfig client, HttpClientOperationConfig.OperationTelemetryConfig.TracingConfig operation) {
            this.client = client;
            this.operation = operation;
        }

        @Override
        public boolean enabled() {
            var operation = this.operation.enabled();
            if (operation != null) {
                return operation;
            }
            return this.client.enabled();
        }

        @Override
        public Map<String, String> attributes() {
            var operation = this.operation.attributes();
            if (operation != null) {
                return operation;
            }
            return this.client.attributes();
        }
    }
}
