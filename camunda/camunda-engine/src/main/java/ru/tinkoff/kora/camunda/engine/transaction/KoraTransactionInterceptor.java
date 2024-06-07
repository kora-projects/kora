package ru.tinkoff.kora.camunda.engine.transaction;

import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;

public final class KoraTransactionInterceptor extends CommandInterceptor {

    private final CamundaTransactionManager transactionManager;
    private final boolean requiresNew;

    public KoraTransactionInterceptor(CamundaTransactionManager transactionManager, boolean requiresNew) {
        this.requiresNew = requiresNew;
        this.transactionManager = transactionManager;
    }

    @Override
    public <T> T execute(Command<T> command) {
        if (requiresNew) {
            return transactionManager.inNewTx(() -> next.execute(command));
        } else {
            return transactionManager.inContinueTx(() -> next.execute(command));
        }
    }
}
