package ru.tinkoff.kora.json.module;

import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.module.http.client.JsonHttpClientRequestMapper;
import ru.tinkoff.kora.json.module.http.client.JsonHttpClientResponseMapper;
import ru.tinkoff.kora.json.module.http.server.JsonReaderHttpServerRequestMapper;
import ru.tinkoff.kora.json.module.http.server.JsonWriterHttpServerResponseMapper;

public interface JsonModule extends JsonCommonModule {
    default <T> JsonReaderHttpServerRequestMapper<T> jsonRequestMapper(JsonReader<T> reader) {
        return new JsonReaderHttpServerRequestMapper<>(reader);
    }

    default <T> JsonWriterHttpServerResponseMapper<T> jsonResponseMapper(JsonWriter<T> writer) {
        return new JsonWriterHttpServerResponseMapper<>(writer);
    }

    default <T> JsonHttpClientRequestMapper<T> jsonHttpClientRequestMapper(JsonWriter<T> jsonWriter) {
        return new JsonHttpClientRequestMapper<>(jsonWriter);
    }

    default <T> JsonHttpClientResponseMapper<T> jsonHttpClientResponseMapper(JsonReader<T> jsonReader) {
        return new JsonHttpClientResponseMapper<>(jsonReader);
    }
}
