package ru.tinkoff.kora.http.client.annotation.processor;

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpInBody;

import java.io.IOException;

public record TestHttpClientResponse(int code, HttpHeaders headers, HttpInBody body) implements HttpClientResponse {

    public static TestHttpClientResponse response(int code) {
        return new TestHttpClientResponse(code, HttpHeaders.of(), HttpBody.empty());
    }

    public TestHttpClientResponse withHeader(String key, String value) {
        return new TestHttpClientResponse(code, headers.with(key, value), body);
    }

    public TestHttpClientResponse withBody(String contentType, byte[] body) {
        return new TestHttpClientResponse(code, HttpHeaders.of(), HttpBody.of(contentType, body));
    }

    public TestHttpClientResponse withBody(String body) {
        return new TestHttpClientResponse(code, HttpHeaders.of(), HttpBody.plaintext(body));
    }

    public TestHttpClientResponse withCode(int code) {
        return new TestHttpClientResponse(code, headers, body);
    }

    @Override
    public void close() throws IOException {

    }

}
