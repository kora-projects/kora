package ru.tinkoff.kora.camunda.rest;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.Application;
import org.camunda.bpm.engine.rest.impl.CamundaRestResources;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import ru.tinkoff.kora.camunda.rest.telemetry.*;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.HashSet;
import java.util.Set;

public interface CamundaRestModule {

    @DefaultComponent
    default CamundaRestLoggerFactory camundaRestLoggerFactory() {
        return new Slf4JCamundaRestLoggerFactory();
    }

    @DefaultComponent
    default CamundaRestTelemetryFactory camundaRestTelemetryFactory(@Nullable CamundaRestLoggerFactory logger,
                                                                    @Nullable CamundaRestMetricsFactory metrics,
                                                                    @Nullable CamundaRestTracerFactory tracer) {
        return new DefaultCamundaRestTelemetryFactory(logger, metrics, tracer);
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
