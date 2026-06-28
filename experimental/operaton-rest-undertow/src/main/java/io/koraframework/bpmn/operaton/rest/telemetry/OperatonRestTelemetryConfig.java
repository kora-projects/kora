package io.koraframework.bpmn.operaton.rest.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

@ConfigValueExtractor
public interface OperatonRestTelemetryConfig extends TelemetryConfig {

    @Override
    CamundaRestLoggingConfig logging();

    @Override
    CamundaRestMetricsConfig metrics();

    @Override
    CamundaRestTracingConfig tracing();

    @ConfigValueExtractor
    interface CamundaRestLoggingConfig extends LoggingConfig {

        default boolean stacktrace() {
            return true;
        }

        default Set<String> maskQueries() {
            return Collections.emptySet();
        }

        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
        }

        default String mask() {
            return "***";
        }

        @Nullable
        Boolean pathFull();
    }

    @ConfigValueExtractor
    interface CamundaRestMetricsConfig extends MetricsConfig {}

    @ConfigValueExtractor
    interface CamundaRestTracingConfig extends TracingConfig {}
}
