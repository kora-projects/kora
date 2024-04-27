package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;

import java.util.Collections;
import java.util.List;

public interface KoraJobWorker {

    String type();

    default List<String> fetchVariables() {
        return Collections.emptyList();
    }

    FinalCommandStep<?> handle(JobClient client, ActivatedJob job) throws JobWorkerException;
}
