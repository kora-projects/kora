package ru.tinkoff.kora.bpmn.camunda8.worker;

import java.util.Map;

public interface JobContext {

    long jobKey();

    String jobName();

    String jobType();

    String tenantId();

    String processId();

    long processInstanceKey();

    int processDefinitionVersion();

    long processDefinitionKey();

    String elementId();

    long elementInstanceKey();

    Map<String, String> headers();
}
