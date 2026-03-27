package io.koraframework.validation.module.http.server;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.server.common.interceptor.HttpServerInterceptor;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import io.koraframework.validation.common.ViolationException;

public final class ValidationHttpServerInterceptor implements HttpServerInterceptor {

    @Nullable
    private final ViolationExceptionHttpServerResponseMapper mapper;

    public ValidationHttpServerInterceptor(@Nullable ViolationExceptionHttpServerResponseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public HttpServerResponse intercept(HttpServerRequest request, InterceptChain chain) throws Exception {
        try {
            return chain.process(request);
        } catch (ViolationException e) {
            return toResponse(request, e);
        }
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
