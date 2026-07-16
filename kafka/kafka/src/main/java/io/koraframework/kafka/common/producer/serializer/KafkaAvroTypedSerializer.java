package io.koraframework.kafka.common.producer.serializer;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.koraframework.avro.common.AvroWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

public class KafkaAvroTypedSerializer<T extends SpecificRecord> implements Serializer<T> {

    protected static final byte MAGIC_BYTE = 0x0;
    protected static final int ID_SIZE = 4;
    private static final String AUTO_REGISTER_SCHEMAS_CONFIG = "auto.register.schemas";

    private final AvroWriter<T> avroWriter;
    private final SchemaRegistryClient schemaRegistry;
    private boolean autoRegisterSchemas = true;
    private boolean isKey;

    public KafkaAvroTypedSerializer(AvroWriter<T> avroWriter, SchemaRegistryClient schemaRegistry) {
        this.avroWriter = Objects.requireNonNull(avroWriter);
        this.schemaRegistry = Objects.requireNonNull(schemaRegistry);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.isKey = isKey;
        var autoRegisterSchemas = configs.get(AUTO_REGISTER_SCHEMAS_CONFIG);
        this.autoRegisterSchemas = autoRegisterSchemas == null || Boolean.parseBoolean(String.valueOf(autoRegisterSchemas));
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
        return serializeImpl(topic, record, schema);
    }

    protected byte[] serializeImpl(String topic, T object, AvroSchema schema) throws SerializationException {
        if (object == null) {
            return null;
        }

        try {
            int id = schemaId(topic, schema);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                out.write(MAGIC_BYTE);
                out.write(ByteBuffer.allocate(ID_SIZE).putInt(id).array());
                out.write(avroWriter.writeBytesUnchecked(object));
                return out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            throw new SerializationException("Error serializing Avro message", e);
        } catch (RestClientException e) {
            throw new SerializationException("Error registering Avro schema", e);
        }
    }

    private int schemaId(String topic, AvroSchema schema) throws IOException, RestClientException {
        var subject = topic + (this.isKey ? "-key" : "-value");
        return this.autoRegisterSchemas
            ? this.schemaRegistry.register(subject, schema)
            : this.schemaRegistry.getId(subject, schema);
    }
}
