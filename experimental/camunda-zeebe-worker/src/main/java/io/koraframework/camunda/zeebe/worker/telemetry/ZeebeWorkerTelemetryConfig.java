package io.koraframework.camunda.zeebe.worker.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface ZeebeWorkerTelemetryConfig extends TelemetryConfig {

    @Override
    ZeebeWorkerLoggingConfig logging();

    @Override
    ZeebeWorkerMetricsConfig metrics();

    @Override
    ZeebeWorkerTracingConfig tracing();

    @ConfigValueExtractor
    interface ZeebeWorkerLoggingConfig extends LoggingConfig { }

    @ConfigValueExtractor
    interface ZeebeWorkerMetricsConfig extends MetricsConfig { }

    @ConfigValueExtractor
    interface ZeebeWorkerTracingConfig extends TracingConfig { }
}
