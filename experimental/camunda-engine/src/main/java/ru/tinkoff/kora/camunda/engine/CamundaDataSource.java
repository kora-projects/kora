package ru.tinkoff.kora.camunda.engine;

import ru.tinkoff.kora.camunda.engine.transaction.CamundaTransactionManager;

import javax.sql.DataSource;

public interface CamundaDataSource {

    CamundaTransactionManager transactionManager();

    DataSource dataSource();
}
