package io.koraframework.http.client.common.response.mapper;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import org.jspecify.annotations.Nullable;
import io.koraframework.http.client.common.exception.HttpClientDecoderException;
import io.koraframework.http.common.HttpResponseEntity;

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
