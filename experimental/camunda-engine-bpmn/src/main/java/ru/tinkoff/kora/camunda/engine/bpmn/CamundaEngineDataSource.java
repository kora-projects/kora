package ru.tinkoff.kora.camunda.engine.bpmn;

import ru.tinkoff.kora.camunda.engine.bpmn.transaction.CamundaTransactionManager;

import javax.sql.DataSource;

public interface CamundaEngineDataSource {

    CamundaTransactionManager transactionManager();

    DataSource dataSource();
}
