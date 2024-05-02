package ru.tinkoff.kora.camunda.engine.transaction;

import org.camunda.bpm.engine.impl.cfg.TransactionContext;
import org.camunda.bpm.engine.impl.cfg.TransactionListener;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;
import ru.tinkoff.kora.database.jdbc.RuntimeSqlException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class KoraTransactionContext implements TransactionContext {

    private static final Logger logger = LoggerFactory.getLogger(KoraTransactionContext.class);

    private final CommandContext commandContext;
    private final JdbcConnectionFactory connectionFactory;

    private final List<TransactionListener> beforeCommit = new CopyOnWriteArrayList<>();
    private final List<TransactionListener> afterCommit = new CopyOnWriteArrayList<>();
    private final List<TransactionListener> beforeRollback = new CopyOnWriteArrayList<>();
    private final List<TransactionListener> afterRollback = new CopyOnWriteArrayList<>();
    private volatile TransactionState lastTransactionState = null;

    public KoraTransactionContext(CommandContext commandContext, JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        this.commandContext = commandContext;
    }

    @Override
    public void commit() {
        try {
            this.lastTransactionState = TransactionState.COMMITTING;
            for (TransactionListener listener : beforeCommit) {
                try {
                    listener.execute(commandContext);
                } catch (Exception e) {
                    logger.warn(e.getMessage());
                }
            }

            Connection connection = connectionFactory.currentConnection();
            connection.commit();

            this.lastTransactionState = TransactionState.COMMITTED;
            for (TransactionListener listener : afterCommit) {
                try {
                    listener.execute(commandContext);
                } catch (Exception e) {
                    logger.warn(e.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void rollback() {
        try {
            this.lastTransactionState = TransactionState.ROLLINGBACK;
            for (TransactionListener listener : beforeRollback) {
                try {
                    listener.execute(commandContext);
                } catch (Exception e) {
                    logger.warn(e.getMessage());
                }
            }

            Connection connection = connectionFactory.currentConnection();
            connection.rollback();

            this.lastTransactionState = TransactionState.ROLLED_BACK;
            for (TransactionListener listener : afterRollback) {
                try {
                    listener.execute(commandContext);
                } catch (Exception e) {
                    logger.warn(e.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public void addTransactionListener(TransactionState transactionState, TransactionListener transactionListener) {
        switch (transactionState) {
            case COMMITTING -> beforeCommit.add(transactionListener);
            case COMMITTED -> afterCommit.add(transactionListener);
            case ROLLINGBACK -> beforeRollback.add(transactionListener);
            case ROLLED_BACK -> afterRollback.add(transactionListener);
        }
    }

    @Override
    public boolean isTransactionActive() {
        return this.lastTransactionState != null
            && this.lastTransactionState != TransactionState.ROLLINGBACK
            && this.lastTransactionState != TransactionState.ROLLED_BACK;
    }
}
