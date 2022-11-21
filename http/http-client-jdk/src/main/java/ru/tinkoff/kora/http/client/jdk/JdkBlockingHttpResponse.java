package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.http.client.common.UnknownHttpClientException;
import ru.tinkoff.kora.http.client.common.response.BlockingHttpResponse;
import ru.tinkoff.kora.http.common.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

public class JdkBlockingHttpResponse implements BlockingHttpResponse {
    private final HttpResponse<InputStream> response;
    private final JdkHttpClientHeaders headers;

    public JdkBlockingHttpResponse(HttpResponse<InputStream> response) {
        this.response = response;
        this.headers = new JdkHttpClientHeaders(this.response.headers());
    }

    @Override
    public int code() {
        return this.response.statusCode();
    }

    @Override
    public HttpHeaders headers() {
        return this.headers;
    }

    @Override
    public InputStream body() {
        return this.response.body();
    }

    @Override
    public void close() {
        try {
            this.response.body().close();
        } catch (IOException e) {
            throw new UnknownHttpClientException(e);
        }
    }
}
