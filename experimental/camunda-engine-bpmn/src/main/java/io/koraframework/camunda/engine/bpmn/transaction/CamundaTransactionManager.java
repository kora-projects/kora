package io.koraframework.camunda.engine.bpmn.transaction;

import io.koraframework.database.jdbc.RuntimeSqlException;

import java.util.function.Supplier;

public interface CamundaTransactionManager {

    <T> T inContinueTx(Supplier<T> supplier) throws RuntimeSqlException;

    void inContinueTx(Runnable runnable) throws RuntimeSqlException;

    <T> T inNewTx(Supplier<T> supplier) throws RuntimeSqlException;

    void inNewTx(Runnable runnable) throws RuntimeSqlException;

    TransactionConnection currentConnection();

    interface TransactionConnection {

        void commit() throws RuntimeSqlException;

        void rollback() throws RuntimeSqlException;
    }
}
