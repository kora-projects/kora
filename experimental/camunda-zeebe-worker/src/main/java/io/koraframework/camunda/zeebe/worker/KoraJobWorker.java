package io.koraframework.camunda.zeebe.worker;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.koraframework.camunda.zeebe.worker.annotation.JobWorker;
import io.koraframework.camunda.zeebe.worker.exception.JobWorkerException;

import java.util.Collections;
import java.util.List;

public interface KoraJobWorker {

    /**
     * @return {@link JobWorker#value()}
     */
    String type();

    /**
     * If empty list than ALL variables will be fetched (default behavior)
     *
     * @return {@link io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3#fetchVariables(List)}
     */
    default List<String> fetchVariables() {
        return Collections.emptyList();
    }

    FinalCommandStep<?> handle(JobClient client, ActivatedJob job) throws JobWorkerException;
}
