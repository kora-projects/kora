package ru.tinkoff.kora.database.vertx.pool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.*;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collector;

public class PooledPgPreparedQueryForPool implements PreparedQuery<RowSet<Row>> {
    private final VertxSqlPool pool;
    private final String sql;
    @Nullable
    private final PrepareOptions options;

    public PooledPgPreparedQueryForPool(VertxSqlPool vertxPgPool, String sql, @Nullable PrepareOptions options) {
        this.pool = vertxPgPool;
        this.sql = sql;
        this.options = options;
    }

    @Override
    public void execute(Tuple tuple, Handler<AsyncResult<RowSet<Row>>> handler) {
        this.pool.withPooledConnection((connection, h) -> {
            connection.preparedQuery(this.sql).execute(tuple, h);
        }, handler);
    }

    @Override
    public Future<RowSet<Row>> execute(Tuple tuple) {
        return Future.future(handler -> this.execute(tuple, handler));
    }

    @Override
    public void executeBatch(List<Tuple> batch, Handler<AsyncResult<RowSet<Row>>> handler) {
        this.pool.withPooledConnection((c, h) -> c.preparedQuery(this.sql, this.options).executeBatch(batch, h), handler);
    }

    @Override
    public Future<RowSet<Row>> executeBatch(List<Tuple> batch) {
        return Future.future(handler -> this.executeBatch(batch, handler));
    }

    @Override
    public void execute(Handler<AsyncResult<RowSet<Row>>> handler) {
        this.pool.withPooledConnection((c, h) -> c.preparedQuery(this.sql, this.options).execute(h), handler);
    }

    @Override
    public Future<RowSet<Row>> execute() {
        return Future.future(this::execute);
    }

    @Override
    public <R> PreparedQuery<SqlResult<R>> collecting(Collector<Row, ?, R> collector) {
        return this.pool.preparedQuery(this.sql, this.options).collecting(collector);
    }

    @Override
    public <U> PreparedQuery<RowSet<U>> mapping(Function<Row, U> mapper) {
        return this.pool.preparedQuery(this.sql, this.options).mapping(mapper);
    }
}
