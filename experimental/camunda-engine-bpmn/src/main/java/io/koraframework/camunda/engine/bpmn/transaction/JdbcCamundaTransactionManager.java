package io.koraframework.camunda.engine.bpmn.transaction;

import io.koraframework.database.jdbc.exception.UncheckedSqlException;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class JdbcCamundaTransactionManager implements CamundaTransactionManager {

    private final ScopedValue<Connection> camundaConnectionKey = ScopedValue.newInstance();

    private final DataSource dataSource;

    public JdbcCamundaTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @return DataSource for the engine: inside a transaction of this manager it returns the transaction connection
     * (its close() does nothing, the manager closes it), otherwise it returns a connection from the original DataSource
     */
    public DataSource managedDataSource() {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return camundaConnectionKey.isBound()
                    ? nonClosingConnection(camundaConnectionKey.get())
                    : dataSource.getConnection();
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return camundaConnectionKey.isBound()
                    ? nonClosingConnection(camundaConnectionKey.get())
                    : dataSource.getConnection(username, password);
            }

            @Override
            public PrintWriter getLogWriter() throws SQLException {
                return dataSource.getLogWriter();
            }

            @Override
            public void setLogWriter(PrintWriter out) throws SQLException {
                dataSource.setLogWriter(out);
            }

            @Override
            public void setLoginTimeout(int seconds) throws SQLException {
                dataSource.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() throws SQLException {
                return dataSource.getLoginTimeout();
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                return dataSource.getParentLogger();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                return iface.isInstance(dataSource) ? iface.cast(dataSource) : dataSource.unwrap(iface);
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws SQLException {
                return iface.isInstance(dataSource) || dataSource.isWrapperFor(iface);
            }
        };
    }

    private static Connection nonClosingConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("close") && method.getParameterCount() == 0) {
                return null;
            }
            try {
                return method.invoke(connection, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    @Override
    public TransactionConnection currentConnection() {
        return new TransactionConnection() {
            @Override
            public void commit() throws UncheckedSqlException {
                try {
                    if (camundaConnectionKey.isBound()) {
                        camundaConnectionKey.get().commit();
                    }
                } catch (SQLException e) {
                    throw new UncheckedSqlException(e);
                }
            }

            @Override
            public void rollback() throws UncheckedSqlException {
                try {
                    if (camundaConnectionKey.isBound()) {
                        camundaConnectionKey.get().rollback();
                    }
                } catch (SQLException e) {
                    throw new UncheckedSqlException(e);
                }
            }
        };
    }

    @Override
    public <T> T inContinueTx(Supplier<T> supplier) {
        if (this.camundaConnectionKey.isBound()) {
            var currentConnection = this.camundaConnectionKey.get();
            boolean isClosed;
            try {
                isClosed = currentConnection.isClosed();
            } catch (SQLException e) {
                isClosed = true;
            }

            if (!isClosed) {
                try {
                    return processSupplier(currentConnection, supplier, false);
                } catch (SQLException e) {
                    throw new UncheckedSqlException(e);
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
        try (var connection = this.dataSource.getConnection()) {
            return ScopedValue.where(this.camundaConnectionKey, connection).call(() -> processSupplier(connection, supplier, true));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public void inNewTx(Runnable runnable) {
        inNewTx(() -> {
            runnable.run();
            return null;
        });
    }

    private <T> T processSupplier(Connection connection, Supplier<T> supplier, boolean newTx) throws SQLException {
        boolean isAutoCommit = connection.getAutoCommit();
        if (isAutoCommit) {
            connection.setAutoCommit(false);
        }

        try {
            T result = supplier.get();
            if (newTx) {
                connection.commit();
            }
            return result;
        } catch (Throwable e) {
            if (newTx) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }
            throw e;
        } finally {
            if (isAutoCommit) {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(true);
                }
            }
        }
    }
}
