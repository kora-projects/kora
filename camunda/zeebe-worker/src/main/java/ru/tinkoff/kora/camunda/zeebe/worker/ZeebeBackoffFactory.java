package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import ru.tinkoff.kora.camunda.zeebe.worker.ZeebeWorkerConfig.BackoffConfig;

public interface ZeebeBackoffFactory {

    BackoffSupplier build(BackoffConfig config);
}
