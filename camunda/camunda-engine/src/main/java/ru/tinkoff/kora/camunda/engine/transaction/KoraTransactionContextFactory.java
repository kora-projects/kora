package ru.tinkoff.kora.camunda.engine.transaction;

import org.camunda.bpm.engine.impl.cfg.TransactionContext;
import org.camunda.bpm.engine.impl.cfg.TransactionContextFactory;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;

public final class KoraTransactionContextFactory implements TransactionContextFactory {

    private final CamundaTransactionManager transactionManager;

    public KoraTransactionContextFactory(CamundaTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public TransactionContext openTransactionContext(CommandContext commandContext) {
        return new KoraTransactionContext(commandContext, transactionManager);
    }
}
