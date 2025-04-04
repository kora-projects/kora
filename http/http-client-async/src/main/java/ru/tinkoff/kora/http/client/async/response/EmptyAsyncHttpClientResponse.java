package ru.tinkoff.kora.http.client.async.response;

import org.asynchttpclient.HttpResponseStatus;
import ru.tinkoff.kora.http.client.async.AsyncHttpClientHeaders;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public class EmptyAsyncHttpClientResponse implements HttpClientResponse {
    private final HttpResponseStatus responseStatus;
    private final io.netty.handler.codec.http.HttpHeaders headers;

    public EmptyAsyncHttpClientResponse(HttpResponseStatus responseStatus, io.netty.handler.codec.http.HttpHeaders headers) {
        this.responseStatus = responseStatus;
        this.headers = headers;
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
        return HttpBody.empty();
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
        return "HttpClientResponse{code=" + code() +
               ", bodyLength=-1" +
               ", bodyType=null" +
               '}';
    }
}
