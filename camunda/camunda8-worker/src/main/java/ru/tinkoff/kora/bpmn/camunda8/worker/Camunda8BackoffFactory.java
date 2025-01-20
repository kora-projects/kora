package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import ru.tinkoff.kora.bpmn.camunda8.worker.Camunda8WorkerConfig.BackoffConfig;

public interface Camunda8BackoffFactory {

    BackoffSupplier build(BackoffConfig config);
}
