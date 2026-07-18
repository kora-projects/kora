package io.koraframework.telemetry.common;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;
import java.util.Map;

@ConfigMapper
public interface TelemetryConfig {

    LoggingConfig logging();

    TracingConfig tracing();

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

        default Map<String, String> attributes() {
            return Map.of();
        }
    }

    @ConfigMapper
    interface MetricsConfig {

        enum MetricsMode {
            SUMMARY,
            SLO,
            VM
        }

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

        default MetricsMode mode() {
            return MetricsMode.SLO;
        }

        VmConfig vm();

        default Duration[] slo() {
            return DEFAULT_SLO;
        }

        default Map<String, String> tags() {
            return Map.of();
        }

        @ConfigMapper
        interface VmConfig {

            default Duration min() {
                return Duration.ofMillis(1);
            }

            default Duration max() {
                return Duration.ofSeconds(90);
            }

            default int buckets() {
                return 16;
            }
        }
    }
}
