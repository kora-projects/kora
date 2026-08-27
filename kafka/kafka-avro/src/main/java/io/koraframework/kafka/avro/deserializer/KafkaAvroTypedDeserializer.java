package io.koraframework.kafka.avro.deserializer;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.koraframework.avro.common.AvroReader;
import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetry;
import io.koraframework.kafka.avro.telemetry.impl.NoopAvroRegistryTelemetry;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <b>Русский</b>: Десериализатор Avro в формате Confluent Schema Registry (magic byte + идентификатор схемы).
 * По идентификатору схемы из сообщения загружается схема писателя, что позволяет корректно выполнять
 * разрешение схем (schema resolution) при эволюции схемы.
 * <hr>
 * <b>English</b>: Avro deserializer using the Confluent Schema Registry wire format (magic byte + schema id).
 * The writer schema is resolved from the registry by the id embedded in the payload, so Avro schema
 * resolution is performed correctly when the producer's schema has evolved away from the consumer's.
 */
public class KafkaAvroTypedDeserializer<T extends SpecificRecord> implements Deserializer<T> {

    protected static final byte MAGIC_BYTE = 0x0;
    protected static final int ID_SIZE = 4;
    protected static final int HEADER_SIZE = 1 + ID_SIZE;

    private final AvroReader<T> avroReader;
    private final SchemaRegistryClient schemaRegistry;
    private final AvroRegistryTelemetry telemetry;
    private final ConcurrentMap<Integer, Schema> writerSchemaCache = new ConcurrentHashMap<>();

    public KafkaAvroTypedDeserializer(AvroReader<T> avroReader, SchemaRegistryClient schemaRegistry) {
        this(avroReader, schemaRegistry, NoopAvroRegistryTelemetry.INSTANCE);
    }

    public KafkaAvroTypedDeserializer(AvroReader<T> avroReader, SchemaRegistryClient schemaRegistry, AvroRegistryTelemetry telemetry) {
        this.avroReader = Objects.requireNonNull(avroReader, "avroReader");
        this.schemaRegistry = Objects.requireNonNull(schemaRegistry, "schemaRegistry");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
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
        if (payload.length < HEADER_SIZE) {
            throw new SerializationException("Invalid Avro payload: too short, expected at least " + HEADER_SIZE + " bytes but got " + payload.length);
        }

        var buffer = ByteBuffer.wrap(payload);
        byte magic = buffer.get();
        if (magic != MAGIC_BYTE) {
            throw new SerializationException("Invalid Avro payload: unknown magic byte " + magic);
        }

        int schemaId = buffer.getInt();
        Schema writerSchema = writerSchema(schemaId);
        try {
            // buffer position is now past the 5-byte header; only the Avro body remains
            return this.avroReader.read(writerSchema, buffer);
        } catch (SerializationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            this.telemetry.observeError(AvroRegistryTelemetry.OP_DESERIALIZE, null);
            throw new SerializationException("Error deserializing Avro message for id " + schemaId, ex);
        }
    }

    private Schema writerSchema(int schemaId) {
        var cached = this.writerSchemaCache.get(schemaId);
        if (cached != null) {
            this.telemetry.observeCache(AvroRegistryTelemetry.OP_DESERIALIZE, true);
            return cached;
        }
        this.telemetry.observeCache(AvroRegistryTelemetry.OP_DESERIALIZE, false);
        var writerSchema = fetchWriterSchema(schemaId);
        this.writerSchemaCache.putIfAbsent(schemaId, writerSchema);
        return writerSchema;
    }

    private Schema fetchWriterSchema(int schemaId) {
        var start = System.nanoTime();
        boolean success = false;
        try {
            ParsedSchema parsedSchema = this.schemaRegistry.getSchemaById(schemaId);
            if (parsedSchema instanceof AvroSchema avroSchema) {
                success = true;
                return avroSchema.rawSchema();
            }
            throw new SerializationException("Schema for id " + schemaId + " is not an Avro schema but "
                + (parsedSchema == null ? "null" : parsedSchema.schemaType()));
        } catch (IOException | RestClientException e) {
            throw new SerializationException("Error retrieving Avro writer schema for id " + schemaId + " from Schema Registry", e);
        } finally {
            this.telemetry.observeRegistryLookup(AvroRegistryTelemetry.OP_GET_BY_ID, success, System.nanoTime() - start);
        }
    }
}
