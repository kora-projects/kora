package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import ru.tinkoff.kora.camunda.zeebe.worker.annotation.JobWorker;

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
     * @return {@link io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3#fetchVariables(List)}
     */
    default List<String> fetchVariables() {
        return Collections.emptyList();
    }

    FinalCommandStep<?> handle(JobClient client, ActivatedJob job) throws JobWorkerException;
}
