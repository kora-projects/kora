package ru.tinkoff.kora.http.client.ok;

import okhttp3.Response;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.IOException;

public final class OkHttpResponse implements HttpClientResponse {
    private final Response response;

    public OkHttpResponse(Response response) {
        this.response = response;
    }

    @Override
    public int code() {
        return this.response.code();
    }

    @Override
    public HttpHeaders headers() {
        return new OkHttpHeaders(this.response.headers());
    }

    @Override
    public HttpBodyInput body() {
        return new OkHttpResponseBody(response.body());
    }

    @Override
    public void close() throws IOException {
        this.response.close();
    }
}
