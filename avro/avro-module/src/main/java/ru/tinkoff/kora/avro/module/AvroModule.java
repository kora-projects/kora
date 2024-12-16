package ru.tinkoff.kora.avro.module;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import ru.tinkoff.kora.avro.common.AvroReader;
import ru.tinkoff.kora.avro.common.AvroWriter;
import ru.tinkoff.kora.avro.common.annotation.AvroBinary;
import ru.tinkoff.kora.avro.common.annotation.AvroJson;
import ru.tinkoff.kora.avro.module.http.client.*;
import ru.tinkoff.kora.avro.module.http.server.AvroAsyncHttpServerRequestMapper;
import ru.tinkoff.kora.avro.module.http.server.AvroHttpServerRequestMapper;
import ru.tinkoff.kora.avro.module.kafka.KafkaAvroTypedDeserializer;
import ru.tinkoff.kora.avro.module.kafka.KafkaAvroTypedSerializer;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;

public interface AvroModule {

    // Kafka
    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> Serializer<T> avroBinaryKafkaSpecificSerializer(@AvroBinary AvroWriter<T> writer) {
        return new KafkaAvroTypedSerializer<>(writer);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> Serializer<T> avroAvroKafkapecificSerializer(@AvroJson AvroWriter<T> writer) {
        return new KafkaAvroTypedSerializer<>(writer);
    }

    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> Deserializer<T> avroBinaryKafkaSpecificDeserializer(@AvroBinary AvroReader<T> reader) {
        return new KafkaAvroTypedDeserializer<>(reader);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> Deserializer<T> avroAvroKafkaSpecificDeserializer(@AvroJson AvroReader<T> reader) {
        return new KafkaAvroTypedDeserializer<>(reader);
    }

    // HTTP Server
    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> HttpServerResponseMapper<T> avroBinaryHttpServerResponseMapper(@AvroBinary AvroWriter<T> writer) {
        return (ctx, request, result) -> HttpServerResponse.of(200, HttpBody.of("application/avro", writer.writeBytes(result)));
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> HttpServerResponseMapper<T> avroAvroHttpServerResponseMapper(@AvroJson AvroWriter<T> writer) {
        return (ctx, request, result) -> HttpServerResponse.of(200, HttpBody.json(writer.writeBytes(result)));
    }

    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> HttpServerResponseMapper<HttpResponseEntity<T>> avroBinaryHttpServerResponseEntityMapper(@AvroBinary AvroWriter<T> writer) {
        return (ctx, request, result) -> HttpServerResponse.of(result.code(), result.headers(), HttpBody.of("application/avro", writer.writeBytes(result.body())));
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> HttpServerResponseMapper<HttpResponseEntity<T>> avroAvroHttpServerResponseEntityMapper(@AvroJson AvroWriter<T> writer) {
        return (ctx, request, result) -> HttpServerResponse.of(result.code(), result.headers(), HttpBody.json(writer.writeBytes(result.body())));
    }

    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> AvroHttpServerRequestMapper<T> avroBinaryRequestMapper(@AvroBinary AvroReader<T> reader) {
        return new AvroHttpServerRequestMapper<>(reader);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> AvroHttpServerRequestMapper<T> avroJsonRequestMapper(@AvroJson AvroReader<T> reader) {
        return new AvroHttpServerRequestMapper<>(reader);
    }

    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> AvroAsyncHttpServerRequestMapper<T> avroBinaryAsyncHttpServerRequestMapper(@AvroBinary AvroReader<T> reader) {
        return new AvroAsyncHttpServerRequestMapper<>(reader);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> AvroAsyncHttpServerRequestMapper<T> avroJsonAsyncHttpServerRequestMapper(@AvroJson AvroReader<T> reader) {
        return new AvroAsyncHttpServerRequestMapper<>(reader);
    }

    // HTTP Client
    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> AvroHttpClientRequestMapper<T> avroBinaryHttpClientRequestMapper(@AvroBinary AvroWriter<T> avroWriter) {
        return new AvroHttpClientRequestMapper<>(avroWriter);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> AvroHttpClientRequestMapper<T> avroJsonHttpClientRequestMapper(@AvroJson AvroWriter<T> avroWriter) {
        return new AvroHttpClientRequestMapper<>(avroWriter);
    }

    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> AvroHttpClientResponseMapper<T> avroBinaryHttpClientResponseMapper(@AvroBinary AvroReader<T> reader) {
        return new AvroHttpClientResponseMapper<>(reader);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> AvroHttpClientResponseMapper<T> avroJsonHttpClientResponseMapper(@AvroJson AvroReader<T> reader) {
        return new AvroHttpClientResponseMapper<>(reader);
    }

    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> AvroAsyncHttpClientResponseMapper<T> avroBinaryAsyncHttpClientResponseMapper(@AvroBinary AvroReader<T> reader) {
        return new AvroAsyncHttpClientResponseMapper<>(reader);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> AvroAsyncHttpClientResponseMapper<T> avroJsonAsyncHttpClientResponseMapper(@AvroJson AvroReader<T> reader) {
        return new AvroAsyncHttpClientResponseMapper<>(reader);
    }

    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> AvroHttpClientResponseEntityMapper<T> avroBinaryHttpClientResponseEntityMapper(@AvroBinary AvroReader<T> reader) {
        return new AvroHttpClientResponseEntityMapper<>(reader);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> AvroHttpClientResponseEntityMapper<T> avroJsonHttpClientResponseEntityMapper(@AvroJson AvroReader<T> reader) {
        return new AvroHttpClientResponseEntityMapper<>(reader);
    }

    @AvroBinary
    @DefaultComponent
    default <T extends SpecificRecord> AvroAsyncHttpClientResponseEntityMapper<T> avroBinaryAsyncHttpClientResponseEntityMapper(@AvroBinary AvroReader<T> reader) {
        return new AvroAsyncHttpClientResponseEntityMapper<>(reader);
    }

    @AvroJson
    @DefaultComponent
    default <T extends SpecificRecord> AvroAsyncHttpClientResponseEntityMapper<T> avroJsonAsyncHttpClientResponseEntityMapper(@AvroJson AvroReader<T> reader) {
        return new AvroAsyncHttpClientResponseEntityMapper<>(reader);
    }
}
