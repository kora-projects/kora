package ru.tinkoff.kora.bpmn.camunda7.rest;

import jakarta.ws.rs.core.Application;
import org.camunda.bpm.engine.rest.impl.CamundaRestResources;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.HashSet;
import java.util.Set;

public interface CamundaRestModule {

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
//                set.add(CamundaAllowAllCorsFilter.class);
                return set;
            }
        };
    }
}
