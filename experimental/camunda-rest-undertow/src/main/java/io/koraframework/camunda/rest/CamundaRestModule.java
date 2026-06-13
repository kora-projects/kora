package io.koraframework.camunda.rest;

import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetryFactory;
import io.koraframework.camunda.rest.telemetry.impl.DefaultCamundaRestLoggerFactory;
import io.koraframework.camunda.rest.telemetry.impl.DefaultCamundaRestMetricsFactory;
import io.koraframework.camunda.rest.telemetry.impl.DefaultCamundaRestTelemetryFactory;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.ws.rs.core.Application;
import org.camunda.bpm.engine.rest.impl.CamundaRestResources;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Use module: `io.koraframework:experimental.operaton.rest.undertow` as a replacement for deprecated Camunda 7 engine
 * <a href="https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/">Camunda 7 EOL</a>
 * <a href="https://operaton.org/">Operaton BPMN Engine</a>
 * <a href="https://github.com/operaton/migrate-from-camunda-recipe">OpenRewrite Migration Recipe</a>
 */
@Deprecated
public interface CamundaRestModule {

    @DefaultComponent
    default CamundaRestTelemetryFactory camundaRestTelemetryFactory(@Nullable Tracer tracer,
                                                                   @Nullable MeterRegistry meterRegistry,
                                                                   @Nullable DefaultCamundaRestLoggerFactory loggerFactory,
                                                                   @Nullable DefaultCamundaRestMetricsFactory metricsFactory) {
        return new DefaultCamundaRestTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    default CamundaRestConfig camundaRestConfig(Config config, ConfigValueExtractor<CamundaRestConfig> extractor) {
        return extractor.extract(config.get("camunda.rest"));
    }

    @Tag(CamundaRest.class)
    default Application camundaRestApplication() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                var set = new HashSet<Class<?>>();
                set.addAll(CamundaRestResources.getResourceClasses());
                set.addAll(CamundaRestResources.getConfigurationClasses());
                return set;
            }

            @Override
            public Set<Object> getSingletons() {
                return Set.of(
                    new ResteasyJackson2Provider()
                );
            }
        };
    }
}
