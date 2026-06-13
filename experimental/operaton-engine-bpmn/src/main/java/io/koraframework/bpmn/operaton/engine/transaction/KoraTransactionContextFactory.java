package io.koraframework.bpmn.operaton.engine.transaction;

import org.operaton.bpm.engine.impl.cfg.TransactionContext;
import org.operaton.bpm.engine.impl.cfg.TransactionContextFactory;
import org.operaton.bpm.engine.impl.interceptor.CommandContext;

public final class KoraTransactionContextFactory implements TransactionContextFactory {

    private final OperatonTransactionManager transactionManager;

    public KoraTransactionContextFactory(OperatonTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public TransactionContext openTransactionContext(CommandContext commandContext) {
        return new KoraTransactionContext(commandContext, transactionManager);
    }
}
