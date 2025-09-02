package ru.tinkoff.kora.http.server.undertow.request;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.io.InputStream;

public final class UndertowRequestHttpBody implements HttpBodyInput {
    private final HttpServerExchange exchange;

    public UndertowRequestHttpBody(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public long contentLength() {
        var contentLengthStr = this.exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
        return contentLengthStr == null ? -1 : Long.parseLong(contentLengthStr);
    }

    @Nullable
    @Override
    public String contentType() {
        return this.exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    @Override
    public InputStream asInputStream() {
        return this.exchange.getInputStream();
    }

    @Override
    public void close() throws IOException {
        this.exchange.getInputStream().close();
    }
}
