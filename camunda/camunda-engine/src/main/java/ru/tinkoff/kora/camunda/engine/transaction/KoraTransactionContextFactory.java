package ru.tinkoff.kora.camunda.engine.transaction;

import org.camunda.bpm.engine.impl.cfg.TransactionContext;
import org.camunda.bpm.engine.impl.cfg.TransactionContextFactory;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;

public final class KoraTransactionContextFactory implements TransactionContextFactory {

    private final JdbcConnectionFactory jdbcConnectionFactory;

    public KoraTransactionContextFactory(JdbcConnectionFactory jdbcConnectionFactory) {
        this.jdbcConnectionFactory = jdbcConnectionFactory;
    }

    @Override
    public TransactionContext openTransactionContext(CommandContext commandContext) {
        return new KoraTransactionContext(commandContext, jdbcConnectionFactory);
    }
}
