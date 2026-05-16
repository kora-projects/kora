package io.koraframework.http.server.common.response;

import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.header.MutableHttpHeaders;
import org.jspecify.annotations.Nullable;

record SimpleHttpServerResponse(int code, MutableHttpHeaders headers, @Nullable HttpBodyOutput body) implements HttpServerResponse {

    @Override
    public String toString() {
        return "SimpleHttpServerResponse{code=" + code() +
               ", bodyLength=" + ((body != null) ? body.contentLength() : -1) +
               ", bodyType=" + ((body != null) ? body.contentType() : -1) +
               ", headers=" + headers.size() +
               '}';
    }
}
