package ru.tinkoff.kora.http.server.common.router;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

public interface PublicApiResponse {
    @Nullable
    HttpServerResponse response();

    @Nullable
    Throwable error();

    void closeSendResponseSuccess(int responseCode, @Nullable HttpHeaders headers, @Nullable Throwable t);

    void closeBodyError(int responseCode, Throwable t);

    void closeConnectionError(int responseCode, Throwable t);
}
