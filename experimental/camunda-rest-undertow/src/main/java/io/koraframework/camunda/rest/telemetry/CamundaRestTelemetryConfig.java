package io.koraframework.camunda.rest.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

@ConfigMapper
public interface CamundaRestTelemetryConfig extends TelemetryConfig {

    @Override
    CamundaRestLoggingConfig logging();

    @Override
    CamundaRestMetricsConfig metrics();

    @Override
    CamundaRestTracingConfig tracing();

    @ConfigMapper
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

    @ConfigMapper
    interface CamundaRestMetricsConfig extends MetricsConfig {}

    @ConfigMapper
    interface CamundaRestTracingConfig extends TracingConfig {}
}
