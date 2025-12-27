package ru.tinkoff.kora.scheduling.common.telemetry;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Objects;

public final class SchedulingTelemetryConfig implements TelemetryConfig {
    private final OperationLogConfig logging;
    private final OperationMetricConfig metrics;
    private final OperationTracingConfig tracing;

    public SchedulingTelemetryConfig(TelemetryConfig config, @Nullable JobTelemetryConfig job) {
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
        private final JobTelemetryConfig.@Nullable JobLogConfig job;

        private OperationLogConfig(LogConfig client, JobTelemetryConfig.@Nullable JobLogConfig job) {
            this.client = Objects.requireNonNull(client);
            this.job = job;
        }

        @Override
        public boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }
    }

    private static class OperationMetricConfig implements MetricsConfig {
        private final MetricsConfig client;
        private final JobTelemetryConfig.@Nullable JobMetricsConfig job;

        private OperationMetricConfig(MetricsConfig client, JobTelemetryConfig.@Nullable JobMetricsConfig job) {
            this.client = Objects.requireNonNull(client);
            this.job = job;
        }

        @Override
        public boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }

        @Override
        public Duration[] slo() {
            if (this.job != null && this.job.slo() != null) {
                return this.job.slo();
            }
            return this.client.slo();
        }
    }

    private static class OperationTracingConfig implements TracingConfig {
        private final TracingConfig client;
        private final JobTelemetryConfig.@Nullable JobTracingConfig job;

        private OperationTracingConfig(TracingConfig client, JobTelemetryConfig.@Nullable JobTracingConfig job) {
            this.client = Objects.requireNonNull(client);
            this.job = job;
        }

        @Override
        public boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }
    }
}
