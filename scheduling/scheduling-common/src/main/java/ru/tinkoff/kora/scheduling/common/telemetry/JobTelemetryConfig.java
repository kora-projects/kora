package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class JobTelemetryConfig implements TelemetryConfig {
    private final OperationLogConfig logging;
    private final OperationMetricConfig metrics;
    private final OperationTracingConfig tracing;

    public JobTelemetryConfig(TelemetryConfig config, @Nullable TelemetryConfig job) {
        this.logging = new OperationLogConfig(config.logging(), job == null ? null : job.logging());
        this.metrics = new OperationMetricConfig(config.metrics(), job == null ? null : job.metrics());
        this.tracing = new OperationTracingConfig(config.tracing(), job == null ? null : job.tracing());
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
        @Nullable
        private final LogConfig job;

        private OperationLogConfig(LogConfig client, @Nullable LogConfig job) {
            this.client = Objects.requireNonNull(client);
            this.job = job;
        }

        @Nullable
        @Override
        public Boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }
    }

    private static class OperationMetricConfig implements MetricsConfig {
        private final MetricsConfig client;
        @Nullable
        private final MetricsConfig job;

        private OperationMetricConfig(MetricsConfig client, @Nullable MetricsConfig job) {
            this.client = Objects.requireNonNull(client);
            this.job = job;
        }

        @Nullable
        @Override
        public Boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }

        @Override
        public double[] slo() {
            if (this.job != null && this.job.slo() != null) {
                return this.job.slo();
            }
            return this.client.slo(null);
        }
    }

    private static class OperationTracingConfig implements TracingConfig {
        private final TracingConfig client;
        @Nullable
        private final TracingConfig job;

        private OperationTracingConfig(TracingConfig client, @Nullable TracingConfig job) {
            this.client = Objects.requireNonNull(client);
            this.job = job;
        }

        @Nullable
        @Override
        public Boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }
    }
}
