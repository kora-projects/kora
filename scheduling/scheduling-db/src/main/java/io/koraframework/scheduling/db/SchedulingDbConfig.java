package io.koraframework.scheduling.db;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;

@ConfigMapper
public interface SchedulingDbConfig {

    default boolean initializeTable() {
        return false;
    }

    default int executionParallelism() {
        return 10; // default in DB Scheduling
    }

    PollingConfig polling();

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    default String tableName() {
        return "kora_scheduling_db_jobs";
    }

    @ConfigMapper
    interface PollingConfig {

        default PollingStrategy strategy() {
            return PollingStrategy.FETCH;
        }

        default PrefetchMode prefetchMode() {
            return PrefetchMode.DEFAULT;
        }

        default Duration interval() {
            return Duration.ofSeconds(10);
        }
    }

    enum PollingStrategy {
        FETCH,
        LOCK_AND_FETCH
    }

    enum PrefetchMode {

        /**
         * Preserves the default db-scheduler polling behavior.
         *
         * <p>For {@code FETCH}, db-scheduler uses {@code 0.5 / 3.0}.
         * For {@code LOCK_AND_FETCH}, it uses {@code 0.5 / 1.0}.
         *
         * @see com.github.kagkarlsson.scheduler.PollingStrategyConfig
         */
        DEFAULT(Double.NaN, Double.NaN),

        /**
         * Keeps the local execution inventory close to
         * {@code executionParallelism}.
         *
         * <p>Provides minimal executor backlog while starting a refill when
         * approximately half of the local executions remain.
         */
        BOUNDED(0.5, 1.0),

        /**
         * Keeps a moderate local execution buffer to reduce idle gaps between
         * completing an execution and fetching more work from the database.
         */
        BUFFERED(0.75, 2.0);

        private final double lowerLimitRatio;
        private final double upperLimitRatio;

        PrefetchMode(double lowerLimitRatio, double upperLimitRatio) {
            this.lowerLimitRatio = lowerLimitRatio;
            this.upperLimitRatio = upperLimitRatio;
        }

        public boolean usesDbSchedulerDefaults() {
            return this == DEFAULT;
        }

        public double lowerLimitRatio() {
            if (usesDbSchedulerDefaults()) {
                throw new IllegalStateException("DEFAULT does not define fixed polling ratios");
            }

            return this.lowerLimitRatio;
        }

        public double upperLimitRatio() {
            if (usesDbSchedulerDefaults()) {
                throw new IllegalStateException("DEFAULT does not define fixed polling ratios");
            }

            return this.upperLimitRatio;
        }
    }
}
