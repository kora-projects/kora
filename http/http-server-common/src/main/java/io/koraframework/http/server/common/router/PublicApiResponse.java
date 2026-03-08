package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.HttpServerResponse;

public interface PublicApiResponse {
    @Nullable
    HttpServerResponse response();

    @Nullable
    Throwable error();

    void closeSendResponseSuccess(int responseCode, @Nullable HttpHeaders headers, @Nullable Throwable t);

    void closeBodyError(int responseCode, Throwable t);

    void closeConnectionError(int responseCode, Throwable t);
}
