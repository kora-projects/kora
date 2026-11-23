package io.koraframework.nats.common.producer;

import io.koraframework.application.graph.Lifecycle;
import io.nats.client.api.PublishAck;
import org.jspecify.annotations.Nullable;

/**
 * Atomically publishes related messages into one JetStream stream using a generated typed publisher.
 */
public interface AtomicBatchPublisher<P> extends Lifecycle {
    interface Batch<P> extends AutoCloseable {
        P publisher();

        String id();

        int size();

        /**
         * Sends buffered messages and returns the acknowledgement of the entire batch. Never retries a commit.
         */
        PublishAck commit();

        /**
         * Discards an uncommitted batch. Cannot undo a committed or indeterminate commit.
         */
        void abort(@Nullable Throwable cause);

        default void abort() {
            abort(null);
        }

        /**
         * Aborts if the batch has not been committed. Closing never commits implicitly.
         */
        @Override
        void close();
    }

    Batch<? extends P> begin();

    /**
     * Runs typed publish calls and commits on success, discarding the batch if the callback fails.
     */
    default <E extends Throwable> PublishAck inBatch(BatchConsumer<P, E> callback) throws E {
        try (var batch = begin()) {
            try {
                callback.accept(batch.publisher());
            } catch (Throwable e) {
                try {
                    batch.abort(e);
                } catch (Throwable cleanup) {
                    if (cleanup != e) {
                        e.addSuppressed(cleanup);
                    }
                }
                throw e;
            }
            return batch.commit();
        }
    }

    @FunctionalInterface
    interface BatchConsumer<P, E extends Throwable> {
        void accept(P publisher) throws E;
    }
}
