package ru.tinkoff.kora.database.jdbc;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface JdbcConnectionFactory {
    <T> T withConnection(JdbcHelper.SqlFunction1<Connection, T> callback) throws RuntimeSqlException;

    @Nullable
    Connection currentConnection();

    Connection newConnection();

    DataBaseTelemetry telemetry();

    default <T> T query(QueryContext queryContext, JdbcHelper.SqlFunction1<PreparedStatement, T> callback) {
        var telemetry = this.telemetry().createContext(Context.current(), queryContext);
        return withConnection(connection -> {
            try (var ps = connection.prepareStatement(queryContext.sql())) {
                var result = callback.apply(ps);
                telemetry.close(null);
                return result;
            } catch (Exception e) {
                telemetry.close(e);
                throw e;
            }
        });
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
            try {
                var result = callback.apply(connection);
                connection.commit();
                connection.setAutoCommit(true);
                return result;
            } catch (Exception e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                } catch (SQLException sqlException) {
                    e.addSuppressed(sqlException);
                }
                throw e;
            }
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
