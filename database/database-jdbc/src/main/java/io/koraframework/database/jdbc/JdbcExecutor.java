package io.koraframework.database.jdbc;

import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.koraframework.database.common.*;
import io.koraframework.database.common.telemetry.*;
import io.koraframework.database.jdbc.ConnectionContext.*;
import io.koraframework.database.jdbc.exception.*;
import io.koraframework.database.jdbc.mapper.result.*;
import org.jspecify.annotations.*;

import java.sql.*;
import java.util.*;
import java.util.function.*;

/**
 * <b>Русский</b>: Фабрика соединений JDBC которая позволяет выполнять запросы в ручном режиме и в рамках транзакции.
 * <hr>
 * <b>English</b>: JDBC's connection factory that allows you to fulfil requests in manual mode or transaction mode.
 *
 * @see JdbcRepository
 */
@SuppressWarnings("overloads")
public interface JdbcExecutor {

    @Nullable
    default Connection currentConnection(KoraContext ctx) {
        return currentConnection();
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
        return withContext(new WithConnectionCallback<>(callback));
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
        var observation = telemetry().observe(queryContext);
        KoraContext current = KoraContext.current();
        if (current != null) {
            var prevObs = current.get(Observation.OBSERVATION_KEY);
            var prevOtel = current.get(OpentelemetryContextStorage.OPENTELEMETRY_KEY);
            Observation.attach(current, observation);
            try {
                return withConnection(new QueryContextConnectionCallback<>(queryContext, callback, observation));
            } finally {
                Observation.detach(current, prevObs, prevOtel);
            }
        }
        if (!observation.span().getSpanContext().isValid()) {
            return withConnection(new QueryContextConnectionCallback<>(queryContext, callback, observation));
        }

        KoraContext updated = Observation.attach(KoraContext.empty(), observation);
        try {
            return KoraContext.runWith(updated, new QueryContextCall<>(this, queryContext, callback, observation));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        KoraContext current = KoraContext.current();
        if (current != null) {
            var prevObs = current.get(Observation.OBSERVATION_KEY);
            var prevOtel = current.get(OpentelemetryContextStorage.OPENTELEMETRY_KEY);
            Observation.attach(current, observation);
            try {
                return this.withConnection(new JdbcQueryConnectionCallback<>(query, callback, observation));
            } finally {
                Observation.detach(current, prevObs, prevOtel);
            }
        }
        KoraContext updated = Observation.attach(KoraContext.empty(), observation);
        try {
            return KoraContext.runWith(updated, new JdbcQueryCall<>(this, query, callback, observation));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <b>Русский</b>: Выполняет готовый {@link JdbcQuery} и преобразует {@link ResultSet} указанным маппером.
     * <hr>
     * <b>English</b>: Executes a built {@link JdbcQuery} and maps {@link ResultSet} with the provided mapper.
     *
     * @param query  готовый JDBC запрос / built JDBC query
     * @param mapper маппер результата / result mapper
     * @return результат маппера / mapper result
     */
    default <T> T query(JdbcQuery query, JdbcResultSetMapper<T> mapper) {
        return this.query(query, new QueryMapperCallback<>(mapper));
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

    /**
     * <b>Русский</b>: Выполняет готовый {@link JdbcQuery} через {@link PreparedStatement#executeUpdate()} и возвращает количество измененных строк.
     * <hr>
     * <b>English</b>: Executes a built {@link JdbcQuery} with {@link PreparedStatement#executeUpdate()} and returns affected rows count.
     *
     * @param query готовый JDBC запрос / built JDBC query
     * @return количество измененных строк / affected rows count
     */
    default UpdateCount executeUpdate(JdbcQuery query) {
        return query(query, ExecuteUpdateCallback.INSTANCE);
    }

    /**
     * <b>Русский</b>: Выполняет готовый {@link JdbcQuery} через {@link PreparedStatement#executeUpdate()} и преобразует сгенерированные ключи.
     * <hr>
     * <b>English</b>: Executes a built {@link JdbcQuery} with {@link PreparedStatement#executeUpdate()} and maps generated keys.
     *
     * @param query               готовый JDBC запрос / built JDBC query
     * @param generatedKeysMapper маппер для {@link PreparedStatement#getGeneratedKeys()} / mapper for {@link PreparedStatement#getGeneratedKeys()}
     * @return результат маппера сгенерированных ключей / generated keys mapper result
     */
    default <T> T executeUpdate(JdbcQuery query, JdbcResultSetMapper<T> generatedKeysMapper) {
        return query(query, new ExecuteUpdateMapperCallback<>(generatedKeysMapper));
    }

    /**
     * <b>Русский</b>: Выполняет готовый JDBC batch и возвращает суммарное количество измененных строк.
     * Если JDBC возвращает {@link Statement#SUCCESS_NO_INFO}, метод возвращает {@code UpdateCount(-1)}.
     * <hr>
     * <b>English</b>: Executes a built JDBC batch and returns total affected rows count.
     * Returns {@code UpdateCount(-1)} when JDBC reports {@link Statement#SUCCESS_NO_INFO}.
     *
     * @param batch готовый JDBC batch / built JDBC batch
     * @return суммарное количество измененных строк / total affected rows count
     * @see PreparedStatement#executeBatch()
     */
    default UpdateCount executeUpdateBatch(JdbcQuery.JdbcQueryBatch batch) {
        var queryContext = new QueryContext(batch.sourceSql(), batch.sql());
        var observation = this.telemetry().observe(queryContext);
        KoraContext current = KoraContext.current();
        if (current != null) {
            var prevObs = current.get(Observation.OBSERVATION_KEY);
            var prevOtel = current.get(OpentelemetryContextStorage.OPENTELEMETRY_KEY);
            Observation.attach(current, observation);
            try {
                return this.withConnection(new ExecuteUpdateBatchConnectionCallback(batch, observation));
            } finally {
                Observation.detach(current, prevObs, prevOtel);
            }
        }
        KoraContext updated = Observation.attach(KoraContext.empty(), observation);
        try {
            return KoraContext.runWith(updated, new ExecuteUpdateBatchCall(this, batch, observation));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <b>Русский</b>: Выполняет готовый JDBC batch и преобразует сгенерированные ключи.
     * <hr>
     * <b>English</b>: Executes a built JDBC batch and maps generated keys.
     *
     * @param batch               готовый JDBC batch / built JDBC batch
     * @param generatedKeysMapper маппер для {@link PreparedStatement#getGeneratedKeys()} / mapper for {@link PreparedStatement#getGeneratedKeys()}
     * @return результат маппера сгенерированных ключей / generated keys mapper result
     * @see PreparedStatement#executeBatch()
     */
    default <T> T executeUpdateBatch(JdbcQuery.JdbcQueryBatch batch, JdbcResultSetMapper<T> generatedKeysMapper) {
        var queryContext = new QueryContext(batch.sourceSql(), batch.sql());
        var observation = this.telemetry().observe(queryContext);
        KoraContext current = KoraContext.current();
        if (current != null) {
            var prevObs = current.get(Observation.OBSERVATION_KEY);
            var prevOtel = current.get(OpentelemetryContextStorage.OPENTELEMETRY_KEY);
            Observation.attach(current, observation);
            try {
                return this.withConnection(new ExecuteUpdateBatchMapperConnectionCallback<>(batch, generatedKeysMapper, observation));
            } finally {
                Observation.detach(current, prevObs, prevOtel);
            }
        }
        KoraContext updated = Observation.attach(KoraContext.empty(), observation);
        try {
            return KoraContext.runWith(updated, new ExecuteUpdateBatchMapperCall<>(this, batch, generatedKeysMapper, observation));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default <T> T withConnection(SqlSupplier<T> callback) throws UncheckedSqlException {
        return this.withConnection(new WithConnectionSupplierCallback<>(callback));
    }

    default void withConnection(SqlConsumer<Connection> callback) throws UncheckedSqlException {
        this.withConnection(new WithConnectionConsumerCallback(callback));
    }

    default void withConnection(SqlRunnable callback) throws UncheckedSqlException {
        this.withConnection(new WithConnectionRunnableCallback(callback));
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

    default <T> T inTx(SqlSupplier<T> callback) throws UncheckedSqlException {
        return this.inTx(new InTxSupplierCallback<>(callback));
    }

    default <T> T inTx(TxIsolation isolationLevel, SqlSupplier<T> callback) throws UncheckedSqlException {
        return this.inTx(isolationLevel, new InTxSupplierCallback<>(callback));
    }

    default void inTx(SqlConsumer<ConnectionContext> callback) throws UncheckedSqlException {
        this.inTx(new InTxConsumerCallback(callback));
    }

    default void inTx(TxIsolation isolationLevel, SqlConsumer<ConnectionContext> callback) throws UncheckedSqlException {
        this.inTx(isolationLevel, new InTxConsumerCallback(callback));
    }

    default void inTx(SqlRunnable callback) throws UncheckedSqlException {
        this.inTx(new InTxRunnableCallback(callback));
    }

    default void inTx(TxIsolation isolationLevel, SqlRunnable callback) throws UncheckedSqlException {
        this.inTx(isolationLevel, new InTxRunnableCallback(callback));
    }

    /**
     * <b>Русский</b>: JDBC callback без аргументов, который возвращает результат.
     * <hr>
     * <b>English</b>: JDBC callback without arguments that returns a result.
     */
    @FunctionalInterface
    interface SqlSupplier<T> {
        T apply() throws SQLException;
    }

    /**
     * <b>Русский</b>: JDBC callback, который принимает аргумент и возвращает результат.
     * <hr>
     * <b>English</b>: JDBC callback that accepts an argument and returns a result.
     */
    @FunctionalInterface
    interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    /**
     * <b>Русский</b>: JDBC callback, который принимает аргумент и не возвращает результат.
     * <hr>
     * <b>English</b>: JDBC callback that accepts an argument and returns no result.
     */
    @FunctionalInterface
    interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }

    /**
     * <b>Русский</b>: JDBC callback без аргументов и результата.
     * <hr>
     * <b>English</b>: JDBC callback without arguments and result.
     */
    @FunctionalInterface
    interface SqlRunnable {
        void run() throws SQLException;
    }

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

    private <T> T doInTx(@Nullable TxIsolation isolationLevel, SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException {
        return this.withContext(new DoInTxCallback<>(isolationLevel, callback));
    }

    class DoInTxCallback<T> implements SqlFunction<ConnectionContext, T> {
        private final TxIsolation isolationLevel;
        private final SqlFunction<ConnectionContext, T> callback;

        public DoInTxCallback(@Nullable TxIsolation isolationLevel, SqlFunction<ConnectionContext, T> callback) {
            this.isolationLevel = isolationLevel;
            this.callback = callback;
        }

        @Override
        public T apply(ConnectionContext ctx) throws SQLException {
            Connection connection = ctx.connection();
            if (!connection.getAutoCommit()) {
                try {
                    return callback.apply(ctx);
                } catch (SQLException e) {
                    throw e;
                } catch (Exception e) {
                    if (e instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(e);
                }
            }

            int previousIsolationLevel = 0;
            boolean isolationLevelChanged = false;
            T result;
            try {
                if (isolationLevel != null) {
                    previousIsolationLevel = connection.getTransactionIsolation();
                    connection.setTransactionIsolation(isolationLevel.value());
                    isolationLevelChanged = true;
                }
                connection.setAutoCommit(false);
                result = callback.apply(ctx);
                connection.commit();
                connection.setAutoCommit(true);
            } catch (Exception e) {
                var rollbackActions = ctx.takePostRollbackActions();
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                    if (rollbackActions instanceof List<PostRollbackAction> list) {
                        for (int i = 0; i < list.size(); i++) {
                            try {
                                list.get(i).run(connection, e);
                            } catch (Exception ex) {
                                e.addSuppressed(ex);
                            }
                        }
                    } else {
                        for (PostRollbackAction action : rollbackActions) {
                            try {
                                action.run(connection, e);
                            } catch (Exception ex) {
                                e.addSuppressed(ex);
                            }
                        }
                    }
                } catch (Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
                if (e instanceof SQLException se) {
                    throw se;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(e);
            } finally {
                if (isolationLevelChanged) {
                    connection.setTransactionIsolation(previousIsolationLevel);
                }
            }
            Exception actionError = null;
            var commitActions = ctx.takePostCommitActions();
            if (commitActions instanceof List<PostCommitAction> list) {
                for (int i = 0; i < list.size(); i++) {
                    try {
                        list.get(i).run(connection);
                    } catch (SQLException | RuntimeException e) {
                        if (actionError == null) {
                            actionError = e;
                        } else {
                            actionError.addSuppressed(e);
                        }
                    }
                }
            } else {
                for (PostCommitAction action : commitActions) {
                    try {
                        action.run(connection);
                    } catch (SQLException | RuntimeException e) {
                        if (actionError == null) {
                            actionError = e;
                        } else {
                            actionError.addSuppressed(e);
                        }
                    }
                }
            }
            if (actionError instanceof SQLException e) {
                throw e;
            }
            if (actionError instanceof RuntimeException e) {
                throw e;
            }
            return result;
        }
    }

    class WithConnectionCallback<T> implements SqlFunction<ConnectionContext, T> {
        private final SqlFunction<Connection, T> callback;

        public WithConnectionCallback(SqlFunction<Connection, T> callback) {
            this.callback = callback;
        }

        @Override
        public T apply(ConnectionContext context) throws SQLException {
            return callback.apply(context.connection());
        }
    }

    class QueryContextCall<T> implements Supplier<T> {
        private final JdbcExecutor executor;
        private final QueryContext queryContext;
        private final SqlFunction<PreparedStatement, T> callback;
        private final Observation observation;

        public QueryContextCall(JdbcExecutor executor, QueryContext queryContext, SqlFunction<PreparedStatement, T> callback, Observation observation) {
            this.executor = executor;
            this.queryContext = queryContext;
            this.callback = callback;
            this.observation = observation;
        }

        @Override
        public T get() {
            try {
                return executor.withConnection(new QueryContextConnectionCallback<>(queryContext, callback, observation));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    class QueryContextConnectionCallback<T> implements SqlFunction<Connection, T> {
        private final QueryContext queryContext;
        private final SqlFunction<PreparedStatement, T> callback;
        private final Observation observation;

        public QueryContextConnectionCallback(QueryContext queryContext, SqlFunction<PreparedStatement, T> callback, Observation observation) {
            this.queryContext = queryContext;
            this.callback = callback;
            this.observation = observation;
        }

        @Override
        public T apply(Connection connection) throws SQLException {
            try (PreparedStatement ps = connection.prepareStatement(queryContext.sql())) {
                return callback.apply(ps);
            } catch (Exception e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
        }
    }

    class JdbcQueryCall<T> implements Supplier<T> {
        private final JdbcExecutor executor;
        private final JdbcQuery query;
        private final SqlFunction<PreparedStatement, T> callback;
        private final Observation observation;

        public JdbcQueryCall(JdbcExecutor executor, JdbcQuery query, SqlFunction<PreparedStatement, T> callback, Observation observation) {
            this.executor = executor;
            this.query = query;
            this.callback = callback;
            this.observation = observation;
        }

        @Override
        public T get() throws RuntimeException {
            try {
                return executor.withConnection(new JdbcQueryConnectionCallback<>(query, callback, observation));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    class JdbcQueryConnectionCallback<T> implements SqlFunction<Connection, T> {
        private final JdbcQuery query;
        private final SqlFunction<PreparedStatement, T> callback;
        private final Observation observation;

        public JdbcQueryConnectionCallback(JdbcQuery query, SqlFunction<PreparedStatement, T> callback, Observation observation) {
            this.query = query;
            this.callback = callback;
            this.observation = observation;
        }

        @Override
        public T apply(Connection connection) throws SQLException {
            try (PreparedStatement ps = query.prepare(connection)) {
                return callback.apply(ps);
            } catch (Exception e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
        }
    }

    class QueryMapperCallback<T> implements SqlFunction<PreparedStatement, T> {
        private final JdbcResultSetMapper<T> mapper;

        public QueryMapperCallback(JdbcResultSetMapper<T> mapper) {
            this.mapper = mapper;
        }

        @Override
        public T apply(PreparedStatement statement) throws SQLException {
            try (var resultSet = statement.executeQuery()) {
                return mapper.apply(resultSet);
            }
        }
    }

    class ExecuteUpdateCallback implements SqlFunction<PreparedStatement, UpdateCount> {
        static final ExecuteUpdateCallback INSTANCE = new ExecuteUpdateCallback();

        @Override
        public UpdateCount apply(PreparedStatement statement) throws SQLException {
            return new UpdateCount(statement.executeUpdate());
        }
    }

    class ExecuteUpdateMapperCallback<T> implements SqlFunction<PreparedStatement, T> {
        private final JdbcResultSetMapper<T> generatedKeysMapper;

        public ExecuteUpdateMapperCallback(JdbcResultSetMapper<T> generatedKeysMapper) {
            this.generatedKeysMapper = generatedKeysMapper;
        }

        @Override
        public T apply(PreparedStatement statement) throws SQLException {
            statement.executeUpdate();
            try (var resultSet = statement.getGeneratedKeys()) {
                return generatedKeysMapper.apply(resultSet);
            }
        }
    }

    class ExecuteUpdateBatchCall implements Supplier<UpdateCount> {
        private final JdbcExecutor executor;
        private final JdbcQuery.JdbcQueryBatch batch;
        private final Observation observation;

        public ExecuteUpdateBatchCall(JdbcExecutor executor, JdbcQuery.JdbcQueryBatch batch, Observation observation) {
            this.executor = executor;
            this.batch = batch;
            this.observation = observation;
        }

        @Override
        public UpdateCount get() throws RuntimeException {
            try {
                return executor.withConnection(new ExecuteUpdateBatchConnectionCallback(batch, observation));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    class ExecuteUpdateBatchConnectionCallback implements SqlFunction<Connection, UpdateCount> {
        private final JdbcQuery.JdbcQueryBatch batch;
        private final Observation observation;

        public ExecuteUpdateBatchConnectionCallback(JdbcQuery.JdbcQueryBatch batch, Observation observation) {
            this.batch = batch;
            this.observation = observation;
        }

        @Override
        public UpdateCount apply(Connection connection) throws SQLException {
            try (PreparedStatement ps = batch.prepare(connection)) {
                int[] batchResult = ps.executeBatch();
                long total = 0;
                for (int i = 0; i < batchResult.length; i++) {
                    int count = batchResult[i];
                    if (count == Statement.SUCCESS_NO_INFO) {
                        return new UpdateCount(-1);
                    }
                    if (count == Statement.EXECUTE_FAILED) {
                        throw new SQLException("Batch execution failed");
                    }
                    total += count;
                }
                return new UpdateCount(total);
            } catch (Exception e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
        }
    }

    class ExecuteUpdateBatchMapperCall<T> implements Supplier<T> {
        private final JdbcExecutor executor;
        private final JdbcQuery.JdbcQueryBatch batch;
        private final JdbcResultSetMapper<T> generatedKeysMapper;
        private final Observation observation;

        public ExecuteUpdateBatchMapperCall(JdbcExecutor executor, JdbcQuery.JdbcQueryBatch batch, JdbcResultSetMapper<T> generatedKeysMapper, Observation observation) {
            this.executor = executor;
            this.batch = batch;
            this.generatedKeysMapper = generatedKeysMapper;
            this.observation = observation;
        }

        @Override
        public T get() throws RuntimeException {
            try {
                return executor.withConnection(new ExecuteUpdateBatchMapperConnectionCallback<>(batch, generatedKeysMapper, observation));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    class ExecuteUpdateBatchMapperConnectionCallback<T> implements SqlFunction<Connection, T> {
        private final JdbcQuery.JdbcQueryBatch batch;
        private final JdbcResultSetMapper<T> generatedKeysMapper;
        private final Observation observation;

        public ExecuteUpdateBatchMapperConnectionCallback(JdbcQuery.JdbcQueryBatch batch, JdbcResultSetMapper<T> generatedKeysMapper, Observation observation) {
            this.batch = batch;
            this.generatedKeysMapper = generatedKeysMapper;
            this.observation = observation;
        }

        @Override
        public T apply(Connection connection) throws SQLException {
            try (PreparedStatement ps = batch.prepare(connection)) {
                ps.executeBatch();
                try (var resultSet = ps.getGeneratedKeys()) {
                    return generatedKeysMapper.apply(resultSet);
                }
            } catch (Exception e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
        }
    }

    class WithConnectionSupplierCallback<T> implements SqlFunction<Connection, T> {
        private final SqlSupplier<T> callback;

        public WithConnectionSupplierCallback(SqlSupplier<T> callback) {
            this.callback = callback;
        }

        @Override
        public T apply(Connection connection) throws SQLException {
            return callback.apply();
        }
    }

    class WithConnectionConsumerCallback implements SqlFunction<Connection, Object> {
        private final SqlConsumer<Connection> callback;

        public WithConnectionConsumerCallback(SqlConsumer<Connection> callback) {
            this.callback = callback;
        }

        @Override
        public Object apply(Connection connection) throws SQLException {
            callback.accept(connection);
            return null;
        }
    }

    class WithConnectionRunnableCallback implements SqlFunction<Connection, Object> {
        private final SqlRunnable callback;

        public WithConnectionRunnableCallback(SqlRunnable callback) {
            this.callback = callback;
        }

        @Override
        public Object apply(Connection connection) throws SQLException {
            callback.run();
            return null;
        }
    }

    class InTxSupplierCallback<T> implements SqlFunction<ConnectionContext, T> {
        private final SqlSupplier<T> callback;

        public InTxSupplierCallback(SqlSupplier<T> callback) {
            this.callback = callback;
        }

        @Override
        public T apply(ConnectionContext context) throws SQLException {
            return callback.apply();
        }
    }

    class InTxConsumerCallback implements SqlFunction<ConnectionContext, Object> {
        private final SqlConsumer<ConnectionContext> callback;

        public InTxConsumerCallback(SqlConsumer<ConnectionContext> callback) {
            this.callback = callback;
        }

        @Override
        public Object apply(ConnectionContext context) throws SQLException {
            callback.accept(context);
            return null;
        }
    }

    class InTxRunnableCallback implements SqlFunction<ConnectionContext, Object> {
        private final SqlRunnable callback;

        public InTxRunnableCallback(SqlRunnable callback) {
            this.callback = callback;
        }

        @Override
        public Object apply(ConnectionContext context) throws SQLException {
            callback.run();
            return null;
        }
    }
}
