package ru.tinkoff.kora.micrometer.module.camunda.zeebe.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;
import ru.tinkoff.kora.camunda.zeebe.worker.JobWorkerException;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerMetrics;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerZeebeWorkerMetrics implements ZeebeWorkerMetrics {

    private static final String CODE_UNKNOWN = "UNKNOWN";

    private static final String STATUS_COMPLETE = "complete";
    private static final String STATUS_FAILED = "failed";

    private static final String ERROR_USER = "user";
    private static final String ERROR_SYSTEM = "system";

    record MetricsKey(String jobName, String jobType) {}

    record Metrics(DistributionSummary complete,
                   DistributionSummary failedUser,
                   DistributionSummary failedSystem,
                   Map<String, Counter> codeToCounter) {}

    private final ConcurrentHashMap<MetricsKey, Metrics> keyToMetrics = new ConcurrentHashMap<>();

    private final MeterRegistry registry;
    private final TelemetryConfig.MetricsConfig config;

    public MicrometerZeebeWorkerMetrics(MeterRegistry registry, TelemetryConfig.MetricsConfig config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public void recordComplete(JobContext jobContext, long startTimeInNanos) {
        final MetricsKey key = new MetricsKey(jobContext.jobName(), jobContext.jobType());
        final Metrics metrics = this.keyToMetrics.computeIfAbsent(key, k -> buildMetrics(jobContext));

        var processingTime = ((double) (System.nanoTime() - startTimeInNanos) / 1_000_000);
        metrics.complete.record(processingTime);
    }

    @Override
    public void recordFailed(JobContext jobContext, long startTimeInNanos, ZeebeWorkerTelemetry.ErrorType errorType, Throwable throwable) {
        final MetricsKey key = new MetricsKey(jobContext.jobName(), jobContext.jobType());
        final Metrics metrics = this.keyToMetrics.computeIfAbsent(key, k -> buildMetrics(jobContext));

        var processingTime = ((double) (System.nanoTime() - startTimeInNanos) / 1_000_000);

        if (ZeebeWorkerTelemetry.ErrorType.USER.equals(errorType)) {
            metrics.failedUser.record(processingTime);
        } else {
            metrics.failedSystem.record(processingTime);
        }

        final String errorCode = (throwable instanceof JobWorkerException je) ? je.getCode() : CODE_UNKNOWN;
        final Counter errorCodeCounter = metrics.codeToCounter.computeIfAbsent(errorCode, k ->
            Counter.builder("zeebe.worker.job")
                .tags(List.of(
                    Tag.of("job.name", jobContext.jobName()),
                    Tag.of("job.type", jobContext.jobType()),
                    Tag.of("status", STATUS_FAILED),
                    Tag.of("error.code", errorCode)
                ))
                .register(this.registry));

        errorCodeCounter.increment();
    }

    private Metrics buildMetrics(JobContext jobContext) {
        var durationSuccess = DistributionSummary.builder("zeebe.worker.job")
            .tags(List.of(
                Tag.of("job.name", jobContext.jobName()),
                Tag.of("job.type", jobContext.jobType()),
                Tag.of("status", STATUS_COMPLETE)
            ))
            .serviceLevelObjectives(this.config.slo())
            .register(this.registry);

        var durationFailedUser = DistributionSummary.builder("zeebe.worker.job")
            .tags(List.of(
                Tag.of("job.name", jobContext.jobName()),
                Tag.of("job.type", jobContext.jobType()),
                Tag.of("status", STATUS_FAILED),
                Tag.of("error", ERROR_USER)
            ))
            .serviceLevelObjectives(this.config.slo())
            .register(this.registry);

        var durationFailedSystem = DistributionSummary.builder("zeebe.worker.job")
            .tags(List.of(
                Tag.of("job.name", jobContext.jobName()),
                Tag.of("job.type", jobContext.jobType()),
                Tag.of("status", STATUS_FAILED),
                Tag.of("error", ERROR_SYSTEM)
            ))
            .serviceLevelObjectives(this.config.slo())
            .register(this.registry);

        return new Metrics(durationSuccess, durationFailedUser, durationFailedSystem, new ConcurrentHashMap<>());
    }
}
