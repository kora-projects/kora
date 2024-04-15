package ru.tinkoff.kora.bpmn.camunda7.webapp;

import org.camunda.bpm.cockpit.impl.web.CockpitApplication;
import ru.tinkoff.kora.bpmn.camunda7.engine.KoraProcessEngineConfiguration;
import ru.tinkoff.kora.bpmn.camunda7.engine.configurator.CamundaConfigurator;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaResource;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;

import java.util.Set;

public interface CamundaWebappCockpitModule {

    default CamundaResource camundaWebappCockpitResource() {
        return () -> Set.of(CockpitApplication.class);
    }

    default CamundaConfigurator camundaWebappCockpitConfigurator(JdbcConnectionFactory jdbcConnectionFactory, KoraProcessEngineConfiguration processEngineConfiguration) {
        return new CockpitRuntimeCamundaConfigurator(jdbcConnectionFactory, processEngineConfiguration);
    }
}
