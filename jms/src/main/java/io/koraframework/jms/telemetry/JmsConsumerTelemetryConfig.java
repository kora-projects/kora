package io.koraframework.jms.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface JmsConsumerTelemetryConfig extends TelemetryConfig {

    @Override
    JmsConsumerLoggingConfig logging();

    @Override
    JmsConsumerMetricsConfig metrics();

    @Override
    JmsConsumerTracingConfig tracing();

    @ConfigMapper
    interface JmsConsumerLoggingConfig extends TelemetryConfig.LoggingConfig { }

    @ConfigMapper
    interface JmsConsumerMetricsConfig extends TelemetryConfig.MetricsConfig { }

    @ConfigMapper
    interface JmsConsumerTracingConfig extends TelemetryConfig.TracingConfig { }
}
