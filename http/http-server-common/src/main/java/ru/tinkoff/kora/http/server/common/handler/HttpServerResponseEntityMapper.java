package ru.tinkoff.kora.http.server.common.handler;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;

import java.util.List;
import java.util.Map;

public class HttpServerResponseEntityMapper<T> implements HttpServerResponseMapper<HttpServerResponseEntity<T>> {
    private final HttpServerResponseMapper<T> delegate;

    public HttpServerResponseEntityMapper(HttpServerResponseMapper<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<? extends HttpServerResponse> apply(HttpServerResponseEntity<T> result) {
        return delegate.apply(result.body())
            .map(response -> {
                HttpHeaders headers;
                if (result.headers().size() == 0) {
                    headers = response.headers();
                } else if (response.headers().size() == 0) {
                    headers = result.headers();
                } else {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Map.Entry<String, List<String>>[] entries = new Map.Entry[response.headers().size() + result.headers().size()];
                    var i = 0;
                    for (var entry : response.headers()) {
                        entries[i++] = entry;
                    }
                    for (var entry : result.headers()) {
                        entries[i++] = entry;
                    }

                    headers = HttpHeaders.of(entries);
                }

                return new SimpleHttpServerResponse(
                    result.code(),
                    response.contentType(),
                    headers,
                    response.contentLength(),
                    response.body()
                );
            });
    }
}
