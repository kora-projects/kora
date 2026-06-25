package io.koraframework.scheduling.common.telemetry;

import io.koraframework.scheduling.common.SchedulingJobConfig.JobTelemetryConfig;
import io.koraframework.scheduling.common.SchedulingJobConfig.JobTelemetryConfig.JobLoggingConfig;
import io.koraframework.scheduling.common.SchedulingJobConfig.JobTelemetryConfig.JobMetricsConfig;
import io.koraframework.scheduling.common.SchedulingJobConfig.JobTelemetryConfig.JobTracingConfig;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public final class SchedulingJobTelemetryConfig implements SchedulingTelemetryConfig {

    private final OperationLoggingConfig logging;
    private final OperationMetricConfig metrics;
    private final OperationTracingConfig tracing;

    public SchedulingJobTelemetryConfig(SchedulingTelemetryConfig telemetryConfig, @Nullable JobTelemetryConfig jobTelemetryConfig) {
        this.logging = new OperationLoggingConfig(telemetryConfig.logging(), jobTelemetryConfig == null ? null : jobTelemetryConfig.logging());
        this.metrics = new OperationMetricConfig(telemetryConfig.metrics(), jobTelemetryConfig == null ? null : jobTelemetryConfig.metrics());
        this.tracing = new OperationTracingConfig(telemetryConfig.tracing(), jobTelemetryConfig == null ? null : jobTelemetryConfig.tracing());
    }

    @Override
    public SchedulingLoggingConfig logging() {
        return this.logging;
    }

    @Override
    public SchedulingTracingConfig tracing() {
        return this.tracing;
    }

    @Override
    public SchedulingMetricsConfig metrics() {
        return this.metrics;
    }

    private record OperationLoggingConfig(TelemetryConfig.LoggingConfig client, @Nullable JobLoggingConfig job) implements SchedulingLoggingConfig {

        @Override
        public boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }
    }

    private record OperationMetricConfig(TelemetryConfig.MetricsConfig client, @Nullable JobMetricsConfig job) implements SchedulingMetricsConfig {

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

        @Override
        public Map<String, String> tags() {
            if (this.job != null && this.job.tags() != null) {
                return this.job.tags();
            }
            return this.client.tags();
        }
    }

    private record OperationTracingConfig(TelemetryConfig.TracingConfig client, @Nullable JobTracingConfig job) implements SchedulingTracingConfig {

        @Override
        public boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }

        @Override
        public Map<String, String> attributes() {
            if (this.job != null && this.job.attributes() != null) {
                return this.job.attributes();
            }
            return this.client.attributes();
        }
    }
}
