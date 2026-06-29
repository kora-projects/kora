package io.koraframework.database.jdbc;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.jdbc.ConnectionContext.PostCommitAction;
import io.koraframework.database.jdbc.ConnectionContext.PostRollbackAction;
import io.opentelemetry.context.Context;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * <b>Русский</b>: Фабрика соединений JDBC которая позволяет выполнять запросы в ручном режиме и в рамках транзакции.
 * <hr>
 * <b>English</b>: JDBC's connection factory that allows you to fulfil requests in manual mode or transaction mode.
 *
 * @see JdbcRepository
 */
@SuppressWarnings("overloads")
public interface JdbcConnectionFactory {

    <T> T withContext(SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException;

    default <T> T withConnection(SqlFunction<Connection, T> callback) throws UncheckedSqlException {
        return withContext(context -> callback.apply(context.connection()));
    }

    Connection acquireConnection();

    @Nullable
    ConnectionContext currentContext();

    DatabaseTelemetry telemetry();

    default <T> T query(QueryContext queryContext, SqlFunction<PreparedStatement, T> callback) {
        var observation = this.telemetry().observe(queryContext);
        return ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> withConnection(connection -> {
                try (var ps = connection.prepareStatement(queryContext.sql())) {
                    return callback.apply(ps);
                } catch (Exception e) {
                    observation.observeError(e);
                    throw e;
                } finally {
                    observation.end();
                }
            }));
    }

    default <T> T withConnection(SqlSupplier<T> callback) throws UncheckedSqlException {
        return this.withConnection(connection -> {
            return callback.apply();
        });
    }

    default void withConnection(SqlConsumer<Connection> callback) throws UncheckedSqlException {
        this.withConnection(connection -> {
            callback.accept(connection);
            return null;
        });
    }

    default void withConnection(SqlRunnable callback) throws UncheckedSqlException {
        this.withConnection(connection -> {
            callback.run();
            return null;
        });
    }

    default <T> T inTx(SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException {
        return this.withConnection(connection -> {
            if (!connection.getAutoCommit()) {
                return callback.apply(currentContext());
            }
            connection.setAutoCommit(false);
            T result;
            try {
                result = callback.apply(currentContext());
                connection.commit();
                connection.setAutoCommit(true);
            } catch (Exception e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                    for (PostRollbackAction action : currentContext().postRollbackActions()) {
                        try {
                            action.run(connection, e);
                        } catch (SQLException ex) {
                            e.addSuppressed(ex);
                        }
                    }
                } catch (Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
            for (PostCommitAction action : currentContext().postCommitActions()) {
                action.run(connection);
            }
            return result;
        });
    }

    default <T> T inTx(SqlSupplier<T> callback) throws UncheckedSqlException {
        return this.inTx(connection -> {
            return callback.apply();
        });
    }

    default void inTx(SqlConsumer<ConnectionContext> callback) throws UncheckedSqlException {
        this.inTx(ctx -> {
            callback.accept(ctx);
            return null;
        });
    }

    default void inTx(SqlRunnable callback) throws UncheckedSqlException {
        this.inTx(_ -> {
            callback.run();
            return null;
        });
    }

    interface SqlSupplier<T> {
        T apply() throws SQLException;
    }

    interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }

    interface SqlRunnable {
        void run() throws SQLException;
    }
}
