package ru.tinkoff.kora.avro.module.kafka;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;
import ru.tinkoff.kora.avro.common.AvroWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;

public class KafkaAvroTypedSerializer<T extends SpecificRecord> implements Serializer<T> {

    protected static final byte MAGIC_BYTE = 0x0;
    protected static final int ID_SIZE = 4;

    private final AvroWriter<T> avroWriter;

    public KafkaAvroTypedSerializer(AvroWriter<T> avroWriter) {
        this.avroWriter = avroWriter;
    }

    @Override
    public byte[] serialize(String topic, T data) {
        return this.serialize(topic, null, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T record) {
        if (record == null) {
            return null;
        }

        AvroSchema schema = new AvroSchema(record.getSchema());
        return serializeImpl(record, schema);
    }

    protected byte[] serializeImpl(T object, AvroSchema schema) throws SerializationException, InvalidConfigurationException {
        if (object == null) {
            return null;
        }

        try {
            int id = schema.version();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                out.write(MAGIC_BYTE);
                out.write(ByteBuffer.allocate(ID_SIZE).putInt(id).array());
                out.write(avroWriter.writeBytesUnchecked(object));
                return out.toByteArray();
            }
        } catch (InterruptedIOException e) {
            throw new TimeoutException("Error serializing Avro message", e);
        } catch (IOException | RuntimeException e) {
            // avro serialization can throw AvroRuntimeException, NullPointerException, ClassCastException, etc
            throw new SerializationException("Error serializing Avro message", e);
        }
    }
}
