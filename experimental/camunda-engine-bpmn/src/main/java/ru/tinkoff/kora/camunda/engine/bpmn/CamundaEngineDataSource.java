package ru.tinkoff.kora.camunda.engine.bpmn;

import jdk.jfr.Experimental;
import ru.tinkoff.kora.camunda.engine.bpmn.transaction.CamundaTransactionManager;

import javax.sql.DataSource;

@Experimental
public interface CamundaEngineDataSource {

    CamundaTransactionManager transactionManager();

    DataSource dataSource();
}
