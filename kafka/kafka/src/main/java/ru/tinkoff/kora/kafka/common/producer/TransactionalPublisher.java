package ru.tinkoff.kora.kafka.common.producer;

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher;

import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * @param <P> publisher type that must be annotated with {@link KafkaPublisher}
 */
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
     */
    Transaction<? extends P> begin();

    default <E extends Throwable> void inTx(TransactionalConsumer<P, E> callback) throws E {
        try (var p = begin()) {
            try {
                callback.accept(p.publisher());
            } catch (Throwable e) {
                p.abort();
                throw e;
            }
        }
    }

    default <E extends Throwable, R> R inTx(TransactionalFunction<P, E, R> callback) throws E {
        try (var p = begin()) {
            try {
                return callback.accept(p.publisher());
            } catch (Throwable e) {
                p.abort();
                throw e;
            }
        }
    }

    default <E extends Throwable> void withTx(TransactionConsumer<P, E> callback) throws E {
        try (var p = begin()) {
            try {
                callback.accept(p);
            } catch (Throwable e) {
                p.abort();
                throw e;
            }
        }
    }

    default <E extends Throwable, R> R withTx(TransactionFunction<P, E, R> callback) throws E {
        try (var p = begin()) {
            try {
                return callback.accept(p);
            } catch (Throwable e) {
                p.abort();
                throw e;
            }
        }
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
