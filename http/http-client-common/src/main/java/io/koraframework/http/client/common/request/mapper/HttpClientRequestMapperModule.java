package io.koraframework.http.client.common.request.mapper;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.form.FormMultipart;
import io.koraframework.http.common.form.FormUrlEncoded;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;

import java.nio.ByteBuffer;

public interface HttpClientRequestMapperModule {

    @DefaultComponent
    default HttpClientRequestMapper<byte[]> httpClientRequestByteArrayMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    @DefaultComponent
    default HttpClientRequestMapper<HttpBodyOutput> httpClientRequestBodyOutputMapper() {
        return body -> body;
    }

    @DefaultComponent
    default HttpClientRequestMapper<ByteBuffer> httpClientRequestByteBufferMapper() {
        return (body) -> HttpBody.octetStream(body);
    }

    @DefaultComponent
    default HttpClientRequestMapper<String> httpClientRequestStringMapper() {
        return (body) -> HttpBody.plaintext(body);
    }

    @DefaultComponent
    default HttpClientRequestMapper<FormUrlEncoded> httpClientRequestFormUrlEncodedMapper() {
        return new FormUrlEncodedClientRequestMapper();
    }

    @DefaultComponent
    default HttpClientRequestMapper<FormMultipart> httpClientRequestFormMultipartMapper() {
        return new FormMultipartClientRequestMapper();
    }

    @Json
    @DefaultComponent
    default <T> HttpClientRequestMapper<T> httpClientRequestJsonMapper(JsonWriter<T> writer) {
        return new JsonHttpClientRequestMapper<>(writer);
    }
}
