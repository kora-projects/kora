package ru.tinkoff.kora.http.client.common.response;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.common.HttpResponseEntity;

import java.io.IOException;

public class HttpClientResponseEntityMapper<T> implements HttpClientResponseMapper<HttpResponseEntity<T>> {
    private final HttpClientResponseMapper<T> delegate;

    public HttpClientResponseEntityMapper(HttpClientResponseMapper<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public @Nullable HttpResponseEntity<T> apply(HttpClientResponse response) throws IOException, HttpClientDecoderException {
        var result = this.delegate.apply(response);
        return HttpResponseEntity.of(response.code(), response.headers().toMutable(), result);
    }
}
