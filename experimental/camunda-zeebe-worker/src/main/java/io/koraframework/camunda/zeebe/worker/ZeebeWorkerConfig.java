package io.koraframework.camunda.zeebe.worker;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigMapper
@NullMarked
public interface ZeebeWorkerConfig {

    String DEFAULT = "default";

    default Map<String, JobConfig> job() {
        return Map.of();
    }

    @ConfigMapper
    interface JobConfig {

        default String name() {
            return "default";
        }

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

    @ConfigMapper
    interface BackoffConfig {

        @Nullable
        Duration maxDelay();

        @Nullable
        Duration minDelay();

        @Nullable
        Double factor();

        @Nullable
        Double jitter();
    }

    BackoffConfig DEFAULT_BACKOFF_CONFIG = new $ZeebeWorkerConfig_BackoffConfig_ConfigValueMapper.BackoffConfig_Impl(
        Duration.ofMillis(500), Duration.ofMillis(100), 1.0, 1.1
    );

    JobConfig DEFAULT_JOB_CONFIG = new $ZeebeWorkerConfig_JobConfig_ConfigValueMapper.JobConfig_Impl(
        "default", DEFAULT_BACKOFF_CONFIG, List.of(), Duration.ofMinutes(15), 32, Duration.ofSeconds(15), Duration.ofMillis(100), true, false, Duration.ofSeconds(15)
    );

    default JobConfig getJobConfig(String jobType) {
        JobConfig defaultConfig = job().get(DEFAULT);
        if (defaultConfig == null) {
            defaultConfig = DEFAULT_JOB_CONFIG;
        } else {
            defaultConfig = merge(defaultConfig, DEFAULT_JOB_CONFIG);
        }

        final JobConfig jobConfig = job().get(jobType);
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
        return new $ZeebeWorkerConfig_JobConfig_ConfigValueMapper.JobConfig_Impl(
            targetConfig.name() == null ? defaultConfig.name() : targetConfig.name(),
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

        return new $ZeebeWorkerConfig_BackoffConfig_ConfigValueMapper.BackoffConfig_Impl(
            targetConfig.maxDelay() == null ? defaultConfig.maxDelay() : targetConfig.maxDelay(),
            targetConfig.minDelay() == null ? defaultConfig.minDelay() : targetConfig.minDelay(),
            targetConfig.factor() == null ? defaultConfig.factor() : targetConfig.factor(),
            targetConfig.jitter() == null ? defaultConfig.jitter() : targetConfig.jitter()
        );
    }
}
