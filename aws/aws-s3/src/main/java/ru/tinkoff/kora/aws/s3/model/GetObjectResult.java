package ru.tinkoff.kora.aws.s3.model;

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

public interface GetObjectResult extends HttpClientResponse {
    record ContentRange(long firstPosition, long lastPosition, long completeLength) {}

    ContentRange contentRange();
}
