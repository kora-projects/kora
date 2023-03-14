package ru.tinkoff.kora.validation.module.http.server;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.server.common.HttpServerInterceptor;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.validation.common.ViolationException;

import javax.annotation.Nullable;
import java.util.function.Function;

public final class ValidationHttpServerInterceptor implements HttpServerInterceptor {
    @Nullable
    private final ViolationExceptionHttpServerResponseMapper mapper;

    public ValidationHttpServerInterceptor(@Nullable ViolationExceptionHttpServerResponseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<HttpServerResponse> intercept(HttpServerRequest request, Function<HttpServerRequest, Mono<HttpServerResponse>> chain) {
        Mono<HttpServerResponse> rsMono;
        try {
            rsMono = chain.apply(request);
        } catch (ViolationException e) {
            return Mono.just(this.toResponse(request, e));
        }
        return rsMono.onErrorResume(ViolationException.class, e -> Mono.just(this.toResponse(request, e)));
    }

    private HttpServerResponse toResponse(HttpServerRequest request, ViolationException exception) {
        if (this.mapper != null) {
            var response = this.mapper.apply(request, exception);
            if (response != null) {
                return response;
            }
        }
        var message = exception.getMessage();
        return HttpServerResponseException.of(400, message);
    }
}
