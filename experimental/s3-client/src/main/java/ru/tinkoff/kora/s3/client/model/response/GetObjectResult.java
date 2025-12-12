package ru.tinkoff.kora.s3.client.model.response;

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

public interface GetObjectResult extends HttpClientResponse {
    record ContentRange(long firstPosition, long lastPosition, long completeLength) {}

    ContentRange contentRange();
}
