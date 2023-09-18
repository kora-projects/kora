package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpOutBody;

import jakarta.annotation.Nullable;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpServerResponseException extends RuntimeException implements HttpServerResponse {
    private final int code;
    private final String contentType;
    private final ByteBuffer body;
    private final HttpHeaders headers;

    public HttpServerResponseException(@Nullable Throwable cause, String message, int code, String contentType, ByteBuffer body, HttpHeaders headers) {
        super(message, cause);
        this.code = code;
        this.contentType = contentType;
        this.body = body.slice();
        this.headers = headers;
    }

    public static HttpServerResponseException of(int code, String text) {
        return of(null, code, text);
    }

    public static HttpServerResponseException of(@Nullable Throwable cause, int code, String text) {
        return new HttpServerResponseException(cause, text, code, "text/plain; charset=utf-8", UTF_8.encode(text), HttpHeaders.of());
    }

    @Override
    public int code() {
        return this.code;
    }

    @Override
    public HttpHeaders headers() {
        return this.headers;
    }

    @Override
    public HttpOutBody body() {
        return HttpBody.of(contentType, this.body.slice());
    }

    @Override
    public String toString() {
        return "HttpResponseException{" +
            "message=" + getMessage() +
            "code=" + code +
            ", contentType='" + contentType + '\'' +
            ", headers=" + headers +
            '}';
    }
}
