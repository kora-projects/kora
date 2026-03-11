package io.koraframework.validation.module.http.server;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.server.common.HttpServerRequest;
import io.koraframework.http.server.common.HttpServerResponse;
import io.koraframework.validation.common.ViolationException;

public interface ViolationExceptionHttpServerResponseMapper {
    @Nullable
    HttpServerResponse apply(HttpServerRequest request, ViolationException exception);
}
