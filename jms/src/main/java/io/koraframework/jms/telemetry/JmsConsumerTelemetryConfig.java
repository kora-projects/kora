package io.koraframework.jms.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface JmsConsumerTelemetryConfig extends TelemetryConfig {

    @Override
    JmsConsumerLoggingConfig logging();

    @Override
    JmsConsumerMetricsConfig metrics();

    @Override
    JmsConsumerTracingConfig tracing();

    @ConfigValueExtractor
    interface JmsConsumerLoggingConfig extends TelemetryConfig.LoggingConfig { }

    @ConfigValueExtractor
    interface JmsConsumerMetricsConfig extends TelemetryConfig.MetricsConfig { }

    @ConfigValueExtractor
    interface JmsConsumerTracingConfig extends TelemetryConfig.TracingConfig { }
}
