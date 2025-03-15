package ru.tinkoff.kora.json.module;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.module.http.client.*;
import ru.tinkoff.kora.json.module.http.server.*;
import ru.tinkoff.kora.json.module.kafka.JsonKafkaDeserializer;
import ru.tinkoff.kora.json.module.kafka.JsonKafkaSerializer;

public interface JsonModule extends JsonCommonModule {

    @Json
    @DefaultComponent
    default <T> JsonReaderHttpServerRequestMapper<T> jsonRequestMapper(JsonReader<T> reader) {
        return new JsonReaderHttpServerRequestMapper<>(reader);
    }

    @Json
    default <T> JsonReaderAsyncHttpServerRequestMapper<T> jsonReaderAsyncHttpServerRequestMapper(JsonReader<T> reader) {
        return new JsonReaderAsyncHttpServerRequestMapper<>(reader);
    }

    @Json
    @DefaultComponent
    default <T> JsonWriterHttpServerResponseMapper<T> jsonResponseMapper(JsonWriter<T> writer) {
        return new JsonWriterHttpServerResponseMapper<>(writer);
    }

    @Json
    default <T> JsonWriterHttpServerEntityResponseMapper<T> jsonResponseEntityMapper(JsonWriter<T> writer) {
        return new JsonWriterHttpServerEntityResponseMapper<>(writer);
    }

    @Json
    @DefaultComponent
    default <T> JsonHttpClientRequestMapper<T> jsonHttpClientRequestMapper(JsonWriter<T> jsonWriter) {
        return new JsonHttpClientRequestMapper<>(jsonWriter);
    }

    @Json
    @DefaultComponent
    default <T> JsonHttpClientResponseMapper<T> jsonHttpClientResponseMapper(JsonReader<T> jsonReader) {
        return new JsonHttpClientResponseMapper<>(jsonReader);
    }

    @Json
    default <T> JsonAsyncHttpClientResponseMapper<T> jsonAsyncHttpClientResponseMapper(JsonReader<T> jsonReader) {
        return new JsonAsyncHttpClientResponseMapper<>(jsonReader);
    }

    @Json
    default <T> JsonReaderHttpClientResponseEntityMapper<T> jsonReaderHttpClientResponseEntityMapper(JsonReader<T> jsonReader) {
        return new JsonReaderHttpClientResponseEntityMapper<>(jsonReader);
    }

    @Json
    default <T> JsonReaderAsyncHttpClientResponseEntityMapper<T> jsonReaderAsyncHttpClientResponseEntityMapper(JsonReader<T> jsonReader) {
        return new JsonReaderAsyncHttpClientResponseEntityMapper<>(jsonReader);
    }

    @Json
    @DefaultComponent
    default <T> JsonStringParameterConverter<T> jsonStringParameterConverter(JsonWriter<T> writer) {
        return new JsonStringParameterConverter<>(writer);
    }

    @Json
    @DefaultComponent
    default <T> JsonStringParameterReader<T> jsonStringParameterReader(JsonReader<T> reader) {
        return new JsonStringParameterReader<>(reader);
    }

    @Json
    @DefaultComponent
    default <T> JsonKafkaDeserializer<T> jsonKafkaDeserializer(JsonReader<T> reader) {
        return new JsonKafkaDeserializer<>(reader);
    }

    @Json
    @DefaultComponent
    default <T> JsonKafkaSerializer<T> jsonKafkaSerializer(JsonWriter<T> writer) {
        return new JsonKafkaSerializer<>(writer);
    }
}
