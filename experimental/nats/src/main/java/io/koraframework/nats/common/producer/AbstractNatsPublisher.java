package io.koraframework.nats.common.producer;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.logging.common.MDC;
import io.koraframework.nats.common.NatsClient;
import io.koraframework.nats.common.consumer.deserializer.NatsDeserializer;
import io.koraframework.nats.common.exceptions.NatsPublishException;
import io.koraframework.nats.common.producer.serializer.NatsSerializer;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public abstract class AbstractNatsPublisher implements GeneratedNatsPublisher {
    private final NatsClient client;
    private final NatsPublisherConfig config;
    private final NatsPublisherTelemetry telemetry;
    private final @Nullable NatsAtomicBatchSink batch;

    protected AbstractNatsPublisher(String name, NatsClient client, NatsPublisherConfig config, NatsPublisherTelemetry telemetry) {
        this(name, client, config, telemetry, null);
    }

    protected AbstractNatsPublisher(String name, NatsClient client, NatsPublisherConfig config, NatsPublisherTelemetry telemetry,
                                    @Nullable NatsAtomicBatchSink batch) {
        this.client = client;
        this.config = config;
        this.telemetry = telemetry;
        this.batch = batch;
        if (config.requestTimeout().isNegative() || config.requestTimeout().isZero()) {
            throw new IllegalArgumentException("NATS publisher requestTimeout must be positive: " + name);
        }
    }

    @Override
    public NatsClient client() {
        return client;
    }

    @Override
    public NatsPublisherTelemetry telemetry() {
        return telemetry;
    }

    @Override
    public void init() throws Exception {
        client.init();
    }

    @Override
    public void release() throws Exception {
        client.release();
    }

    protected NatsPublisherRecordObservation observeSend(String subject) {
        return batch == null ? telemetry.observeSend(subject)
            : Observation.scoped(batch.observation()).call(() -> telemetry.observeSend(subject));
    }

    private void requireStandalonePublish(NatsPublisherRecordObservation observation) {
        if (batch == null) {
            return;
        }
        var error = new IllegalStateException("Atomic batch supports void publish methods; acknowledgement is returned by commit()");
        batch.fail(error);
        observation.end(error);
        throw error;
    }

    protected <T> Message serializeMessage(String subject, @Nullable String replyTo, Headers headers, T value,
                                           NatsSerializer<T> serializer, NatsPublisherRecordObservation observation) {
        try {
            return Observation.scoped(observation).call(() ->
                NatsMessage.builder().subject(subject).replyTo(replyTo).headers(headers).data(serializer.serialize(subject, headers, value)).build());
        } catch (RuntimeException | Error e) {
            if (batch != null) {
                batch.fail(e);
            }
            throw e;
        }
    }

    @SuppressWarnings("deprecation")
    private Message observedMessage(Message original, NatsPublisherRecordObservation observation) {
        var headers = original.hasHeaders() ? new Headers(original.getHeaders()) : new Headers();
        var message = NatsMessage.builder().subject(original.getSubject()).replyTo(original.getReplyTo())
            .headers(headers).data(original.getData()).utf8mode(original.isUtf8mode()).build();
        observation.observeRecord(message);
        return message;
    }

    protected @Nullable PublishAck publish(Message original, @Nullable PublishOptions options) {
        return publish(original, options, observeSend(original.getSubject()));
    }

    protected @Nullable PublishAck publish(Message original, @Nullable PublishOptions options, NatsPublisherRecordObservation observation) {
        if (batch != null) {
            try {
                if (options != null) {
                    throw new IllegalArgumentException("Atomic batch uses message headers instead of PublishOptions");
                }
                batch.append(Observation.scoped(observation).call(() -> observedMessage(original, observation)), observation);
                return null;
            } catch (RuntimeException | Error e) {
                batch.fail(e);
                observation.end(e);
                throw e;
            }
        }
        try {
            var ack = Observation.scoped(observation).call(() -> {
                var message = observedMessage(original, observation);
                if (config.mode() == NatsPublisherConfig.Mode.JETSTREAM) {
                    return options == null ? client.jetStream().publish(message) : client.jetStream().publish(message, options);
                }
                if (options != null) {
                    throw new IllegalArgumentException("PublishOptions require JETSTREAM publisher mode");
                }
                client.connection().publish(message);
                return (PublishAck) null;
            });
            observation.onCompletion(ack, null);
            return ack;
        } catch (Throwable e) {
            observation.end(e);
            if (e instanceof Error error) {
                throw error;
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new NatsPublishException("Failed to publish NATS message on " + original.getSubject(), e);
        }
    }

    protected CompletableFuture<PublishAck> publishAsync(Message original, @Nullable PublishOptions options) {
        return publishAsync(original, options, observeSend(original.getSubject()));
    }

    protected CompletableFuture<PublishAck> publishAsync(Message original, @Nullable PublishOptions options, NatsPublisherRecordObservation observation) {
        requireStandalonePublish(observation);
        var mdc = MDC.VALUE.isBound() ? MDC.get().fork() : new MDC();
        try {
            if (config.mode() != NatsPublisherConfig.Mode.JETSTREAM) {
                throw new IllegalStateException("Async PublishAck requires JETSTREAM publisher mode");
            }
            var result = Observation.scoped(observation).call(() -> {
                var message = observedMessage(original, observation);
                return options == null ? client.jetStream().publishAsync(message) : client.jetStream().publishAsync(message, options);
            });
            return observeCompletion(result, observation, mdc);
        } catch (Throwable e) {
            observation.end(e);
            if (e instanceof Error error) {
                throw error;
            }
            return CompletableFuture.failedFuture(e);
        }
    }

    protected CompletableFuture<Void> publishCompletionAsync(Message original, @Nullable PublishOptions options) {
        return publishCompletionAsync(original, options, observeSend(original.getSubject()));
    }

    protected CompletableFuture<Void> publishCompletionAsync(Message original, @Nullable PublishOptions options, NatsPublisherRecordObservation observation) {
        if (config.mode() == NatsPublisherConfig.Mode.JETSTREAM) {
            return publishAsync(original, options, observation).thenApply(ack -> null);
        }
        try {
            publish(original, options, observation);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    protected PublishAck publishAcknowledged(Message original, @Nullable PublishOptions options) {
        return publishAcknowledged(original, options, observeSend(original.getSubject()));
    }

    protected PublishAck publishAcknowledged(Message original, @Nullable PublishOptions options, NatsPublisherRecordObservation observation) {
        requireStandalonePublish(observation);
        if (config.mode() != NatsPublisherConfig.Mode.JETSTREAM) {
            var error = new IllegalStateException("PublishAck requires JETSTREAM publisher mode");
            observation.end(error);
            throw error;
        }
        return publish(original, options, observation);
    }

    protected void publishCallback(Message message, @Nullable PublishOptions options, NatsPublishCallback callback) {
        publishCallback(message, options, callback, observeSend(message.getSubject()));
    }

    protected void publishCallback(Message message, @Nullable PublishOptions options, NatsPublishCallback callback, NatsPublisherRecordObservation observation) {
        requireStandalonePublish(observation);
        if (config.mode() == NatsPublisherConfig.Mode.JETSTREAM) {
            publishAsync(message, options, observation).whenComplete((ack, error) -> callback.onCompletion(ack, unwrap(error)));
        } else {
            PublishAck ack;
            try {
                ack = publish(message, options, observation);
            } catch (RuntimeException e) {
                callback.onCompletion(null, e);
                return;
            }
            callback.onCompletion(ack, null);
        }
    }

    protected <T> CompletableFuture<T> requestAsync(Message original, NatsDeserializer<T> deserializer, @Nullable Duration timeout) {
        return requestAsync(original, deserializer, timeout, telemetry.observeRequest(original.getSubject()));
    }

    protected <T> CompletableFuture<T> requestAsync(Message original, NatsDeserializer<T> deserializer, @Nullable Duration timeout, NatsPublisherRecordObservation observation) {
        requireStandalonePublish(observation);
        var mdc = MDC.VALUE.isBound() ? MDC.get().fork() : new MDC();
        try {
            var result = Observation.scoped(observation).call(() -> client.connection().requestWithTimeout(
                observedMessage(original, observation), timeout == null ? config.requestTimeout() : timeout));
            var decoded = result.thenApply(message -> Observation.scoped(observation).where(MDC.VALUE, mdc).call(() -> {
                if (message == null) {
                    throw new NatsPublishException("NATS request timed out on " + original.getSubject(), null);
                }
                observation.observeResponse(message);
                return deserializer.deserialize(message);
            }));
            var observed = observeCompletion(decoded, observation, mdc);
            observed.whenComplete((value, error) -> {
                if (observed.isCancelled()) {
                    result.cancel(true);
                }
            });
            return observed;
        } catch (Throwable e) {
            observation.end(e);
            if (e instanceof Error error) {
                throw error;
            }
            return CompletableFuture.failedFuture(e);
        }
    }

    private static <T> CompletableFuture<T> observeCompletion(CompletableFuture<T> source, NatsPublisherRecordObservation observation, MDC mdc) {
        var result = new CompletableFuture<T>();
        source.whenComplete((value, error) -> {
            var failure = unwrap(error);
            try {
                Observation.scoped(observation).where(MDC.VALUE, mdc).run(() -> {
                    try {
                        observation.observeResult(value);
                        observation.end(failure);
                    } finally {
                        if (failure == null) {
                            result.complete(value);
                        } else {
                            result.completeExceptionally(failure);
                        }
                    }
                });
            } catch (Throwable telemetryError) {
                result.completeExceptionally(telemetryError);
            }
        });
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                source.cancel(true);
            }
        });
        return result;
    }

    protected <T> T request(Message message, NatsDeserializer<T> deserializer, @Nullable Duration timeout) {
        return request(message, deserializer, timeout, telemetry.observeRequest(message.getSubject()));
    }

    protected <T> T request(Message message, NatsDeserializer<T> deserializer, @Nullable Duration timeout, NatsPublisherRecordObservation observation) {
        try {
            return requestAsync(message, deserializer, timeout, observation).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NatsPublishException("NATS request interrupted on " + message.getSubject(), e);
        } catch (Exception e) {
            throw new NatsPublishException("NATS request failed on " + message.getSubject(), unwrap(e.getCause()));
        }
    }

    private static @Nullable Throwable unwrap(@Nullable Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
