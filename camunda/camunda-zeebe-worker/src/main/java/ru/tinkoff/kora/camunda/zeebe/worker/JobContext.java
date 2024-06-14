package ru.tinkoff.kora.camunda.zeebe.worker;

import java.time.Instant;
import java.util.Map;

public interface JobContext {

    long jobKey();

    String jobName();

    String jobType();

    String jobWorker();

    String tenantId();

    String processId();

    long processInstanceKey();

    int processDefinitionVersion();

    long processDefinitionKey();

    String elementId();

    long elementInstanceKey();

    Map<String, String> headers();

    int retryCount();

    Instant deadline();

    long deadlineAsMillis();

    String variablesAsString();
}
