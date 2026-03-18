package io.koraframework.validation.module.http.server;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.validation.common.ViolationException;

public interface ViolationExceptionHttpServerResponseMapper {
    @Nullable
    HttpServerResponse apply(HttpServerRequest request, ViolationException exception);
}
