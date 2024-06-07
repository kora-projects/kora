package ru.tinkoff.kora.camunda.engine.transaction;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.jdbc.RuntimeSqlException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

public class JdbcCamundaTransactionManager implements CamundaTransactionManager {

    private final Context.Key<Connection> camundaConnectionKey = new Context.Key<>() {
        @Override
        protected Connection copy(Connection object) {
            return null;
        }
    };

    private final DataSource dataSource;

    public JdbcCamundaTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TransactionConnection currentConnection() {
        return new TransactionConnection() {
            @Override
            public void commit() throws RuntimeSqlException {
                try {
                    var ctx = Context.current();
                    Connection connection = ctx.get(camundaConnectionKey);
                    if (connection != null) {
                        connection.commit();
                    }
                } catch (SQLException e) {
                    throw new RuntimeSqlException(e);
                }
            }

            @Override
            public void rollback() throws RuntimeSqlException {
                try {
                    var ctx = Context.current();
                    Connection connection = ctx.get(camundaConnectionKey);
                    if (connection != null) {
                        connection.rollback();
                    }
                } catch (SQLException e) {
                    throw new RuntimeSqlException(e);
                }
            }
        };
    }

    @Override
    public <T> T inContinueTx(Supplier<T> supplier) {
        var ctx = Context.current();

        var currentConnection = ctx.get(this.camundaConnectionKey);
        if (currentConnection != null) {
            boolean isClosed;
            try {
                isClosed = currentConnection.isClosed();
            } catch (SQLException e) {
                isClosed = true;
            }

            if (!isClosed) {
                try {
                    return processSupplier(currentConnection, supplier);
                } catch (SQLException e) {
                    throw new RuntimeSqlException(e);
                }
            }
        }

        return inNewTx(supplier);
    }

    @Override
    public void inContinueTx(Runnable runnable) {
        inContinueTx(() -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public <T> T inNewTx(Supplier<T> supplier) {
        var ctx = Context.current();
        try (var connection = ctx.set(this.camundaConnectionKey, this.dataSource.getConnection())) {
            return processSupplier(connection, supplier);
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            ctx.remove(this.camundaConnectionKey);
        }
    }

    @Override
    public void inNewTx(Runnable runnable) {
        inNewTx(() -> {
            runnable.run();
            return null;
        });
    }

    private <T> T processSupplier(Connection connection, Supplier<T> supplier) throws SQLException {
        boolean isAutoCommit = connection.getAutoCommit();
        if (isAutoCommit) {
            connection.setAutoCommit(false);
        }

        try {
            return supplier.get();
        } finally {
            if (isAutoCommit) {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(true);
                }
            }
        }
    }
}
