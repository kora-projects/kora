package io.koraframework.kafka.avro;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.koraframework.avro.common.annotation.Avro;
import io.koraframework.avro.common.AvroReader;
import io.koraframework.avro.common.AvroWriter;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.kafka.avro.deserializer.KafkaAvroGenericDeserializer;
import io.koraframework.kafka.avro.deserializer.KafkaAvroTypedDeserializer;
import io.koraframework.kafka.avro.serializer.KafkaAvroGenericSerializer;
import io.koraframework.kafka.avro.serializer.KafkaAvroSerializerConfig;
import io.koraframework.kafka.avro.serializer.KafkaAvroTypedSerializer;
import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetry;
import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetryFactory;
import io.koraframework.kafka.avro.telemetry.impl.DefaultAvroRegistryTelemetryFactory;
import io.koraframework.kafka.schemaregistry.KafkaSchemaRegistryModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.jspecify.annotations.Nullable;

/**
 * <b>Русский</b>: Модуль Kafka + Avro: сериализаторы/десериализаторы для {@link SpecificRecord} и
 * {@link GenericRecord} в формате Confluent Schema Registry, конфигурация и телеметрия.
 * Включает {@link KafkaSchemaRegistryModule}, предоставляющий клиент реестра схем.
 * <hr>
 * <b>English</b>: Kafka + Avro module: serializers/deserializers for {@link SpecificRecord} and
 * {@link GenericRecord} in the Confluent Schema Registry wire format, plus config and telemetry.
 * Extends {@link KafkaSchemaRegistryModule}, which provides the Schema Registry client.
 */
public interface KafkaAvroModule extends KafkaSchemaRegistryModule {

    @DefaultComponent
    default KafkaAvroSerializerConfig kafkaAvroSerializerConfig(Config config, ConfigValueMapper<KafkaAvroSerializerConfig> mapper) {
        return mapper.mapOrThrow(config.get("kafka.avro.serializer"));
    }

    @DefaultComponent
    default AvroRegistryTelemetryFactory avroRegistryTelemetryFactory(@Nullable MeterRegistry meterRegistry) {
        return new DefaultAvroRegistryTelemetryFactory(meterRegistry);
    }

    @DefaultComponent
    default AvroRegistryTelemetry avroRegistryTelemetry(AvroRegistryTelemetryFactory factory) {
        return factory.get();
    }

    @Avro
    @DefaultComponent
    default <T extends SpecificRecord> Serializer<T> avroKafkaSpecificSerializer(AvroWriter<T> writer, SchemaRegistryClient schemaRegistry, KafkaAvroSerializerConfig config, AvroRegistryTelemetry telemetry) {
        return new KafkaAvroTypedSerializer<>(writer, schemaRegistry, config.subjectNameStrategy().create(), config.autoRegisterSchemas(), telemetry);
    }

    @Avro
    @DefaultComponent
    default Serializer<GenericRecord> avroKafkaGenericSerializer(SchemaRegistryClient schemaRegistry, KafkaAvroSerializerConfig config, AvroRegistryTelemetry telemetry) {
        return new KafkaAvroGenericSerializer(schemaRegistry, config.subjectNameStrategy().create(), config.autoRegisterSchemas(), telemetry);
    }

    @Avro
    @DefaultComponent
    default <T extends SpecificRecord> Deserializer<T> avroKafkaSpecificDeserializer(AvroReader<T> reader, SchemaRegistryClient schemaRegistry, AvroRegistryTelemetry telemetry) {
        return new KafkaAvroTypedDeserializer<>(reader, schemaRegistry, telemetry);
    }

    @Avro
    @DefaultComponent
    default Deserializer<GenericRecord> avroKafkaGenericDeserializer(SchemaRegistryClient schemaRegistry, AvroRegistryTelemetry telemetry) {
        return new KafkaAvroGenericDeserializer(schemaRegistry, telemetry);
    }
}
