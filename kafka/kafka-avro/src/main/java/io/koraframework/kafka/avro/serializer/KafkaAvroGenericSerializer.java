package io.koraframework.kafka.avro.serializer;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;
import io.koraframework.avro.common.AvroGenericData;
import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetry;
import io.koraframework.kafka.avro.telemetry.impl.NoopAvroRegistryTelemetry;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <b>Русский</b>: Сериализатор {@link GenericRecord} в формате Confluent Schema Registry.
 * Схема берётся из самой записи ({@link GenericRecord#getSchema()}).
 * <hr>
 * <b>English</b>: {@link GenericRecord} serializer in the Confluent Schema Registry wire format.
 * The schema is taken from the record itself ({@link GenericRecord#getSchema()}).
 */
public class KafkaAvroGenericSerializer implements Serializer<GenericRecord> {

    protected static final byte MAGIC_BYTE = 0x0;
    private static final String AUTO_REGISTER_SCHEMAS_CONFIG = "auto.register.schemas";

    private final SchemaRegistryClient schemaRegistry;
    private final SubjectNameStrategy subjectNameStrategy;
    private final AvroRegistryTelemetry telemetry;
    private final GenericData genericData = AvroGenericData.withStandardConversions();
    private final ConcurrentMap<String, Integer> subjectToId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Schema, GenericDatumWriter<GenericRecord>> writers = new ConcurrentHashMap<>();

    private boolean autoRegisterSchemas;
    private boolean isKey;

    public KafkaAvroGenericSerializer(SchemaRegistryClient schemaRegistry) {
        this(schemaRegistry, new TopicNameStrategy(), true, NoopAvroRegistryTelemetry.INSTANCE);
    }

    public KafkaAvroGenericSerializer(SchemaRegistryClient schemaRegistry,
                                      SubjectNameStrategy subjectNameStrategy,
                                      boolean autoRegisterSchemas,
                                      AvroRegistryTelemetry telemetry) {
        this.schemaRegistry = Objects.requireNonNull(schemaRegistry, "schemaRegistry");
        this.subjectNameStrategy = Objects.requireNonNull(subjectNameStrategy, "subjectNameStrategy");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.autoRegisterSchemas = autoRegisterSchemas;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.isKey = isKey;
        var autoRegister = configs.get(AUTO_REGISTER_SCHEMAS_CONFIG);
        if (autoRegister != null) {
            this.autoRegisterSchemas = Boolean.parseBoolean(String.valueOf(autoRegister));
        }
    }

    @Override
    public byte[] serialize(String topic, GenericRecord data) {
        return serialize(topic, null, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, GenericRecord record) {
        if (record == null) {
            return null;
        }

        var recordSchema = record.getSchema();
        var schema = new AvroSchema(recordSchema);
        try {
            int id = schemaId(topic, schema);
            var writer = this.writers.computeIfAbsent(recordSchema, s -> new GenericDatumWriter<>(s, this.genericData));
            try (var out = new ByteArrayOutputStream()) {
                out.write(MAGIC_BYTE);
                out.write((id >>> 24) & 0xFF);
                out.write((id >>> 16) & 0xFF);
                out.write((id >>> 8) & 0xFF);
                out.write(id & 0xFF);
                var encoder = EncoderFactory.get().directBinaryEncoder(out, null);
                writer.write(record, encoder);
                encoder.flush();
                return out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            this.telemetry.observeError(AvroRegistryTelemetry.OP_SERIALIZE, topic);
            throw new SerializationException("Error serializing Avro GenericRecord for topic " + topic, e);
        } catch (RestClientException e) {
            this.telemetry.observeError(AvroRegistryTelemetry.OP_SERIALIZE, topic);
            throw new SerializationException("Error registering Avro schema for topic " + topic + " in Schema Registry", e);
        }
    }

    private int schemaId(String topic, AvroSchema schema) throws IOException, RestClientException {
        var subject = this.subjectNameStrategy.subjectName(topic, this.isKey, schema);
        var cached = this.subjectToId.get(subject);
        if (cached != null) {
            this.telemetry.observeCache(AvroRegistryTelemetry.OP_SERIALIZE, true);
            return cached;
        }
        this.telemetry.observeCache(AvroRegistryTelemetry.OP_SERIALIZE, false);

        var operation = this.autoRegisterSchemas ? AvroRegistryTelemetry.OP_REGISTER : AvroRegistryTelemetry.OP_GET_ID;
        var start = System.nanoTime();
        boolean success = false;
        try {
            int id = this.autoRegisterSchemas
                ? this.schemaRegistry.register(subject, schema)
                : this.schemaRegistry.getId(subject, schema);
            success = true;
            this.subjectToId.putIfAbsent(subject, id);
            return id;
        } finally {
            this.telemetry.observeRegistryLookup(operation, success, System.nanoTime() - start);
        }
    }
}
