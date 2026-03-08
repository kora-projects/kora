package io.koraframework.camunda.zeebe.worker;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.koraframework.camunda.zeebe.worker.ZeebeWorkerConfig.BackoffConfig;

public interface ZeebeBackoffFactory {

    BackoffSupplier build(BackoffConfig config);
}
