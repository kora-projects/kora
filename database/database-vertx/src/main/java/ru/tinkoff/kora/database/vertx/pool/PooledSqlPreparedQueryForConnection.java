package ru.tinkoff.kora.database.vertx.pool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.*;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collector;

public class PooledSqlPreparedQueryForConnection implements PreparedQuery<RowSet<Row>> {
    private final SqlConnection connection;
    private final String sql;
    private final PooledSqlConnection pooled;
    private final PrepareOptions options;

    public PooledSqlPreparedQueryForConnection(PooledSqlConnection connection, String sql, PrepareOptions options) {
        this.pooled = connection;
        this.connection = connection.unwrap();
        this.sql = sql;
        this.options = options;
    }

    @Override
    public void execute(Tuple tuple, Handler<AsyncResult<RowSet<Row>>> handler) {
        this.connection.preparedQuery(this.sql, this.options).execute(tuple, event -> {
            if (event.succeeded()) {
                this.pooled.updateLastSuccessfulOperation();
            }
            handler.handle(event);
        });
    }

    @Override
    public Future<RowSet<Row>> execute(Tuple tuple) {
        return this.connection.preparedQuery(this.sql, this.options).execute(tuple)
            .onSuccess(v -> this.pooled.updateLastSuccessfulOperation());
    }

    @Override
    public void executeBatch(List<Tuple> batch, Handler<AsyncResult<RowSet<Row>>> handler) {
        this.connection.preparedQuery(this.sql, this.options).executeBatch(batch, event -> {
            if (event.succeeded()) {
                this.pooled.updateLastSuccessfulOperation();
            }
            handler.handle(event);
        });
    }

    @Override
    public Future<RowSet<Row>> executeBatch(List<Tuple> batch) {
        return this.connection.preparedQuery(this.sql, this.options).executeBatch(batch)
            .onSuccess(v -> this.pooled.updateLastSuccessfulOperation());
    }

    @Override
    public void execute(Handler<AsyncResult<RowSet<Row>>> handler) {
        this.connection.query(this.sql).execute(event -> {
            if (event.succeeded()) {
                this.pooled.updateLastSuccessfulOperation();
            }
            handler.handle(event);
        });
    }

    @Override
    public Future<RowSet<Row>> execute() {
        return this.connection.preparedQuery(this.sql, this.options).execute()
            .onSuccess(v -> this.pooled.updateLastSuccessfulOperation());
    }

    @Override
    public <R> PreparedQuery<SqlResult<R>> collecting(Collector<Row, ?, R> collector) {
        return this.connection.preparedQuery(this.sql, this.options).collecting(collector);
    }

    @Override
    public <U> PreparedQuery<RowSet<U>> mapping(Function<Row, U> mapper) {
        return this.connection.preparedQuery(this.sql, this.options).mapping(mapper);
    }
}
