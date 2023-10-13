package ru.tinkoff.kora.http.client.common.response;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.HttpClientUnknownException;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public interface HttpClientResponseMapper<T> extends Mapping.MappingFunction {

    @Nullable
    T apply(HttpClientResponse response) throws IOException, HttpClientDecoderException;

    static <T> HttpClientResponseMapper<T> fromAsync(HttpClientResponseMapper<CompletionStage<T>> delegate) {
        return response -> {
            try {
                return delegate.apply(response).toCompletableFuture().get();
            } catch (InterruptedException e) {
                throw new HttpClientUnknownException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof HttpClientException ce) {
                    throw ce;
                }
                throw new HttpClientUnknownException(Objects.requireNonNullElse(e.getCause(), e));
            }
        };
    }

}
