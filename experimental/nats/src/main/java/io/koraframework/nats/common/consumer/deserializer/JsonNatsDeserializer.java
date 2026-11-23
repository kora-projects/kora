package io.koraframework.nats.common.consumer.deserializer;

import io.koraframework.json.common.JsonReader;
import io.koraframework.nats.common.exceptions.NatsSerializationException;
import io.nats.client.Message;

/**
 * JSON codec whose format is also available to consumer body logging.
 */
public final class JsonNatsDeserializer<T> implements NatsDeserializer<T> {
    private final JsonReader<T> reader;

    public JsonNatsDeserializer(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T deserialize(Message message) {
        if (message.getData() == null) {
            return null;
        }
        try {
            return reader.read(message.getData());
        } catch (RuntimeException e) {
            throw new NatsSerializationException("Failed to deserialize NATS JSON on " + message.getSubject(), e);
        }
    }
}
