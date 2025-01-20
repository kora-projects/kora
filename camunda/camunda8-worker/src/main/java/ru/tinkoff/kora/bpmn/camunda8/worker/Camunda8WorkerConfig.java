package ru.tinkoff.kora.bpmn.camunda8.worker;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigValueExtractor
public interface Camunda8WorkerConfig {

    String DEFAULT = "default";

    default Map<String, JobConfig> job() {
        return Map.of();
    }

    @ConfigValueExtractor
    interface BackoffConfig {

        @Nullable
        Duration maxDelay();

        @Nullable
        Duration minDelay();

        @Nullable
        Double factory();

        @Nullable
        Double jitter();
    }

    @ConfigValueExtractor
    interface JobConfig {

        String type();

        @Nullable
        BackoffConfig backoff();

        @Nullable
        List<String> tenantIds();

        @Nullable
        Duration timeout();

        @Nullable
        Integer maxJobsActive();

        @Nullable
        Duration requestTimeout();

        @Nullable
        Duration pollInterval();

        @Nullable
        Boolean enabled();

        @Nullable
        Boolean streamEnabled();

        @Nullable
        Duration streamTimeout();
    }

    BackoffConfig DEFAULT_BACKOFF_CONFIG = new $Camunda8WorkerConfig_BackoffConfig_ConfigValueExtractor.BackoffConfig_Impl(
        Duration.ofMillis(500), Duration.ofMillis(100), 1.0, 1.1
    );

    JobConfig DEFAULT_JOB_CONFIG = new $Camunda8WorkerConfig_JobConfig_ConfigValueExtractor.JobConfig_Impl(
        "unknown", DEFAULT_BACKOFF_CONFIG, List.of(), Duration.ofMinutes(15), 32, Duration.ofSeconds(15), Duration.ofMillis(100), false, false, Duration.ofSeconds(15)
    );

    default JobConfig getJobConfig(@Nonnull String name) {
        JobConfig defaultConfig = job().get(DEFAULT);
        if (defaultConfig == null) {
            defaultConfig = DEFAULT_JOB_CONFIG;
        } else {
            defaultConfig = merge(defaultConfig, DEFAULT_JOB_CONFIG);
        }

        final JobConfig jobConfig = job().get(name);
        if (jobConfig == null) {
            return defaultConfig;
        } else {
            return merge(jobConfig, defaultConfig);
        }
    }

    private static JobConfig merge(@Nullable JobConfig targetConfig, JobConfig defaultConfig) {
        if (targetConfig == null) {
            return defaultConfig;
        }

        final BackoffConfig backoff = merge(targetConfig.backoff(), defaultConfig.backoff());
        return new $Camunda8WorkerConfig_JobConfig_ConfigValueExtractor.JobConfig_Impl(
            targetConfig.type() == null ? defaultConfig.type() : targetConfig.type(),
            backoff,
            targetConfig.tenantIds() == null ? defaultConfig.tenantIds() : targetConfig.tenantIds(),
            targetConfig.timeout() == null ? defaultConfig.timeout() : targetConfig.timeout(),
            targetConfig.maxJobsActive() == null ? defaultConfig.maxJobsActive() : targetConfig.maxJobsActive(),
            targetConfig.requestTimeout() == null ? defaultConfig.requestTimeout() : targetConfig.requestTimeout(),
            targetConfig.pollInterval() == null ? defaultConfig.pollInterval() : targetConfig.pollInterval(),
            targetConfig.enabled() == null ? defaultConfig.enabled() : targetConfig.enabled(),
            targetConfig.streamEnabled() == null ? defaultConfig.streamEnabled() : targetConfig.streamEnabled(),
            targetConfig.streamTimeout() == null ? defaultConfig.streamTimeout() : targetConfig.streamTimeout()
        );
    }

    private static BackoffConfig merge(@Nullable BackoffConfig targetConfig, BackoffConfig defaultConfig) {
        if (targetConfig == null) {
            return defaultConfig;
        }

        return new $Camunda8WorkerConfig_BackoffConfig_ConfigValueExtractor.BackoffConfig_Impl(
            targetConfig.maxDelay() == null ? defaultConfig.maxDelay() : targetConfig.maxDelay(),
            targetConfig.minDelay() == null ? defaultConfig.minDelay() : targetConfig.minDelay(),
            targetConfig.factory() == null ? defaultConfig.factory() : targetConfig.factory(),
            targetConfig.jitter() == null ? defaultConfig.jitter() : targetConfig.jitter()
        );
    }
}
