package io.koraframework.kafka.avro.deserializer;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.koraframework.avro.common.AvroGenericData;
import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetry;
import io.koraframework.kafka.avro.telemetry.impl.NoopAvroRegistryTelemetry;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <b>Русский</b>: Десериализатор {@link GenericRecord} в формате Confluent Schema Registry.
 * Схема писателя загружается из реестра по идентификатору и используется как схема чтения.
 * <hr>
 * <b>English</b>: {@link GenericRecord} deserializer in the Confluent Schema Registry wire format.
 * The writer schema is loaded from the registry by id and used as the reader schema (read as written).
 */
public class KafkaAvroGenericDeserializer implements Deserializer<GenericRecord> {

    protected static final byte MAGIC_BYTE = 0x0;
    protected static final int ID_SIZE = 4;
    protected static final int HEADER_SIZE = 1 + ID_SIZE;

    private final SchemaRegistryClient schemaRegistry;
    private final AvroRegistryTelemetry telemetry;
    private final GenericData genericData = AvroGenericData.withStandardConversions();
    private final ConcurrentMap<Integer, GenericDatumReader<GenericRecord>> readersById = new ConcurrentHashMap<>();

    public KafkaAvroGenericDeserializer(SchemaRegistryClient schemaRegistry) {
        this(schemaRegistry, NoopAvroRegistryTelemetry.INSTANCE);
    }

    public KafkaAvroGenericDeserializer(SchemaRegistryClient schemaRegistry, AvroRegistryTelemetry telemetry) {
        this.schemaRegistry = Objects.requireNonNull(schemaRegistry, "schemaRegistry");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    @Override
    public GenericRecord deserialize(String topic, byte[] bytes) {
        return deserialize(bytes);
    }

    @Override
    public GenericRecord deserialize(String topic, Headers headers, byte[] bytes) {
        return deserialize(bytes);
    }

    protected GenericRecord deserialize(byte[] payload) throws SerializationException {
        if (payload == null || payload.length == 0) {
            return null;
        }

        if (payload.length < HEADER_SIZE) {
            throw new SerializationException("Invalid Avro payload: too short, expected at least " + HEADER_SIZE + " bytes but got " + payload.length);
        }

        var buffer = ByteBuffer.wrap(payload);
        byte magic = buffer.get();
        if (magic != MAGIC_BYTE) {
            throw new SerializationException("Invalid Avro payload: unknown magic byte " + magic);
        }

        int schemaId = buffer.getInt();
        var reader = reader(schemaId);
        try {
            var decoder = DecoderFactory.get().binaryDecoder(
                buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), null);
            return reader.read(null, decoder);
        } catch (SerializationException ex) {
            throw ex;
        } catch (RuntimeException | IOException ex) {
            this.telemetry.observeError(AvroRegistryTelemetry.OP_DESERIALIZE, null);
            throw new SerializationException("Error deserializing Avro GenericRecord for id " + schemaId, ex);
        }
    }

    private GenericDatumReader<GenericRecord> reader(int schemaId) {
        var cached = this.readersById.get(schemaId);
        if (cached != null) {
            this.telemetry.observeCache(AvroRegistryTelemetry.OP_DESERIALIZE, true);
            return cached;
        }
        this.telemetry.observeCache(AvroRegistryTelemetry.OP_DESERIALIZE, false);
        var writerSchema = fetchWriterSchema(schemaId);
        var reader = new GenericDatumReader<GenericRecord>(writerSchema, writerSchema, this.genericData);
        var existing = this.readersById.putIfAbsent(schemaId, reader);
        return existing != null ? existing : reader;
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
