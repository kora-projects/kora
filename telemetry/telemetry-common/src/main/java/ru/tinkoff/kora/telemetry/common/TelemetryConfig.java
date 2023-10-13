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

        @Nullable
        Boolean enabled();

        default double[] slo() {
            return DEFAULT_SLO;
        }
    }
}
