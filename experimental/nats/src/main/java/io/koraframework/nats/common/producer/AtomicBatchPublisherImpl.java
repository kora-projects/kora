package io.koraframework.nats.common.producer;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.nats.common.NatsClient;
import io.koraframework.nats.common.exceptions.NatsAtomicBatchException;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherOperationObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Buffered ADR-50 atomic batch transport. Each batch uses its own id and one configured stream.
 */
public class AtomicBatchPublisherImpl<P> implements AtomicBatchPublisher<P> {
    private static final String BATCH_ID = "Nats-Batch-Id";
    private static final String BATCH_SEQUENCE = "Nats-Batch-Sequence";
    private static final String BATCH_COMMIT = "Nats-Batch-Commit";
    private static final String EXPECTED_STREAM = "Nats-Expected-Stream";
    private final NatsClient client;
    private final NatsAtomicBatchConfig config;
    private final NatsPublisherTelemetry telemetry;
    private final Function<NatsAtomicBatchSink, P> factory;
    private final Set<BatchImpl> batches = ConcurrentHashMap.newKeySet();
    private boolean released;

    public AtomicBatchPublisherImpl(NatsClient client, NatsPublisherConfig publisherConfig, NatsAtomicBatchConfig config,
                                    NatsPublisherTelemetry telemetry, Function<NatsAtomicBatchSink, P> factory) {
        this.client = Objects.requireNonNull(client);
        this.config = Objects.requireNonNull(config);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.factory = Objects.requireNonNull(factory);
        if (publisherConfig.mode() != NatsPublisherConfig.Mode.JETSTREAM) {
            throw new IllegalArgumentException("NATS atomic batch requires JETSTREAM publisher mode");
        }
        if (config.stream() == null || config.stream().isBlank() || config.commitTimeout().isZero()
            || config.commitTimeout().isNegative() || config.maxMessages() <= 0 || config.maxMessages() > 1000 || config.maxBytes() <= 0) {
            throw new IllegalArgumentException("NATS atomic batch requires a stream, positive commitTimeout/maxBytes and maxMessages in [1, 1000]");
        }
    }

    @Override
    public synchronized void init() throws Exception {
        client.init();
        released = false;
    }

    @Override
    public synchronized void release() throws Exception {
        released = true;
        Throwable failure = null;
        for (var batch : batches) {
            try {
                batch.abort(new CancellationException("NATS atomic publisher released"));
            } catch (RuntimeException | Error e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        try {
            client.release();
        } catch (Exception | Error e) {
            if (failure == null) {
                failure = e;
            } else if (failure != e) {
                failure.addSuppressed(e);
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception error) {
            throw error;
        }
    }

    @Override
    public synchronized Batch<P> begin() {
        if (released) {
            throw new IllegalStateException("NATS atomic publisher is released");
        }
        var batch = new BatchImpl(client.connection());
        batches.add(batch);
        try {
            Observation.scoped(batch.observation()).call(() -> {
                if (!client.jetStreamManagement().getStreamInfo(config.stream()).getConfiguration().getAllowAtomicPublish()) {
                    throw new IllegalArgumentException("NATS atomic batch stream must enable allow_atomic: " + config.stream());
                }
                return null;
            });
            batch.publisher = factory.apply(batch);
            return batch;
        } catch (Exception | Error e) {
            try {
                batch.abort(e);
            } catch (Throwable cleanup) {
                if (cleanup != e) {
                    e.addSuppressed(cleanup);
                }
            }
            if (e instanceof Error error) {
                throw error;
            }
            if (e instanceof RuntimeException failure) {
                throw failure;
            }
            throw new io.koraframework.nats.common.exceptions.NatsPublishException("Failed to begin NATS atomic batch", e);
        }
    }

    private record Pending(Message message, NatsPublisherRecordObservation observation) {
    }

    private enum State {
        OPEN, COMMITTING, COMMITTED, ABORTED, UNKNOWN
    }

    private final class BatchImpl implements Batch<P>, NatsAtomicBatchSink {
        private final String id = UUID.randomUUID().toString();
        private final Connection connection;
        private final NatsPublisherOperationObservation observation;
        private final ArrayList<Pending> pending = new ArrayList<>();
        private @Nullable P publisher;
        private State state = State.OPEN;
        private @Nullable PublishAck acknowledgement;
        private int size;
        private long bytes;

        private BatchImpl(Connection connection) {
            this.connection = connection;
            this.observation = telemetry.observeOperation("batch");
            observation.span().setAttribute("messaging.nats.stream", config.stream());
            observation.span().setAttribute("messaging.nats.batch.id", id);
        }

        @Override
        public P publisher() {
            return Objects.requireNonNull(publisher);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public synchronized int size() {
            return size;
        }

        @Override
        public NatsPublisherOperationObservation observation() {
            return observation;
        }

        @Override
        public synchronized void append(Message original, NatsPublisherRecordObservation recordObservation) {
            requireOpen();
            try {
                var headers = original.hasHeaders() ? new Headers(original.getHeaders()) : new Headers();
                if (headers.containsKeyIgnoreCase(BATCH_ID) || headers.containsKeyIgnoreCase(BATCH_SEQUENCE)
                    || headers.containsKeyIgnoreCase(BATCH_COMMIT)) {
                    throw new IllegalArgumentException("Atomic batch headers are owned by AtomicBatchPublisher");
                }
                if (original.getReplyTo() != null) {
                    throw new IllegalArgumentException("Atomic batch messages cannot carry replyTo");
                }
                if (headers.containsKeyIgnoreCase(EXPECTED_STREAM) && !config.stream().equals(headers.getFirst(EXPECTED_STREAM))) {
                    throw new IllegalArgumentException("Atomic batch message targets a different stream");
                }
                headers.put(EXPECTED_STREAM, config.stream());
                var requiredApi = headers.getFirst("Nats-Required-Api-Level");
                if (requiredApi == null || Integer.parseInt(requiredApi) < 2) {
                    headers.put("Nats-Required-Api-Level", "2");
                }
                var data = original.getData();
                long messageBytes = data.length + (long) headers.serializedLength();
                if (size >= config.maxMessages() || messageBytes > config.maxBytes() - bytes) {
                    throw new IllegalArgumentException("NATS atomic batch exceeds maxMessages or maxBytes");
                }
                var copy = NatsMessage.builder().subject(original.getSubject()).headers(headers)
                    .data(Arrays.copyOf(data, data.length)).utf8mode(original.isUtf8mode()).build();
                pending.add(new Pending(copy, recordObservation));
                size++;
                bytes += messageBytes;
            } catch (RuntimeException | Error e) {
                abort(e);
                throw e;
            }
        }

        private void requireOpen() {
            if (state != State.OPEN) {
                throw new IllegalStateException("NATS atomic batch is " + state);
            }
        }

        @Override
        public synchronized PublishAck commit() {
            if (state == State.COMMITTED) {
                return Objects.requireNonNull(acknowledgement);
            }
            requireOpen();
            if (pending.isEmpty()) {
                throw new IllegalStateException("NATS atomic batch must contain at least one message");
            }
            var commitObservation = Observation.scoped(observation).call(() -> telemetry.observeOperation("batch.commit"));
            state = State.COMMITTING;
            long deadline = System.nanoTime() + config.commitTimeout().toNanos();
            boolean commitAttempted = false;
            boolean messageAttempted = false;
            try {
                for (int index = 0; index < pending.size(); index++) {
                    var original = pending.get(index).message();
                    var headers = new Headers(original.getHeaders()).put(BATCH_ID, id).put(BATCH_SEQUENCE, Integer.toString(index + 1));
                    boolean last = index == pending.size() - 1;
                    if (last) {
                        headers.put(BATCH_COMMIT, "1");
                    }
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        throw new TimeoutException("NATS atomic batch commitTimeout expired");
                    }
                    var message = NatsMessage.builder().subject(original.getSubject()).headers(headers).data(original.getData())
                        .utf8mode(original.isUtf8mode()).build();
                    messageAttempted = true;
                    commitAttempted = last;
                    var reply = Observation.scoped(commitObservation).call(() -> connection.request(message, Duration.ofNanos(remaining)));
                    if (reply == null) {
                        throw new TimeoutException("NATS atomic batch acknowledgement timed out");
                    }
                    if (reply.isStatusMessage()) {
                        throw new JetStreamApiException(io.nats.client.api.Error.convert(reply.getStatus()));
                    }
                    if (last) {
                        var ack = new PublishAck(reply);
                        if (!config.stream().equals(ack.getStream()) || !id.equals(ack.getBatchId()) || size != ack.getBatchSize()) {
                            throw new IOException("NATS atomic batch acknowledgement does not match stream, batch id or size");
                        }
                        acknowledgement = ack;
                    } else if (reply.getData().length != 0) {
                        new PublishAck(reply); // Throws the server's error, preserving JetStreamApiException.
                        throw new IOException("Unexpected acknowledgement before atomic batch commit");
                    }
                }
                state = State.COMMITTED;
            } catch (Throwable e) {
                var outcome = commitAttempted && !(e instanceof JetStreamApiException)
                    ? NatsAtomicBatchException.Outcome.UNKNOWN : NatsAtomicBatchException.Outcome.ABORTED;
                state = outcome == NatsAtomicBatchException.Outcome.UNKNOWN ? State.UNKNOWN : State.ABORTED;
                if (messageAttempted && outcome == NatsAtomicBatchException.Outcome.ABORTED) {
                    abandon(e);
                }
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                var failure = new NatsAtomicBatchException(id, outcome, e);
                try {
                    commitObservation.end(failure);
                } catch (RuntimeException | Error cleanup) {
                    failure.addSuppressed(cleanup);
                } finally {
                    complete(failure);
                }
                if (e instanceof Error error) {
                    throw error;
                }
                throw failure;
            }
            var result = Objects.requireNonNull(acknowledgement);
            try {
                commitObservation.observeResult(result);
                observation.observeResult(result);
            } finally {
                try {
                    commitObservation.end();
                } finally {
                    complete(null);
                }
            }
            return result;
        }

        private void abandon(Throwable failure) {
            try {
                var original = pending.getFirst().message();
                var headers = new Headers().put(EXPECTED_STREAM, config.stream()).put(BATCH_ID, id).put(BATCH_SEQUENCE, "0");
                connection.publish(NatsMessage.builder().subject(original.getSubject()).headers(headers).data(new byte[0])
                    .utf8mode(original.isUtf8mode()).build());
            } catch (RuntimeException e) {
                failure.addSuppressed(e);
            }
        }

        @Override
        public synchronized void abort(@Nullable Throwable cause) {
            if (state != State.OPEN) {
                return;
            }
            state = State.ABORTED;
            NatsPublisherOperationObservation abortObservation = null;
            try {
                abortObservation = Observation.scoped(observation).call(() -> telemetry.observeOperation("batch.abort"));
            } finally {
                try {
                    complete(cause == null ? new CancellationException("NATS atomic batch aborted") : cause);
                } finally {
                    if (abortObservation != null) {
                        abortObservation.end(cause);
                    }
                }
            }
        }

        @Override
        public void fail(Throwable cause) {
            abort(cause);
        }

        @Override
        public void close() {
            abort();
        }

        private void complete(@Nullable Throwable failure) {
            Throwable cleanupFailure = null;
            try {
                for (var item : pending) {
                    try {
                        item.observation().onCompletion(null, failure);
                    } catch (RuntimeException | Error e) {
                        if (cleanupFailure == null) {
                            cleanupFailure = e;
                        } else if (cleanupFailure != e) {
                            cleanupFailure.addSuppressed(e);
                        }
                    }
                }
            } finally {
                pending.clear();
                bytes = 0;
                batches.remove(this);
                try {
                    observation.end(failure);
                } catch (RuntimeException | Error e) {
                    if (cleanupFailure == null) {
                        cleanupFailure = e;
                    } else if (cleanupFailure != e) {
                        cleanupFailure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                if (cleanupFailure != null && cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            } else if (cleanupFailure instanceof Error e) {
                throw e;
            } else if (cleanupFailure instanceof RuntimeException e) {
                throw e;
            }
        }
    }
}
