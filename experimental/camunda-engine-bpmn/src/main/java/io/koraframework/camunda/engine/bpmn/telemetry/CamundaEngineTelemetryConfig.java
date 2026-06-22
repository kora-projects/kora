package io.koraframework.camunda.engine.bpmn.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface CamundaEngineTelemetryConfig extends TelemetryConfig {

    @Override
    CamundaEngineLoggingConfig logging();

    @Override
    CamundaEngineMetricsConfig metrics();

    @Override
    CamundaEngineTracingConfig tracing();

    @ConfigValueExtractor
    interface CamundaEngineLoggingConfig extends LoggingConfig {

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
