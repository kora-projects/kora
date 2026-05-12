package io.koraframework.s3.client.kora.model.response;

import io.koraframework.http.client.common.response.HttpClientResponse;

public interface GetObjectResult extends HttpClientResponse {

    record ContentRange(long firstPosition, long lastPosition, long completeLength) {}

    ContentRange contentRange();
}
