package io.koraframework.camunda.engine.bpmn.transaction;

import io.koraframework.database.jdbc.UncheckedSqlException;

import java.util.function.Supplier;

public interface CamundaTransactionManager {

    <T> T inContinueTx(Supplier<T> supplier) throws UncheckedSqlException;

    void inContinueTx(Runnable runnable) throws UncheckedSqlException;

    <T> T inNewTx(Supplier<T> supplier) throws UncheckedSqlException;

    void inNewTx(Runnable runnable) throws UncheckedSqlException;

    TransactionConnection currentConnection();

    interface TransactionConnection {

        void commit() throws UncheckedSqlException;

        void rollback() throws UncheckedSqlException;
    }
}
