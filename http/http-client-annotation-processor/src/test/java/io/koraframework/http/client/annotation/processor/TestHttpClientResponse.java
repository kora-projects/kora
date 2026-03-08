package io.koraframework.http.client.annotation.processor;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.header.MutableHttpHeaders;

import java.io.IOException;

public record TestHttpClientResponse(int code, MutableHttpHeaders headers, HttpBodyInput body) implements HttpClientResponse {

    public static TestHttpClientResponse response(int code) {
        return new TestHttpClientResponse(code, HttpHeaders.of(), HttpBody.empty());
    }

    public TestHttpClientResponse withHeader(String key, String value) {
        return new TestHttpClientResponse(code, headers.set(key, value), body);
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
