package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface HttpServerResponseMapperModule {

    default HttpServerResponseMapper<HttpServerResponse> noopResponseMapper() {
        return (ctx, request, r) -> r;
    }

    default HttpServerResponseMapper<ByteBuffer> byteBufBodyResponseMapper() {
        return (ctx, request, r) -> HttpServerResponse.of(200, HttpBody.octetStream(r));
    }

    default HttpServerResponseMapper<byte[]> byteArrayResponseMapper() {
        return (ctx, request, r) -> HttpServerResponse.of(200, HttpBody.octetStream(r));
    }

    default HttpServerResponseMapper<String> stringResponseMapper() {
        return (ctx, request, r) -> HttpServerResponse.of(200, HttpBody.plaintext(String.valueOf(r)));
    }

    @DefaultComponent
    default HttpServerResponseMapper<Number> numberResponseMapper() {
        return (ctx, request, r) -> HttpServerResponse.of(200, HttpBody.plaintext(String.valueOf(r)));
    }

    @DefaultComponent
    default <T> HttpServerResponseMapper<Set<T>> setResponseMapper() {
        return (ctx, request, r) -> {
            var res = (r == null) ? Collections.emptySet() : r;
            return HttpServerResponse.of(200, HttpBody.plaintext(res.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"))));
        };
    }

    @DefaultComponent
    default <T> HttpServerResponseMapper<List<T>> listResponseMapper() {
        return (ctx, request, r) -> {
            var res = (r == null) ? Collections.emptyList() : r;
            return HttpServerResponse.of(200, HttpBody.plaintext(res.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"))));
        };
    }

    @DefaultComponent
    default <T> HttpServerResponseMapper<Collection<T>> collectionResponseMapper() {
        return (ctx, request, r) -> {
            var res = (r == null) ? Collections.emptyList() : r;
            return HttpServerResponse.of(200, HttpBody.plaintext(res.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"))));
        };
    }

    default <T> HttpServerResponseMapper<HttpResponseEntity<T>> httpServerResponseEntityMapper(HttpServerResponseMapper<T> delegate) {
        return new HttpServerResponseEntityMapper<>(delegate);
    }
}
