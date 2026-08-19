package io.koraframework.kafka.avro.telemetry;

/**
 * <b>Русский</b>: Фабрика телеметрии Avro (де)сериализации.
 * <hr>
 * <b>English</b>: Factory for the Avro (de)serialization telemetry.
 */
public interface AvroRegistryTelemetryFactory {

    AvroRegistryTelemetry get();
}
