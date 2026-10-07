package io.koraframework.kafka.common.producer;

import io.koraframework.common.telemetry.Observation;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;
import io.koraframework.kafka.common.annotation.KafkaPublisher;

import java.util.Map;

/**
 * @param <P> publisher type that must be annotated with {@link KafkaPublisher}
 */
@SuppressWarnings("overloads")
public interface TransactionalPublisher<P> {

    interface Transaction<P> extends AutoCloseable {

        P publisher();

        Producer<byte[], byte[]> producer();

        /**
         * See {@link KafkaProducer#sendOffsetsToTransaction(Map, ConsumerGroupMetadata)}
         */
        void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata);

        /**
         * See {@link KafkaProducer#abortTransaction()}
         */
        void abort(@Nullable Throwable t);

        /**
         * See {@link KafkaProducer#abortTransaction()}
         */
        default void abort() {
            this.abort(null);
        }

        /**
         * See {@link KafkaProducer#flush()} ()}
         */
        void flush();

        @Override
        void close();
    }

    /**
     * Initialize Publisher in transaction mode {@link Producer#initTransactions()} and then begins transaction {@link Producer#beginTransaction()} and returns publisher in such state
     *
     * @return Publisher as {@link P}
     * <p>
     * It is expected that you will manually call {@link Producer#commitTransaction()} or {@link Producer#abortTransaction()} and then {@link Producer#close()}
     * <p>
     * Records sent through a transaction obtained here are not traced as children of the transaction span,
     * use {@link #inTx} or {@link #withTx} for that.
     */
    Transaction<? extends P> begin();

    default <E extends Throwable> void inTx(TransactionalConsumer<P, E> callback) throws E {
        try (var p = begin()) {
            try {
                inScope(p, () -> {
                    callback.accept(p.publisher());
                    return null;
                });
            } catch (Throwable e) {
                p.abort();
                throw e;
            }
        }
    }

    default <E extends Throwable, R> R inTx(TransactionalFunction<P, E, R> callback) throws E {
        try (var p = begin()) {
            try {
                return inScope(p, () -> callback.accept(p.publisher()));
            } catch (Throwable e) {
                p.abort();
                throw e;
            }
        }
    }

    default <E extends Throwable> void withTx(TransactionConsumer<P, E> callback) throws E {
        try (var p = begin()) {
            try {
                inScope(p, () -> {
                    callback.accept(p);
                    return null;
                });
            } catch (Throwable e) {
                p.abort();
                throw e;
            }
        }
    }

    default <E extends Throwable, R> R withTx(TransactionFunction<P, E, R> callback) throws E {
        try (var p = begin()) {
            try {
                return inScope(p, () -> callback.accept(p));
            } catch (Throwable e) {
                p.abort();
                throw e;
            }
        }
    }

    /**
     * Runs the callback with the transaction observation bound as the current {@link Observation} and its span as the
     * current span, so records sent inside {@link #inTx} / {@link #withTx} are traced as children of the transaction span.
     * Commit and abort stay outside of this scope. A bare {@link #begin()} cannot bind a scope, so sends made through it are not nested.
     */
    private static <R, E extends Throwable> R inScope(Transaction<?> tx, ScopedValue.CallableOp<R, E> callback) throws E {
        if (tx instanceof TransactionImpl<?> impl) {
            return Observation.scoped(impl.observation()).call(callback);
        }
        return callback.call();
    }

    @FunctionalInterface
    interface TransactionalConsumer<P, E extends Throwable> {

        void accept(P publisher) throws E;
    }

    @FunctionalInterface
    interface TransactionalFunction<P, E extends Throwable, R> {

        R accept(P publisher) throws E;
    }

    @FunctionalInterface
    interface TransactionConsumer<P, E extends Throwable> {

        void accept(Transaction<? extends P> tx) throws E;
    }

    @FunctionalInterface
    interface TransactionFunction<P, E extends Throwable, R> {

        R accept(Transaction<? extends P> tx) throws E;
    }
}
