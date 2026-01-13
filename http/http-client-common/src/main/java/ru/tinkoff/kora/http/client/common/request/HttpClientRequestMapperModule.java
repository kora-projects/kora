package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.client.common.form.FormMultipartClientRequestMapper;
import ru.tinkoff.kora.http.client.common.form.FormUrlEncodedClientRequestMapper;
import ru.tinkoff.kora.http.client.common.request.mapper.JsonHttpClientRequestMapper;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.nio.ByteBuffer;

public interface HttpClientRequestMapperModule {

    default HttpClientRequestMapper<byte[]> byteArrayHttpClientRequestMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    default HttpClientRequestMapper<ByteBuffer> byteBufferHttpClientRequestMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    default HttpClientRequestMapper<String> stringHttpClientRequestMapper() {
        return (body) -> HttpBody.plaintext(body);
    }

    default FormUrlEncodedClientRequestMapper formUrlEncodedClientRequestMapper() {
        return new FormUrlEncodedClientRequestMapper();
    }

    default FormMultipartClientRequestMapper formMultipartClientRequestMapper() {
        return new FormMultipartClientRequestMapper();
    }

    @Tag(Json.class)
    default <T> JsonHttpClientRequestMapper<T> jsonHttpClientRequestMapper(JsonWriter<T> writer) {
        return new JsonHttpClientRequestMapper<>(writer);
    }
}
