package io.koraframework.bpmn.operaton.engine.transaction;

import org.operaton.bpm.engine.impl.interceptor.Command;
import org.operaton.bpm.engine.impl.interceptor.CommandInterceptor;

public final class KoraTransactionInterceptor extends CommandInterceptor {

    private final OperatonTransactionManager transactionManager;
    private final boolean requiresNew;

    public KoraTransactionInterceptor(OperatonTransactionManager transactionManager, boolean requiresNew) {
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
