package ru.tinkoff.kora.telemetry.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.Map;

@ConfigValueExtractor
public interface TelemetryConfig {
    /**
     * @return Logging telemetry configuration.
     */
    LogConfig logging();

    /**
     * @return Tracing telemetry configuration.
     */
    TracingConfig tracing();

    /**
     * @return Metrics telemetry configuration.
     */
    MetricsConfig metrics();

    @ConfigValueExtractor
    interface LogConfig {
        /**
         * @return Whether logging is enabled for the module.
         */
        @Nullable
        Boolean enabled();
    }

    @ConfigValueExtractor
    interface TracingConfig {
        /**
         * @return Whether tracing is enabled for the module.
         */
        @Nullable
        Boolean enabled();

        /**
         * @return Attributes added to every span created by the module.
         */
        default Map<String, String> attributes() {
            return Map.of();
        }
    }

    @ConfigValueExtractor
    interface MetricsConfig {
        double[] DEFAULT_SLO = new double[]{1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000};
        double[] DEFAULT_SLO_V123 = new double[]{0.001, 0.010, 0.050, 0.100, 0.200, 0.500, 1, 2, 5, 10, 20, 30, 60, 90};

        enum OpentelemetrySpec {
            V120, V123
        }

        /**
         * @return Whether metrics collection is enabled for the module.
         */
        @Nullable
        Boolean enabled();

        /**
         * @return SLO histogram buckets for DistributionSummary and Timer metrics.
         */
        @Nullable
        double[] slo();

        default double[] slo(OpentelemetrySpec spec) {
            var slo = this.slo();
            if (slo != null) {
                return slo;
            }
            return switch (spec) {
                case V120 -> DEFAULT_SLO;
                case V123 -> DEFAULT_SLO_V123;
            };
        }

        /**
         * @return Extra common tags added to every metric reported by the module.
         */
        default Map<String, String> tags() {
            return Map.of();
        }
    }
}
