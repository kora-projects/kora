package ru.tinkoff.kora.telemetry.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface TelemetryConfig {
    LogConfig logging();

    TracingConfig tracing();

    MetricsConfig metrics();

    @ConfigValueExtractor
    interface LogConfig {
        @Nullable
        Boolean enabled();
    }

    @ConfigValueExtractor
    interface TracingConfig {
        @Nullable
        Boolean enabled();
    }

    @ConfigValueExtractor
    interface MetricsConfig {
        double[] DEFAULT_SLO = new double[]{1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000};
        double[] DEFAULT_SLO_V123 = new double[]{0.001, 0.010, 0.050, 0.100, 0.200, 0.500, 1, 2, 5, 10, 20, 30, 60, 90};

        enum OpentelemetrySpec {
            V120, V123
        }

        default OpentelemetrySpec spec() {
            // todo replace in some major release maybe
            return OpentelemetrySpec.V120;
        }

        @Nullable
        Boolean enabled();

        @Nullable
        double[] slo();

        default double[] slo(Object ignored/* force ignore by config processor */) {
            var slo = this.slo();
            if (slo != null) {
                return slo;
            }
            return switch (spec()) {
                case V120 -> DEFAULT_SLO;
                case V123 -> DEFAULT_SLO_V123;
            };
        }
    }
}
