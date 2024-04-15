package ru.tinkoff.kora.bpmn.camunda7.webapp;

import org.camunda.bpm.tasklist.impl.web.TasklistApplication;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaResource;

import java.util.Set;

public interface CamundaWebappTasklistModule {

    default CamundaResource camundaWebappTasklistResource() {
        return () -> Set.of(TasklistApplication.class);
    }
}
