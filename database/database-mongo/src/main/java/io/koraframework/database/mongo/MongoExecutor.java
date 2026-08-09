package io.koraframework.database.mongo;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <b>Русский</b>: Фабрика соединений MongoDB которая позволяет выполнять запросы в ручном режиме и в рамках транзакции.
 * <hr>
 * <b>English</b>: MongoDB's connection factory that allows you to fulfil requests in manual mode or transaction mode.
 *
 * @see MongoRepository
 */
public interface MongoExecutor {

    MongoClient client();

    /**
     * <b>Русский</b>: База данных по умолчанию из конфигурации.
     * <hr>
     * <b>English</b>: Default database from the configuration.
     */
    MongoDatabase database();

    MongoDatabase database(String name);

    DatabaseTelemetry telemetry();

    /**
     * <b>Русский</b>: Сессия текущей транзакции или {@code null}, если вызов происходит вне {@link #inTx(Supplier)}.
     * <hr>
     * <b>English</b>: Session of the current transaction, or {@code null} when called outside {@link #inTx(Supplier)}.
     */
    @Nullable
    ClientSession currentSession();

    /**
     * <b>Русский</b>: Выполняет callback в транзакции. Вложенный вызов присоединяется к уже открытой транзакции,
     * потому что MongoDB не поддерживает вложенные транзакции. Callback может быть выполнен повторно при временной ошибке транзакции,
     * поэтому он должен быть идемпотентен.
     * <hr>
     * <b>English</b>: Executes the callback in a transaction. A nested call joins the transaction that is already open,
     * because MongoDB has no nested transactions. The callback may be executed more than once when a transient transaction
     * error occurs, so it must be idempotent.
     *
     * @param options  опции транзакции / transaction options
     * @param callback callback транзакции / transaction callback
     * @return результат callback / callback result
     */
    <T> T inTx(TransactionOptions options, Supplier<T> callback);

    default <T> T inTx(Supplier<T> callback) {
        return this.inTx(TransactionOptions.builder().build(), callback);
    }

    /**
     * <b>Русский</b>: То же что и {@link #inTx(Supplier)}, но для callback без возвращаемого значения.
     * <hr>
     * <b>English</b>: Same as {@link #inTx(Supplier)} but for a callback without a return value.
     */
    default void inTxWithoutResult(Runnable callback) {
        this.inTxWithoutResult(TransactionOptions.builder().build(), callback);
    }

    default void inTxWithoutResult(TransactionOptions options, Runnable callback) {
        this.inTx(options, () -> {
            callback.run();
            return Boolean.TRUE;
        });
    }

    /**
     * <b>Русский</b>: Выполняет callback над базой данных по умолчанию и оборачивает выполнение телеметрией.
     * <hr>
     * <b>English</b>: Executes a callback against the default database and wraps execution with telemetry.
     *
     * @param queryContext контекст запроса / query context
     * @param callback     callback с базой данных / callback with database
     * @return результат callback / callback result
     */
    default <T> T query(QueryContext queryContext, Function<MongoDatabase, T> callback) {
        var observation = this.telemetry().observe(queryContext);
        return Observation.scoped(observation).call(() -> {
            observation.observeConnection();
            var database = this.database();
            observation.observeStatement();
            try {
                return callback.apply(database);
            } catch (Exception e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
        });
    }
}
