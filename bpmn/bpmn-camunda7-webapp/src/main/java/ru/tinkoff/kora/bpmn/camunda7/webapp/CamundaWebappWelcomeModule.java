package ru.tinkoff.kora.bpmn.camunda7.webapp;

import org.camunda.bpm.welcome.impl.web.WelcomeApplication;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaResource;

import java.util.Set;

public interface CamundaWebappWelcomeModule {

    default CamundaResource camundaWebappWelcomeResource() {
        return () -> Set.of(WelcomeApplication.class);
    }
}
