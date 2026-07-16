package io.koraframework.kafka.common.consumer.deserializer;

import io.koraframework.avro.common.AvroReader;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class KafkaAvroTypedDeserializer<T extends SpecificRecord> implements Deserializer<T> {

    protected static final byte MAGIC_BYTE = 0x0;
    protected static final int ID_SIZE = 4;

    private final AvroReader<T> avroReader;

    public KafkaAvroTypedDeserializer(AvroReader<T> avroReader) {
        this.avroReader = avroReader;
    }

    @Override
    public T deserialize(String topic, byte[] bytes) {
        return deserialize(bytes);
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] bytes) {
        return deserialize(bytes);
    }

    protected T deserialize(byte[] payload) throws SerializationException {
        if (payload == null || payload.length == 0) {
            return null;
        }

        return read(payload);
    }

    private T read(byte[] payload) {
        if (payload.length <= 1 + ID_SIZE) {
            throw new SerializationException("Invalid Avro payload: too short");
        }
        if (payload[0] != MAGIC_BYTE) {
            throw new SerializationException("Invalid Avro payload: unknown magic byte " + payload[0]);
        }

        int schemaId = ByteBuffer.wrap(payload, 1, ID_SIZE).getInt();
        try {
            int offset = 1 + ID_SIZE;
            ByteBuffer buffer = ByteBuffer.wrap(payload, offset, payload.length - offset);
            return avroReader.read(buffer);
        } catch (IOException ex) {
            throw new SerializationException("Error deserializing Avro message for id " + schemaId, ex);
        }
    }
}
