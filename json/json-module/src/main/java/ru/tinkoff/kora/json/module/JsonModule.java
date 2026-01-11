package ru.tinkoff.kora.json.module;

import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.module.http.client.JsonHttpClientRequestMapper;
import ru.tinkoff.kora.json.module.http.client.JsonHttpClientResponseMapper;
import ru.tinkoff.kora.json.module.http.client.JsonReaderHttpClientResponseEntityMapper;
import ru.tinkoff.kora.json.module.http.client.JsonStringParameterConverter;

public interface JsonModule extends JsonCommonModule {
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

}
