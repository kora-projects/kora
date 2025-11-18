package ru.tinkoff.kora.camunda.rest;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.Application;
import org.camunda.bpm.engine.rest.impl.CamundaRestResources;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTelemetryFactory;
import ru.tinkoff.kora.camunda.rest.telemetry.DefaultCamundaRestTelemetryFactory;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.HashSet;
import java.util.Set;

public interface CamundaRestModule {

    @DefaultComponent
    default CamundaRestTelemetryFactory camundaRestTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        return new DefaultCamundaRestTelemetryFactory(meterRegistry, tracer);
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
        };
    }
}
