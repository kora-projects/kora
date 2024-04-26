package ru.tinkoff.kora.bpmn.camunda7.rest;

import jakarta.ws.rs.core.Application;
import org.camunda.bpm.engine.rest.impl.CamundaRestResources;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.HashSet;
import java.util.Set;

public interface Camunda7RestModule {

    default Camunda7RestConfig camundaRestConfig(Config config, ConfigValueExtractor<Camunda7RestConfig> extractor) {
        return extractor.extract(config.get("camunda7.rest"));
    }

    @Tag(Camunda7Rest.class)
    default Application camundaRestApplication(All<Camunda7Resource> camundaResources) {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                var set = new HashSet<Class<?>>();
                set.addAll(CamundaRestResources.getResourceClasses());
                set.addAll(CamundaRestResources.getConfigurationClasses());

                for (Camunda7Resource camundaResource : camundaResources) {
                    set.addAll(camundaResource.resources());
                }

//                set.add(CamundaAllowAllCorsFilter.class);
                return set;
            }
        };
    }
}
