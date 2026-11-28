package io.koraframework.camunda.zeebe.worker;

import io.koraframework.common.context.*;

import java.time.*;
import java.util.*;

public interface JobContext {

    KoraContext.Key<JobContext> KEY = KoraContext.Key.create();

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

    static JobContext current() {
        KoraContext current = KoraContext.current();
        return current != null ? current.get(KEY) : null;
    }
}
