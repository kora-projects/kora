package io.koraframework.camunda.engine.bpmn;

import io.koraframework.camunda.engine.bpmn.transaction.CamundaTransactionManager;

import javax.sql.DataSource;

public interface CamundaEngineDataSource {

    CamundaTransactionManager transactionManager();

    DataSource dataSource();
}
