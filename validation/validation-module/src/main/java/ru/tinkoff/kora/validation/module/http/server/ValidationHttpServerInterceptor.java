package ru.tinkoff.kora.validation.module.http.server;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.HttpServerInterceptor;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.validation.common.ViolationException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class ValidationHttpServerInterceptor implements HttpServerInterceptor {

    @Nullable
    private final ViolationExceptionHttpServerResponseMapper mapper;

    public ValidationHttpServerInterceptor(@Nullable ViolationExceptionHttpServerResponseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain) throws Exception {
        try {
            return chain.process(context, request).exceptionallyCompose(e -> {
                if (e instanceof ViolationException ve) {
                    return CompletableFuture.completedFuture(toResponse(context, request, ve));
                } else if (e instanceof CompletionException ce && ce.getCause() instanceof ViolationException ve) {
                    return CompletableFuture.completedFuture(toResponse(context, request, ve));
                } else {
                    return CompletableFuture.failedFuture(e);
                }
            });
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof ViolationException ve) {
                return CompletableFuture.completedFuture(toResponse(context, request, ve));
            }
            throw ce;
        } catch (ViolationException e) {
            return CompletableFuture.completedFuture(toResponse(context, request, e));
        }
    }

    private HttpServerResponse toResponse(Context contextFromChain, HttpServerRequest request, ViolationException exception) {
        if (this.mapper != null) {
            final Context current = Context.current();
            try {
                contextFromChain.inject();
                var response = this.mapper.apply(request, exception);
                if (response != null) {
                    return response;
                }
            } finally {
                current.inject();
            }
        }
        var message = exception.getMessage();
        return HttpServerResponseException.of(400, message);
    }
}
