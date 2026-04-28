package io.koraframework.redis.jedis;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface JedisConfig {

    List<String> uri();

    @Nullable
    Integer database();

    @Nullable
    String user();

    @Nullable
    String password();

    default Protocol protocol() {
        return Protocol.RESP3;
    }

    default Duration connectionTimeout() {
        return Duration.ofSeconds(5);
    }

    default Duration socketTimeout() {
        return Duration.ofSeconds(15);
    }

    SslConfig ssl();

    PoolConfig pool();

    ClusterConfig cluster();

    @ConfigValueExtractor
    interface SslConfig {

        default boolean enabled() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface PoolConfig {

        default int maxSize() {
            return Math.min(8, Runtime.getRuntime().availableProcessors() * 8);
        }

        default int maxIdle() {
            return Math.min(8, Runtime.getRuntime().availableProcessors() * 8);
        }

        default int minIdle() {
            return 1;
        }

        default Duration maxAcquireWait() {
            return Duration.ofSeconds(30);
        }

        default boolean validateOnAcquire() {
            return false;
        }

        default boolean validateOnCreate() {
            return false;
        }

        default boolean validateOnRelease() {
            return false;
        }

        default boolean validateOnIdle() {
            return true;
        }

        default Duration evictIdleDuration() {
            return Duration.ofSeconds(60);
        }

        default Duration evictBetweenRunsDuration() {
            return Duration.ofSeconds(30);
        }

        /** -1 means that test available all connections */
        default int evictTestsPerRun() {
            return -1;
        }

        default boolean jmxEnabled() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface ClusterConfig {

        enum ReadStrategy {
            MASTER,
            REPLICA
        }

        default ReadStrategy readStrategy() {
            return ReadStrategy.MASTER;
        }

        @Nullable
        Duration topologyRefreshPeriod();

        default int retryMaxAttempts() {
            return 1; // minimum 1
        }

        default Duration retryMaxDuration() {
            return Duration.ofSeconds(31);
        }
    }

    enum Protocol {

        /**
         * Redis 2 to Redis 5
         */
        RESP2,
        /**
         * Redis 6+
         */
        RESP3
    }
}
