package io.koraframework.bpmn.operaton.engine.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface OperatonEngineTelemetryConfig extends TelemetryConfig {

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
    interface CamundaEngineMetricsConfig extends MetricsConfig {

        default boolean engineMetrics() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface CamundaEngineTracingConfig extends TracingConfig {}
}
