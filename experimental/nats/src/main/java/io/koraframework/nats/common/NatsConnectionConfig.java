package io.koraframework.nats.common;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Properties;

/**
 * Connection settings inherited by each publisher and listener configuration.
 */
public interface NatsConnectionConfig {

    /**
     * Official jnats Options properties for this publisher or listener.
     */
    default Properties driverProperties() {
        return new Properties();
    }

    default boolean driverMetricsEnabled() {
        return false;
    }

    /**
     * Enables connection and listener readiness checks. Disabled by default.
     */
    default boolean readinessProbe() {
        return false;
    }

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    default JetStreamOptionsConfig jetStreamOptions() {

        return new JetStreamOptionsConfig() {
            @Override
            public @Nullable String domain() {
                return null;
            }

            @Override
            public @Nullable String prefix() {
                return null;
            }
        };
    }

    @ConfigMapper
    interface JetStreamOptionsConfig {

        @Nullable String domain();

        @Nullable String prefix();

        default Duration requestTimeout() {
            return Duration.ofSeconds(5);
        }
    }
}
