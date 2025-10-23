package ru.tinkoff.kora.database.jdbc;

import io.opentelemetry.context.Context;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.jdbc.ConnectionContext.PostCommitAction;
import ru.tinkoff.kora.database.jdbc.ConnectionContext.PostRollbackAction;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * <b>Русский</b>: Фабрика соединений JDBC которая позволяет выполнять запросы в ручном режиме и в рамках транзакции.
 * <hr>
 * <b>English</b>: JDBC's connection factory that allows you to fulfil requests in manual mode or transaction mode.
 *
 * @see JdbcRepository
 */
@SuppressWarnings("overloads")
public interface JdbcConnectionFactory {

    <T> T withConnection(JdbcHelper.SqlFunction1<Connection, T> callback) throws RuntimeSqlException;

    @Nullable
    Connection currentConnection();

    @Nullable
    default ConnectionContext currentConnectionContext() {
        return null;
    }

    Connection newConnection();

    DataBaseTelemetry telemetry();

    default <T> T query(QueryContext queryContext, JdbcHelper.SqlFunction1<PreparedStatement, T> callback) {
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

    default <T> T withConnection(JdbcHelper.SqlFunction0<T> callback) throws RuntimeSqlException {
        return this.withConnection(connection -> {
            return callback.apply();
        });
    }

    default void withConnection(JdbcHelper.SqlConsumer<Connection> callback) throws RuntimeSqlException {
        this.withConnection(connection -> {
            callback.accept(connection);
            return null;
        });
    }

    default void withConnection(JdbcHelper.SqlRunnable callback) throws RuntimeSqlException {
        this.withConnection(connection -> {
            callback.run();
            return null;
        });
    }

    default <T> T inTx(JdbcHelper.SqlFunction1<Connection, T> callback) throws RuntimeSqlException {
        return this.withConnection(connection -> {
            if (!connection.getAutoCommit()) {
                return callback.apply(connection);
            }
            connection.setAutoCommit(false);
            T result;
            try {
                result = callback.apply(connection);
                connection.commit();
                connection.setAutoCommit(true);
            } catch (Exception e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                    for (PostRollbackAction action : currentConnectionContext().postRollbackActions()) {
                        action.run(connection, e);
                    }
                } catch (Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
            for (PostCommitAction action : currentConnectionContext().postCommitActions()) {
                action.run(connection);
            }
            return result;
        });
    }

    default <T> T inTx(JdbcHelper.SqlFunction0<T> callback) throws RuntimeSqlException {
        return this.inTx(connection -> {
            return callback.apply();
        });
    }

    default void inTx(JdbcHelper.SqlConsumer<Connection> callback) throws RuntimeSqlException {
        this.inTx(connection -> {
            callback.accept(connection);
            return null;
        });
    }

    default void inTx(JdbcHelper.SqlRunnable callback) throws RuntimeSqlException {
        this.inTx(connection -> {
            callback.run();
            return null;
        });
    }
}
