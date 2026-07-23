package ru.tinkoff.kora.camunda.zeebe.worker;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigValueExtractor
public interface ZeebeWorkerConfig {

    String DEFAULT = "default";

    /**
     * @return Job worker settings by worker type, where the default section is applied to all workers.
     */
    default Map<String, JobConfig> job() {
        return Map.of();
    }

    @ConfigValueExtractor
    interface JobConfig {

        /**
         * @return Name the worker is registered under on the broker.
         */
        default String name() {
            return "default";
        }

        /**
         * @return Retry backoff configuration of the worker.
         */
        @Nullable
        BackoffConfig backoff();

        /**
         * @return Tenant identifiers for which the worker can receive jobs.
         */
        @Nullable
        List<String> tenantIds();

        /**
         * @return Maximum time for one job execution by the worker.
         */
        @Nullable
        Duration timeout();

        /**
         * @return Maximum number of jobs activated simultaneously for this worker, used to align job fetching speed with processing speed.
         */
        @Nullable
        Integer maxJobsActive();

        /**
         * @return Request timeout used for polling a new job by the worker.
         */
        @Nullable
        Duration requestTimeout();

        /**
         * @return Maximum interval between polling new jobs.
         */
        @Nullable
        Duration pollInterval();

        /**
         * @return Whether the worker is enabled.
         */
        @Nullable
        Boolean enabled();

        /**
         * @return Whether streaming is used together with polling for job activation.
         */
        @Nullable
        Boolean streamEnabled();

        /**
         * @return Maximum stream lifetime when streaming is enabled.
         */
        @Nullable
        Duration streamTimeout();
    }

    @ConfigValueExtractor
    interface BackoffConfig {

        /**
         * @return Maximum retry delay, which can be exceeded due to jitter.
         */
        @Nullable
        Duration maxDelay();

        /**
         * @return Minimum retry delay, which the actual delay can fall below due to jitter.
         */
        @Nullable
        Duration minDelay();

        /**
         * @return Factor the previous delay is multiplied by.
         */
        @Nullable
        Double factor();

        /**
         * @return Jitter factor randomly changing the next delay within its +/- range.
         */
        @Nullable
        Double jitter();
    }

    BackoffConfig DEFAULT_BACKOFF_CONFIG = new $ZeebeWorkerConfig_BackoffConfig_ConfigValueExtractor.BackoffConfig_Impl(
        Duration.ofMillis(500), Duration.ofMillis(100), 1.0, 1.1
    );

    JobConfig DEFAULT_JOB_CONFIG = new $ZeebeWorkerConfig_JobConfig_ConfigValueExtractor.JobConfig_Impl(
        "default", DEFAULT_BACKOFF_CONFIG, List.of(), Duration.ofMinutes(15), 32, Duration.ofSeconds(15), Duration.ofMillis(100), true, false, Duration.ofSeconds(15)
    );

    default JobConfig getJobConfig(@Nonnull String jobType) {
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
        return new $ZeebeWorkerConfig_JobConfig_ConfigValueExtractor.JobConfig_Impl(
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

        return new $ZeebeWorkerConfig_BackoffConfig_ConfigValueExtractor.BackoffConfig_Impl(
            targetConfig.maxDelay() == null ? defaultConfig.maxDelay() : targetConfig.maxDelay(),
            targetConfig.minDelay() == null ? defaultConfig.minDelay() : targetConfig.minDelay(),
            targetConfig.factor() == null ? defaultConfig.factor() : targetConfig.factor(),
            targetConfig.jitter() == null ? defaultConfig.jitter() : targetConfig.jitter()
        );
    }
}
