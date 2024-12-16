package ru.tinkoff.kora.avro.module.kafka;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import ru.tinkoff.kora.avro.common.AvroReader;

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
        try {
            int offset = 1 + ID_SIZE;
            ByteBuffer buffer = ByteBuffer.wrap(payload, offset, payload.length - offset);
            return avroReader.read(buffer);
        } catch (IOException ex) {
            String schemaId = new String(payload, 0, 1 + ID_SIZE);
            throw new SerializationException("Error deserializing Avro message for id " + schemaId, ex.getCause());
        }
    }
}
