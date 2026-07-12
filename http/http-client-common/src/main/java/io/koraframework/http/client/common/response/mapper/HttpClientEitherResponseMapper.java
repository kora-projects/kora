package io.koraframework.http.client.common.response.mapper;

import io.koraframework.common.Either;
import io.koraframework.http.client.common.exception.HttpClientDecoderException;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public final class HttpClientEitherResponseMapper<T, E> implements HttpClientResponseMapper<Either<T, E>> {
    private final HttpClientResponseMapper<T> successMapper;
    private final HttpClientResponseMapper<E> errorMapper;

    public HttpClientEitherResponseMapper(HttpClientResponseMapper<T> successMapper, HttpClientResponseMapper<E> errorMapper) {
        this.successMapper = successMapper;
        this.errorMapper = errorMapper;
    }

    @Override
    public @Nullable Either<T, E> apply(HttpClientResponse response) throws IOException, HttpClientDecoderException {
        return response.code() >= 200 && response.code() < 300
            ? Either.left(this.successMapper.apply(response))
            : Either.right(this.errorMapper.apply(response));
    }
}
