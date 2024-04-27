package ru.tinkoff.kora.bpmn.camunda7.engine.transaction;

import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;

public final class KoraTransactionInterceptor extends CommandInterceptor {

    private final JdbcConnectionFactory jdbcConnectionFactory;

    public KoraTransactionInterceptor(JdbcConnectionFactory jdbcConnectionFactory) {
        this.jdbcConnectionFactory = jdbcConnectionFactory;
    }

    //TODO requires new tx not supported cause can't share connection to KoraTransactionContext
    @Override
    public <T> T execute(Command<T> command) {
        return jdbcConnectionFactory.withConnection(c -> {
            boolean isAutoCommit = c.getAutoCommit();
            if (isAutoCommit) {
                c.setAutoCommit(false);
            }

            try {
                return next.execute(command);
            } finally {
                if (isAutoCommit) {
                    c.setAutoCommit(true);
                }
            }
        });
    }
}
