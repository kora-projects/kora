package io.koraframework.camunda.zeebe.worker.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface ZeebeWorkerTelemetryConfig extends TelemetryConfig {

    @Override
    ZeebeWorkerLoggingConfig logging();

    @Override
    ZeebeWorkerMetricsConfig metrics();

    @Override
    ZeebeWorkerTracingConfig tracing();

    @ConfigMapper
    interface ZeebeWorkerLoggingConfig extends LoggingConfig { }

    @ConfigMapper
    interface ZeebeWorkerMetricsConfig extends MetricsConfig { }

    @ConfigMapper
    interface ZeebeWorkerTracingConfig extends TracingConfig { }
}
