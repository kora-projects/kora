package io.koraframework.nats.common.consumer;

import io.koraframework.nats.common.consumer.deserializer.NatsDeserializer;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerRecordObservation;
import io.koraframework.nats.common.consumer.telemetry.impl.NoopNatsConsumerRecordObservation;
import io.koraframework.nats.common.exceptions.NatsSerializationException;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Lazy deserialization keeps raw message available when payload cannot be decoded.
 */
public final class NatsMessage<T> {
    private final Message message;
    private final NatsDeserializer<T> deserializer;
    private final NatsConsumerRecordObservation observation;
    private boolean decoded;
    private @Nullable T value;
    private @Nullable NatsSerializationException error;

    public NatsMessage(Message message, NatsDeserializer<T> deserializer) {
        this(message, deserializer, NoopNatsConsumerRecordObservation.INSTANCE);
    }

    public NatsMessage(Message message, NatsDeserializer<T> deserializer, NatsConsumerRecordObservation observation) {
        this.message = message;
        this.deserializer = deserializer;
        this.observation = observation;
    }

    public NatsConsumerRecordObservation observation() {
        return observation;
    }

    public Message message() {
        return message;
    }

    public String subject() {
        return message.getSubject();
    }

    public Headers headers() {
        return message.hasHeaders() ? message.getHeaders() : new Headers();
    }

    public @Nullable String replyTo() {
        return message.getReplyTo();
    }

    public synchronized T value() {
        if (!decoded) {
            decoded = true;
            try {
                value = deserializer.deserialize(message);
                observation.observeData(value);
            } catch (RuntimeException e) {
                error = new NatsSerializationException("Failed to deserialize NATS message on " + subject(), e);
                observation.observeError(error);
            }
        }
        if (error != null) {
            throw error;
        }
        return value;
    }

    private void acknowledge(String operation, Runnable action) {
        var ack = observation.observeAck(operation);
        try {
            action.run();
        } catch (RuntimeException | Error e) {
            ack.observeError(e);
            observation.observeError(e);
            throw e;
        } finally {
            ack.end();
        }
    }

    public void ack() {
        acknowledge("ack", message::ack);
    }

    public void ackSync(Duration timeout) throws Exception {
        var ack = observation.observeAck("ackSync");
        try {
            message.ackSync(timeout);
        } catch (Exception | Error e) {
            ack.observeError(e);
            observation.observeError(e);
            throw e;
        } finally {
            ack.end();
        }
    }

    public void nak() {
        acknowledge("nak", message::nak);
    }

    public void nakWithDelay(Duration delay) {
        acknowledge("nak", () -> message.nakWithDelay(delay));
    }

    public void term() {
        acknowledge("term", message::term);
    }

    public void inProgress() {
        acknowledge("inProgress", message::inProgress);
    }
}
