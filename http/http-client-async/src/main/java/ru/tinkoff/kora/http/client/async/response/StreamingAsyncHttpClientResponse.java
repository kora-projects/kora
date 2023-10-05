package ru.tinkoff.kora.http.client.async.response;

import org.asynchttpclient.HttpResponseStatus;
import ru.tinkoff.kora.http.client.async.AsyncHttpClientHeaders;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public class StreamingAsyncHttpClientResponse implements HttpClientResponse {
    private final HttpResponseStatus responseStatus;
    private final io.netty.handler.codec.http.HttpHeaders headers;
    private final AsyncHttpClientStreamingResponseBody body;

    public StreamingAsyncHttpClientResponse(HttpResponseStatus responseStatus, io.netty.handler.codec.http.HttpHeaders headers, Flow.Publisher<ByteBuffer> body) {
        this.responseStatus = responseStatus;
        this.headers = headers;
        this.body = new AsyncHttpClientStreamingResponseBody(headers, body);
    }

    @Override
    public int code() {
        return this.responseStatus.getStatusCode();
    }

    @Override
    public HttpHeaders headers() {
        return new AsyncHttpClientHeaders(this.headers);
    }

    @Override
    public HttpBodyInput body() {
        return this.body;
    }

    @Override
    public void close() throws IOException {
        this.body.close();
    }
}
