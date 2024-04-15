package ru.tinkoff.kora.bpmn.camunda7.webapp;

import org.camunda.bpm.admin.impl.web.AdminApplication;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaResource;

import java.util.Set;

public interface CamundaWebappAdminModule {

    default CamundaResource camundaWebappAdminResource() {
        return () -> Set.of(AdminApplication.class);
    }
}
