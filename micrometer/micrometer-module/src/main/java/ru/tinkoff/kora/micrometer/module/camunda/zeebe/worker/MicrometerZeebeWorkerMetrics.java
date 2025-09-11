package ru.tinkoff.kora.micrometer.module.camunda.zeebe.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;
import ru.tinkoff.kora.camunda.zeebe.worker.JobWorkerException;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerMetrics;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MicrometerZeebeWorkerMetrics implements ZeebeWorkerMetrics {

    private static final String CODE_UNKNOWN = "UNKNOWN";

    private static final String STATUS_COMPLETE = "complete";
    private static final String STATUS_FAILED = "failed";

    private static final String ERROR_USER = "user";
    private static final String ERROR_SYSTEM = "system";

    record MetricsKey(String jobName, String jobType) {}

    record Metrics(Timer complete,
                   Timer failedUser,
                   Timer failedSystem,
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

        metrics.complete.record(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordFailed(JobContext jobContext, long startTimeInNanos, ZeebeWorkerTelemetry.ErrorType errorType, Throwable throwable) {
        final MetricsKey key = new MetricsKey(jobContext.jobName(), jobContext.jobType());
        final Metrics metrics = this.keyToMetrics.computeIfAbsent(key, k -> buildMetrics(jobContext));

        if (ZeebeWorkerTelemetry.ErrorType.USER.equals(errorType)) {
            metrics.failedUser.record(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);
        } else {
            metrics.failedSystem.record(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);
        }

        final String errorCode = (throwable instanceof JobWorkerException je) ? je.getCode() : CODE_UNKNOWN;
        final Counter errorCodeCounter = metrics.codeToCounter.computeIfAbsent(errorCode, k ->
            Counter.builder("zeebe.worker.handler")
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
        var durationSuccess = Timer.builder("zeebe.worker.handler.duration")
            .tags(List.of(
                Tag.of("job.name", jobContext.jobName()),
                Tag.of("job.type", jobContext.jobType()),
                Tag.of("status", STATUS_COMPLETE)
            ))
            .serviceLevelObjectives(this.config.slo())
            .register(this.registry);

        var durationFailedUser = Timer.builder("zeebe.worker.handler.duration")
            .tags(List.of(
                Tag.of("job.name", jobContext.jobName()),
                Tag.of("job.type", jobContext.jobType()),
                Tag.of("status", STATUS_FAILED),
                Tag.of("error", ERROR_USER)
            ))
            .serviceLevelObjectives(this.config.slo())
            .register(this.registry);

        var durationFailedSystem = Timer.builder("zeebe.worker.handler.duration")
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
