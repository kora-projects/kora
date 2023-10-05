package ru.tinkoff.kora.http.client.async.response;

import org.asynchttpclient.HttpResponseStatus;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.async.AsyncHttpClientHeaders;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.DefaultFullHttpBody;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpInBody;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.nio.ByteBuffer;

public class SingleBufferAsyncHttpClientResponse implements HttpClientResponse {
    private final HttpResponseStatus responseStatus;
    private final io.netty.handler.codec.http.HttpHeaders headers;
    private final DefaultFullHttpBody body;

    public SingleBufferAsyncHttpClientResponse(Context ctx, HttpResponseStatus responseStatus, io.netty.handler.codec.http.HttpHeaders headers, ByteBuffer body) {
        this.responseStatus = responseStatus;
        this.headers = headers;
        this.body = HttpBody.of(ctx, this.headers.get("content-type"), body.slice());
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
    public HttpInBody body() {
        return this.body;
    }

    @Override
    public void close() {
    }

}
