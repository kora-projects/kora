package io.koraframework.bpmn.operaton.rest;

import io.koraframework.bpmn.operaton.rest.telemetry.impl.DefaultOperatonRestLoggerFactory;
import io.koraframework.bpmn.operaton.rest.telemetry.impl.DefaultOperatonRestMetricsFactory;
import io.koraframework.bpmn.operaton.rest.telemetry.impl.DefaultOperatonRestTelemetryFactory;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.ws.rs.core.Application;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jspecify.annotations.Nullable;
import org.operaton.bpm.engine.rest.impl.OperatonRestResources;

import java.util.HashSet;
import java.util.Set;

public interface OperatonRestModule {

    @DefaultComponent
    default OperatonRestTelemetryFactory operatonRestTelemetryFactory(@Nullable Tracer tracer,
                                                                      @Nullable MeterRegistry meterRegistry,
                                                                      @Nullable DefaultOperatonRestLoggerFactory loggerFactory,
                                                                      @Nullable DefaultOperatonRestMetricsFactory metricsFactory) {
        return new DefaultOperatonRestTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    default OperatonRestConfig operatonRestConfig(Config config, ConfigValueExtractor<OperatonRestConfig> extractor) {
        return extractor.extract(config.get("operaton.rest"));
    }

    @Tag(OperatonRest.class)
    default Application operatonRestApplication() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                var set = new HashSet<Class<?>>();
                set.addAll(OperatonRestResources.getResourceClasses());
                set.addAll(OperatonRestResources.getConfigurationClasses());
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
