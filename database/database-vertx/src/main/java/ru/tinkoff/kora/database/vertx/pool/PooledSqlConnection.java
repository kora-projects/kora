package ru.tinkoff.kora.database.vertx.pool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.*;
import io.vertx.sqlclient.spi.DatabaseMetadata;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.pool.PooledRef;

public class PooledSqlConnection implements SqlConnection {
    private final PooledRef<?> ref;
    private final SqlConnection connection;
    private final SqlConnectionHolder holder;
    private volatile Transaction tx = null;

    public PooledSqlConnection(PooledRef<SqlConnectionHolder> ref) {
        this.ref = ref;
        this.holder = ref.poolable();
        this.connection = this.holder.con;
        this.connection.closeHandler(v -> {
            this.ref.release().subscribe();
        });
    }

    public void updateLastSuccessfulOperation() {
        this.holder.updateLastSuccessfulOperation();
    }

    public SqlConnection unwrap() {
        return this.connection;
    }

    @Override
    public SqlConnection prepare(String sql, Handler<AsyncResult<PreparedStatement>> handler) {
        return this.connection.prepare(sql, event -> {
            if (event.succeeded()) {
                this.updateLastSuccessfulOperation();
            } else {
                this.ref.invalidate();
            }
            handler.handle(event);
        });
    }

    @Override
    public SqlConnection prepare(String sql, PrepareOptions options, Handler<AsyncResult<PreparedStatement>> handler) {
        return this.connection.prepare(sql, options, event -> {
            if (event.succeeded()) {
                this.updateLastSuccessfulOperation();
            } else {
                this.ref.invalidate();
            }
            handler.handle(event);
        });
    }

    @Override
    public Future<PreparedStatement> prepare(String sql, PrepareOptions options) {
        return this.connection.prepare(sql, options)
            .onComplete(result -> {
                if (result.succeeded()) {
                    this.updateLastSuccessfulOperation();
                } else {
                    this.ref.invalidate();
                }
            });
    }

    @Override
    public SqlConnection exceptionHandler(Handler<Throwable> handler) {
        return this.connection.exceptionHandler(handler);
    }

    @Override
    public SqlConnection closeHandler(Handler<Void> handler) {
        return this.connection.closeHandler(handler);
    }

    @Override
    public Future<PreparedStatement> prepare(String sql) {
        return this.connection.prepare(sql)
            .onComplete(result -> {
                if (result.succeeded()) {
                    this.updateLastSuccessfulOperation();
                } else {
                    this.ref.invalidate();
                }
            });
    }

    @Override
    public void begin(Handler<AsyncResult<Transaction>> handler) {
        this.connection.begin(event -> {
            if (event.succeeded()) {
                this.tx = event.result();
                this.tx.completion(e -> this.tx = null);
                this.updateLastSuccessfulOperation();
            } else {
                this.ref.invalidate();
            }
            handler.handle(event);
        });
    }

    @Override
    public Future<Transaction> begin() {
        return this.connection.begin()
            .onComplete(result -> {
                if (result.succeeded()) {
                    this.tx = result.result();
                    this.tx.completion(event -> this.tx = null);
                    this.updateLastSuccessfulOperation();
                } else {
                    this.ref.invalidate();
                }
            });
    }

    @Override
    public boolean isSSL() {
        return this.connection.isSSL();
    }

    @Override
    public DatabaseMetadata databaseMetadata() {
        return this.connection.databaseMetadata();
    }

    @Override
    public Query<RowSet<Row>> query(String sql) {
        return new PooledSqlPreparedQueryForConnection(this, sql, null);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String sql) {
        return new PooledSqlPreparedQueryForConnection(this, sql, null);
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String sql, PrepareOptions options) {
        return new PooledSqlPreparedQueryForConnection(this, sql, options);
    }

    @Override
    public Future<Void> close() {
        var promise = Promise.<Void>promise();
        if (this.tx != null) {
            this.tx.rollback().onComplete(result -> {
                this.tx = null;
                if (result.succeeded()) {
                    this.updateLastSuccessfulOperation();
                    this.release(promise);
                } else {
                    this.invalidate();
                }
            });
        } else {
            this.release(promise);
        }
        return promise.future();
    }

    private void release(Promise<Void> promise) {
        this.ref.release().subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(Void unused) {}

            @Override
            public void onError(Throwable t) {
                promise.fail(t);
            }

            @Override
            public void onComplete() {
                promise.complete();
            }
        });
    }


    @Override
    public void close(Handler<AsyncResult<Void>> handler) {
        this.close().onComplete(handler);
    }

    public void invalidate() {
        this.connection.close();
        this.ref.invalidate().subscribe();
    }
}
