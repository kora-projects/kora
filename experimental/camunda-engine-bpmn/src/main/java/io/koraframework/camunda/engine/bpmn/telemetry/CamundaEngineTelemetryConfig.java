package io.koraframework.camunda.engine.bpmn.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface CamundaEngineTelemetryConfig extends TelemetryConfig {

    @Override
    CamundaEngineLoggingConfig logging();

    @Override
    CamundaEngineMetricsConfig metrics();

    @Override
    CamundaEngineTracingConfig tracing();

    @ConfigMapper
    interface CamundaEngineLoggingConfig extends LoggingConfig {

        default boolean stacktrace() {
            return true;
        }
    }

    @ConfigMapper
    interface CamundaEngineMetricsConfig extends TelemetryConfig.MetricsConfig {

        default boolean engineMetrics() {
            return false;
        }
    }

    @ConfigMapper
    interface CamundaEngineTracingConfig extends TelemetryConfig.TracingConfig {}
}
