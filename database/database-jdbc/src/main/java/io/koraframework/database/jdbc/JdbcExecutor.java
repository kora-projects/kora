package io.koraframework.database.jdbc;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.jdbc.ConnectionContext.PostCommitAction;
import io.koraframework.database.jdbc.ConnectionContext.PostRollbackAction;
import io.koraframework.database.jdbc.exception.UncheckedSqlException;
import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import io.opentelemetry.context.Context;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * <b>Русский</b>: Фабрика соединений JDBC которая позволяет выполнять запросы в ручном режиме и в рамках транзакции.
 * <hr>
 * <b>English</b>: JDBC's connection factory that allows you to fulfil requests in manual mode or transaction mode.
 *
 * @see JdbcRepository
 */
@SuppressWarnings("overloads")
public interface JdbcExecutor {

    /**
     * <b>Русский</b>: Уровень изоляции JDBC транзакции.
     * В большинстве баз данных уровень по умолчанию - {@link #READ_COMMITTED}, но точное значение зависит от JDBC драйвера и настроек базы данных.
     * <hr>
     * <b>English</b>: JDBC transaction isolation level.
     * Most databases use {@link #READ_COMMITTED} by default, but the exact value depends on the JDBC driver and database settings.
     *
     * @see Connection#setTransactionIsolation(int)
     */
    enum TxIsolation {

        READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
        READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
        REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
        SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

        private final int value;

        TxIsolation(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }
    }

    /**
     * <b>Русский</b>: Выполняет callback с текущим или новым контекстом соединения.
     * <hr>
     * <b>English</b>: Executes a callback with the current or a new connection context.
     *
     * @param callback callback с контекстом соединения / callback with connection context
     * @return результат callback / callback result
     */
    <T> T withContext(SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException;

    /**
     * <b>Русский</b>: Выполняет callback с текущим или новым JDBC соединением.
     * <hr>
     * <b>English</b>: Executes a callback with the current or a new JDBC connection.
     *
     * @param callback callback с JDBC соединением / callback with JDBC connection
     * @return результат callback / callback result
     */
    default <T> T withConnection(SqlFunction<Connection, T> callback) throws UncheckedSqlException {
        return withContext(context -> callback.apply(context.connection()));
    }

    Connection acquireConnection();

    @Nullable
    default Connection currentConnection() {
        var ctx = currentContext();
        return ctx == null ? null : ctx.connection();
    }

    @Nullable
    ConnectionContext currentContext();

    DatabaseTelemetry telemetry();

    /**
     * <b>Русский</b>: Выполняет SQL из {@link QueryContext}, создает {@link PreparedStatement} и оборачивает выполнение в телеметрию.
     * <hr>
     * <b>English</b>: Executes SQL from {@link QueryContext}, creates a {@link PreparedStatement}, and wraps execution with telemetry.
     *
     * @param queryContext контекст запроса / query context
     * @param callback     callback с {@link PreparedStatement} / callback with {@link PreparedStatement}
     * @return результат callback / callback result
     */
    default <T> T query(QueryContext queryContext, SqlFunction<PreparedStatement, T> callback) {
        var observation = this.telemetry().observe(queryContext);
        return ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> withConnection(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(queryContext.sql())) {
                    return callback.apply(ps);
                } catch (Exception e) {
                    observation.observeError(e);
                    throw e;
                } finally {
                    observation.end();
                }
            }));
    }

    /**
     * <b>Русский</b>: Выполняет готовый {@link JdbcQuery}, создает {@link PreparedStatement}, проставляет параметры и оборачивает выполнение в телеметрию.
     * <hr>
     * <b>English</b>: Executes a built {@link JdbcQuery}, creates a {@link PreparedStatement}, binds parameters, and wraps execution with telemetry.
     *
     * @param query    готовый JDBC запрос / built JDBC query
     * @param callback callback с {@link PreparedStatement} / callback with {@link PreparedStatement}
     * @return результат callback / callback result
     * @see JdbcQuery#prepare(Connection)
     */
    default <T> T query(JdbcQuery query, SqlFunction<PreparedStatement, T> callback) {
        var queryContext = new QueryContext(query.sourceSql(), query.sql());
        var observation = this.telemetry().observe(queryContext);
        return ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> withConnection(connection -> {
                try (PreparedStatement ps = query.prepare(connection)) {
                    return callback.apply(ps);
                } catch (Exception e) {
                    observation.observeError(e);
                    throw e;
                } finally {
                    observation.end();
                }
            }));
    }

    /**
     * <b>Русский</b>: Выполняет готовый {@link JdbcQuery} и преобразует {@link java.sql.ResultSet} указанным маппером.
     * <hr>
     * <b>English</b>: Executes a built {@link JdbcQuery} and maps {@link java.sql.ResultSet} with the provided mapper.
     *
     * @param query  готовый JDBC запрос / built JDBC query
     * @param mapper маппер результата / result mapper
     * @return результат маппера / mapper result
     */
    default <T> T query(JdbcQuery query, JdbcResultSetMapper<T> mapper) {
        return this.query(query, (SqlFunction<PreparedStatement, T>) statement -> {
            try (var resultSet = statement.executeQuery()) {
                return mapper.apply(resultSet);
            }
        });
    }

    /**
     * <b>Русский</b>: Выполняет готовый {@link JdbcQuery} и возвращает одну строку, преобразованную указанным маппером.
     * <hr>
     * <b>English</b>: Executes a built {@link JdbcQuery} and returns one row mapped with the provided mapper.
     *
     * @param query  готовый JDBC запрос / built JDBC query
     * @param mapper маппер строки / row mapper
     * @return одна строка или {@code null} / one row or {@code null}
     */
    @Nullable
    default <T> T queryOne(JdbcQuery query, JdbcRowMapper<T> mapper) {
        return this.query(query, JdbcResultSetMapper.singleResultSetMapper(mapper));
    }

    /**
     * <b>Русский</b>: Выполняет готовый {@link JdbcQuery} и возвращает одну опциональную строку.
     * <hr>
     * <b>English</b>: Executes a built {@link JdbcQuery} and returns one optional row.
     *
     * @param query  готовый JDBC запрос / built JDBC query
     * @param mapper маппер строки / row mapper
     * @return опциональная строка / optional row
     */
    default <T> Optional<T> queryOptional(JdbcQuery query, JdbcRowMapper<T> mapper) {
        return this.query(query, JdbcResultSetMapper.optionalResultSetMapper(mapper));
    }

    /**
     * <b>Русский</b>: Выполняет готовый {@link JdbcQuery} и возвращает список строк.
     * <hr>
     * <b>English</b>: Executes a built {@link JdbcQuery} and returns a list of rows.
     *
     * @param query  готовый JDBC запрос / built JDBC query
     * @param mapper маппер строки / row mapper
     * @return список строк / row list
     */
    default <T> List<T> queryList(JdbcQuery query, JdbcRowMapper<T> mapper) {
        return this.query(query, JdbcResultSetMapper.listResultSetMapper(mapper));
    }

    default <T> T withConnection(SqlSupplier<T> callback) throws UncheckedSqlException {
        return this.withConnection(_ -> {
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
        this.withConnection(_ -> {
            callback.run();
            return null;
        });
    }

    default <T> T inTx(SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException {
        return this.doInTx(null, callback);
    }

    /**
     * <b>Русский</b>: Выполняет callback в транзакции с указанным уровнем изоляции.
     * Если транзакция уже открыта, используется текущая транзакция.
     * <hr>
     * <b>English</b>: Executes a callback in a transaction with the specified isolation level.
     * If a transaction is already open, the current transaction is used.
     *
     * @param isolationLevel уровень изоляции / isolation level
     * @param callback       callback с контекстом соединения / callback with connection context
     * @return результат callback / callback result
     */
    default <T> T inTx(TxIsolation isolationLevel, SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException {
        Objects.requireNonNull(isolationLevel);
        return this.doInTx(isolationLevel, callback);
    }

    private <T> T doInTx(@Nullable TxIsolation isolationLevel, SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException {
        return this.withContext(ctx -> {
            Connection connection = ctx.connection();
            if (!connection.getAutoCommit()) {
                return callback.apply(ctx);
            }

            int previousIsolationLevel = connection.getTransactionIsolation();
            boolean isolationLevelChanged = false;
            T result;
            try {
                if (isolationLevel != null) {
                    connection.setTransactionIsolation(isolationLevel.value());
                    isolationLevelChanged = true;
                }
                connection.setAutoCommit(false);
                result = callback.apply(ctx);
                connection.commit();
                connection.setAutoCommit(true);
            } catch (Exception e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                    for (PostRollbackAction action : ctx.postRollbackActions()) {
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
            } finally {
                if (isolationLevelChanged) {
                    connection.setTransactionIsolation(previousIsolationLevel);
                }
            }
            for (PostCommitAction action : ctx.postCommitActions()) {
                action.run(connection);
            }
            return result;
        });
    }

    default <T> T inTx(SqlSupplier<T> callback) throws UncheckedSqlException {
        return this.inTx(_ -> {
            return callback.apply();
        });
    }

    default <T> T inTx(TxIsolation isolationLevel, SqlSupplier<T> callback) throws UncheckedSqlException {
        return this.inTx(isolationLevel, _ -> {
            return callback.apply();
        });
    }

    default void inTx(SqlConsumer<ConnectionContext> callback) throws UncheckedSqlException {
        this.inTx(ctx -> {
            callback.accept(ctx);
            return null;
        });
    }

    default void inTx(TxIsolation isolationLevel, SqlConsumer<ConnectionContext> callback) throws UncheckedSqlException {
        this.inTx(isolationLevel, ctx -> {
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

    default void inTx(TxIsolation isolationLevel, SqlRunnable callback) throws UncheckedSqlException {
        this.inTx(isolationLevel, _ -> {
            callback.run();
            return null;
        });
    }

    /**
     * <b>Русский</b>: JDBC callback без аргументов, который возвращает результат.
     * <hr>
     * <b>English</b>: JDBC callback without arguments that returns a result.
     */
    interface SqlSupplier<T> {
        T apply() throws SQLException;
    }

    /**
     * <b>Русский</b>: JDBC callback, который принимает аргумент и возвращает результат.
     * <hr>
     * <b>English</b>: JDBC callback that accepts an argument and returns a result.
     */
    interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    /**
     * <b>Русский</b>: JDBC callback, который принимает аргумент и не возвращает результат.
     * <hr>
     * <b>English</b>: JDBC callback that accepts an argument and returns no result.
     */
    interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }

    /**
     * <b>Русский</b>: JDBC callback без аргументов и результата.
     * <hr>
     * <b>English</b>: JDBC callback without arguments and result.
     */
    interface SqlRunnable {
        void run() throws SQLException;
    }
}
