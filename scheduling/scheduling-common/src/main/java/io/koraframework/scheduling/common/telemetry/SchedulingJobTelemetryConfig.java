package io.koraframework.scheduling.common.telemetry;

import io.koraframework.scheduling.common.SchedulingJobConfig;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

public final class SchedulingJobTelemetryConfig implements SchedulingTelemetryConfig {

    private final OperationLogConfig logging;
    private final OperationMetricConfig metrics;
    private final OperationTracingConfig tracing;

    public SchedulingJobTelemetryConfig(SchedulingTelemetryConfig telemetryConfig,
                                        SchedulingJobConfig.JobTelemetryConfig jobTelemetryConfig) {
        this.logging = new OperationLogConfig(telemetryConfig.logging(), jobTelemetryConfig == null ? null : jobTelemetryConfig.logging());
        this.metrics = new OperationMetricConfig(telemetryConfig.metrics(), jobTelemetryConfig == null ? null : jobTelemetryConfig.metrics());
        this.tracing = new OperationTracingConfig(telemetryConfig.tracing(), jobTelemetryConfig == null ? null : jobTelemetryConfig.tracing());
    }

    @Override
    public SchedulingLogConfig logging() {
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

    private record OperationLogConfig(TelemetryConfig.LogConfig client, SchedulingJobConfig.JobTelemetryConfig.@Nullable LogConfig job) implements SchedulingLogConfig {

        @Override
        public boolean enabled() {
            if (this.job != null && this.job.enabled() != null) {
                return this.job.enabled();
            }
            return this.client.enabled();
        }
    }

    private record OperationMetricConfig(TelemetryConfig.MetricsConfig client, SchedulingJobConfig.JobTelemetryConfig.@Nullable MetricsConfig job) implements SchedulingMetricsConfig {

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

    private record OperationTracingConfig(TelemetryConfig.TracingConfig client, SchedulingJobConfig.JobTelemetryConfig.@Nullable TracingConfig job) implements SchedulingTracingConfig {

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
