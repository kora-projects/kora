package ru.tinkoff.kora.http.server.common;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

public record SimpleHttpServerResponse(int code, MutableHttpHeaders headers, @Nullable HttpBodyOutput body) implements HttpServerResponse {


    @Override
    public String toString() {
        return "HttpServerResponseException{code=" + code() +
               ", bodyLength=" + ((body != null) ? body.contentLength() : -1) +
               ", bodyType=" + ((body != null) ? body.contentType() : -1) +
               '}';
    }
}
