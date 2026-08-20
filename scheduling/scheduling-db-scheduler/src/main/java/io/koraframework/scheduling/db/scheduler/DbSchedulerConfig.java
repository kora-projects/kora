package io.koraframework.scheduling.db.scheduler;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;

/**
 * Configuration for the Kora db-scheduler integration.
 *
 * <p>This config is read from the {@code scheduling.dbScheduler} path by
 * {@code DbSchedulerModule}. It controls table initialization, scheduler
 * execution parallelism, polling behavior, shutdown timeout, and database table
 * name used by the underlying db-scheduler instance.
 *
 * <p>The module registers a default scheduler wrapper that uses the application
 * {@code DataSource}. A different data source can be supplied with the
 * {@code DbSchedulerWrapper} tag, and the underlying
 * {@code com.github.kagkarlsson.scheduler.SchedulerBuilder} can be customized
 * by providing a {@code Configurer<SchedulerBuilder>} component.
 *
 * <p>Example configuration:
 * <pre>{@code
 * scheduling {
 *   dbScheduler {
 *     initializeTable = true
 *     executionParallelism = 10
 *     shutdownWait = "30s"
 *     tableName = "kora_scheduling_db_jobs"
 *
 *     polling {
 *       strategy = "FETCH"
 *       prefetchMode = "BOUNDED"
 *       interval = "10s"
 *     }
 *   }
 * }
 * }</pre>
 */
@ConfigMapper
public interface DbSchedulerConfig {

    /**
     * Enables automatic creation of the db-scheduler table on application
     * startup.
     *
     * <p>When enabled, the module checks whether {@link #tableName()} exists
     * and applies the bundled db-scheduler schema migration when the table is
     * missing. The migration is selected for the current database type and the
     * default db-scheduler table name is replaced with {@link #tableName()}.
     *
     * <p>The default is {@code false}; production deployments may prefer
     * external schema management.
     *
     * @return {@code true} to initialize the scheduler table automatically
     */
    default boolean initializeTable() {
        return false;
    }

    /**
     * Maximum number of scheduler job bodies that may execute concurrently.
     *
     * <p>This value is passed to db-scheduler as its thread count and is also
     * used by Kora's bounded virtual-thread executor. The default executor
     * starts accepted tasks on virtual threads and uses this value to limit
     * concurrently running job bodies.
     *
     * @return maximum scheduler execution parallelism
     */
    default int executionParallelism() {
        return 10; // default in DB Scheduling
    }

    /**
     * @return Polling configuration used by db-scheduler to fetch due executions from
     * the database.
     */
    PollingConfig polling();

    /**
     * Maximum time to wait for scheduler shutdown.
     *
     * <p>The value is passed to db-scheduler as {@code shutdownMaxWait}. It
     * controls how long shutdown waits for currently running executions to
     * finish.
     *
     * @return shutdown wait timeout
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * Database table name used by db-scheduler.
     *
     * <p>The same value is used for scheduler runtime operations and optional
     * table initialization. The default is {@code kora_scheduling_db_jobs}.
     *
     * @return scheduler table name
     */
    default String tableName() {
        return "kora_scheduling_db_jobs";
    }

    /**
     * Configuration for db-scheduler polling.
     *
     * <p>Polling controls how often the scheduler checks the database for due
     * executions and how many executions it prefetches into local memory.
     */
    @ConfigMapper
    interface PollingConfig {

        /**
         * @return Polling algorithm used by db-scheduler.
         */
        default PollingStrategy strategy() {
            return PollingStrategy.FETCH;
        }

        /**
         * Prefetch limit preset for the selected polling strategy.
         *
         * <p>The preset is translated to db-scheduler lower and upper polling
         * ratios. {@link PrefetchMode#DEFAULT} delegates to db-scheduler's own
         * defaults.
         *
         * @return prefetch mode
         */
        default PrefetchMode prefetchMode() {
            return PrefetchMode.DEFAULT;
        }

        /**
         * @return Delay between scheduler polling attempts.
         */
        default Duration interval() {
            return Duration.ofSeconds(10);
        }
    }

    /**
     * db-scheduler polling strategy.
     */
    enum PollingStrategy {
        /**
         * Uses db-scheduler fetch polling.
         */
        FETCH,

        /**
         * Uses db-scheduler lock-and-fetch polling.
         */
        LOCK_AND_FETCH
    }

    /**
     * Prefetch presets for db-scheduler polling limits.
     *
     * <p>The selected mode controls the lower and upper fraction-of-threads
     * limits passed to db-scheduler polling configuration. These limits define
     * when the scheduler refills its local execution inventory and how far it
     * may prefetch relative to {@link #executionParallelism()}.
     */
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

        /**
         * Returns whether this mode delegates polling ratios to db-scheduler
         * defaults.
         *
         * @return {@code true} only for {@link #DEFAULT}
         */
        public boolean usesDbSchedulerDefaults() {
            return this == DEFAULT;
        }

        /**
         * Lower fraction-of-threads limit passed to db-scheduler.
         *
         * @return lower polling ratio
         * @throws IllegalStateException when called for {@link #DEFAULT}
         */
        public double lowerLimitRatio() {
            if (usesDbSchedulerDefaults()) {
                throw new IllegalStateException("DEFAULT does not define fixed polling ratios");
            }

            return this.lowerLimitRatio;
        }

        /**
         * Upper fraction-of-threads limit passed to db-scheduler.
         *
         * @return upper polling ratio
         * @throws IllegalStateException when called for {@link #DEFAULT}
         */
        public double upperLimitRatio() {
            if (usesDbSchedulerDefaults()) {
                throw new IllegalStateException("DEFAULT does not define fixed polling ratios");
            }

            return this.upperLimitRatio;
        }
    }
}
