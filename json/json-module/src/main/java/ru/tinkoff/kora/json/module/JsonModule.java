package ru.tinkoff.kora.json.module;

import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.module.http.client.JsonHttpClientRequestMapper;
import ru.tinkoff.kora.json.module.http.client.JsonHttpClientResponseMapper;
import ru.tinkoff.kora.json.module.http.client.JsonReaderHttpClientResponseEntityMapper;
import ru.tinkoff.kora.json.module.http.client.JsonStringParameterConverter;
import ru.tinkoff.kora.json.module.http.server.JsonReaderHttpServerRequestMapper;
import ru.tinkoff.kora.json.module.http.server.JsonStringParameterReader;
import ru.tinkoff.kora.json.module.http.server.JsonWriterHttpServerEntityResponseMapper;
import ru.tinkoff.kora.json.module.http.server.JsonWriterHttpServerResponseMapper;
import ru.tinkoff.kora.json.module.kafka.JsonKafkaDeserializer;
import ru.tinkoff.kora.json.module.kafka.JsonKafkaSerializer;

public interface JsonModule extends JsonCommonModule {
    @Json
    default <T> JsonReaderHttpServerRequestMapper<T> jsonRequestMapper(JsonReader<T> reader) {
        return new JsonReaderHttpServerRequestMapper<>(reader);
    }

    @Json
    default <T> JsonWriterHttpServerResponseMapper<T> jsonResponseMapper(JsonWriter<T> writer) {
        return new JsonWriterHttpServerResponseMapper<>(writer);
    }

    @Json
    default <T> JsonWriterHttpServerEntityResponseMapper<T> jsonResponseEntityMapper(JsonWriter<T> writer) {
        return new JsonWriterHttpServerEntityResponseMapper<>(writer);
    }

    @Json
    default <T> JsonHttpClientRequestMapper<T> jsonHttpClientRequestMapper(JsonWriter<T> jsonWriter) {
        return new JsonHttpClientRequestMapper<>(jsonWriter);
    }

    @Json
    default <T> JsonHttpClientResponseMapper<T> jsonHttpClientResponseMapper(JsonReader<T> jsonReader) {
        return new JsonHttpClientResponseMapper<>(jsonReader);
    }

    @Json
    default <T> JsonReaderHttpClientResponseEntityMapper<T> jsonReaderHttpClientResponseEntityMapper(JsonReader<T> jsonReader) {
        return new JsonReaderHttpClientResponseEntityMapper<>(jsonReader);
    }

    @Json
    default <T> JsonStringParameterConverter<T> jsonStringParameterConverter(JsonWriter<T> writer) {
        return new JsonStringParameterConverter<>(writer);
    }

    @Json
    default <T> JsonStringParameterReader<T> jsonStringParameterReader(JsonReader<T> reader) {
        return new JsonStringParameterReader<>(reader);
    }

    @Json
    default <T> JsonKafkaDeserializer<T> jsonKafkaDeserializer(JsonReader<T> reader) {
        return new JsonKafkaDeserializer<>(reader);
    }

    @Json
    default <T> JsonKafkaSerializer<T> jsonKafkaSerializer(JsonWriter<T> writer) {
        return new JsonKafkaSerializer<>(writer);
    }
}
