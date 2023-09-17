package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HttpServerResponseEntityMapper<T> implements HttpServerResponseMapper<HttpServerResponseEntity<T>> {
    private final HttpServerResponseMapper<T> delegate;

    public HttpServerResponseEntityMapper(HttpServerResponseMapper<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public HttpServerResponse apply(Context ctx, HttpServerRequest request, HttpServerResponseEntity<T> result) throws IOException {
        var response = delegate.apply(ctx, request, result.body());

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

        return HttpServerResponse.of(
            result.code(),
            headers,
            response.body()
        );

    }
}
