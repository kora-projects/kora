package io.koraframework.bpmn.operaton.engine;

import io.koraframework.bpmn.operaton.engine.transaction.OperatonTransactionManager;

import javax.sql.DataSource;

public interface OperatonEngineDataSource {

    OperatonTransactionManager transactionManager();

    DataSource dataSource();
}
