package io.koraframework.camunda.engine.bpmn.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface CamundaEngineTelemetryConfig extends TelemetryConfig {

    @Override
    CamundaEngineLoggerConfig logging();

    @Override
    CamundaEngineMetricsConfig metrics();

    @Override
    CamundaEngineTracingConfig tracing();

    @ConfigValueExtractor
    interface CamundaEngineLoggerConfig extends LoggingConfig {

        default boolean stacktrace() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface CamundaEngineMetricsConfig extends TelemetryConfig.MetricsConfig {

        default boolean engineMetrics() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface CamundaEngineTracingConfig extends TelemetryConfig.TracingConfig {}
}
