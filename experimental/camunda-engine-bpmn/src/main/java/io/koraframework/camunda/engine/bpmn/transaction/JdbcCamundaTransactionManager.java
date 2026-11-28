package io.koraframework.camunda.engine.bpmn.transaction;

import io.koraframework.common.context.*;
import io.koraframework.database.jdbc.exception.*;

import javax.sql.*;
import java.sql.*;
import java.util.function.*;

public class JdbcCamundaTransactionManager implements CamundaTransactionManager {

    public static final KoraContext.Key<Connection> CAMUNDA_CONNECTION_KEY = KoraContext.Key.create();

    private final DataSource dataSource;

    public JdbcCamundaTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TransactionConnection currentConnection() {
        return new TransactionConnection() {
            @Override
            public void commit() throws UncheckedSqlException {
                try {
                    KoraContext current = KoraContext.current();
                    if (current != null) {
                        Connection conn = current.get(CAMUNDA_CONNECTION_KEY);
                        if (conn != null) {
                            conn.commit();
                        }
                    }
                } catch (SQLException e) {
                    throw new UncheckedSqlException(e);
                }
            }

            @Override
            public void rollback() throws UncheckedSqlException {
                try {
                    KoraContext current = KoraContext.current();
                    if (current != null) {
                        Connection conn = current.get(CAMUNDA_CONNECTION_KEY);
                        if (conn != null) {
                            conn.rollback();
                        }
                    }
                } catch (SQLException e) {
                    throw new UncheckedSqlException(e);
                }
            }
        };
    }

    @Override
    public <T> T inContinueTx(Supplier<T> supplier) {
        KoraContext current = KoraContext.current();
        if (current != null) {
            Connection currentConnection = current.get(CAMUNDA_CONNECTION_KEY);
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
                        throw new UncheckedSqlException(e);
                    }
                }
            }
        }

        return inNewTx(supplier);
    }

    @Override
    public void inContinueTx(Runnable runnable) {
        inContinueTx(new RunnableSupplierAdapter(runnable));
    }

    @Override
    public <T> T inNewTx(Supplier<T> supplier) {
        try (var connection = this.dataSource.getConnection()) {
            KoraContext current = KoraContext.current();
            KoraContext updatedContext = (current != null ? current : KoraContext.empty())
                .with(CAMUNDA_CONNECTION_KEY, connection);

            return KoraContext.runWith(updatedContext, new TransactionExecutionRunner<>(this, connection, supplier));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public void inNewTx(Runnable runnable) {
        inNewTx(new RunnableSupplierAdapter(runnable));
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

    private static final class TransactionExecutionRunner<T> implements Supplier<T> {
        private final JdbcCamundaTransactionManager manager;
        private final Connection connection;
        private final Supplier<T> supplier;

        public TransactionExecutionRunner(JdbcCamundaTransactionManager manager, Connection connection, Supplier<T> supplier) {
            this.manager = manager;
            this.connection = connection;
            this.supplier = supplier;
        }

        @Override
        public T get() {
            try {
                return manager.processSupplier(connection, supplier);
            } catch (SQLException e) {
                throw new UncheckedSqlException(e);
            }
        }
    }

    private static final class RunnableSupplierAdapter implements Supplier<Void> {
        private final Runnable runnable;

        public RunnableSupplierAdapter(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public Void get() {
            runnable.run();
            return null;
        }
    }
}
