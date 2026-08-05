package io.koraframework.telemetry.common;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;
import java.util.Map;

@ConfigMapper
public interface TelemetryConfig {

    /**
     * @return Logging telemetry configuration.
     */
    LoggingConfig logging();

    /**
     * @return Tracing telemetry configuration.
     */
    TracingConfig tracing();

    /**
     * @return Metrics telemetry configuration.
     */
    MetricsConfig metrics();

    @ConfigMapper
    interface LoggingConfig {

        default boolean enabled() {
            return false;
        }
    }

    @ConfigMapper
    interface TracingConfig {

        default boolean enabled() {
            return true;
        }

        /**
         * @return Attributes added to every span created by the module.
         */
        default Map<String, String> attributes() {
            return Map.of();
        }
    }

    @ConfigMapper
    interface MetricsConfig {

        Duration[] DEFAULT_SLO = new Duration[]{
            Duration.ofMillis(1),
            Duration.ofMillis(10),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(200),
            Duration.ofMillis(500),
            Duration.ofMillis(1000),
            Duration.ofMillis(2000),
            Duration.ofMillis(5000),
            Duration.ofMillis(10000),
            Duration.ofMillis(20000),
            Duration.ofMillis(30000),
            Duration.ofMillis(60000),
            Duration.ofMillis(90000)
        };

        default boolean enabled() {
            return false;
        }

        default Duration[] slo() {
            return DEFAULT_SLO;
        }

        /**
         * @return Extra common tags added to every metric reported by the module.
         */
        default Map<String, String> tags() {
            return Map.of();
        }
    }
}
