package io.koraframework.http.client.common.telemetry;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;

public interface HttpClientObservation extends Observation {
    HttpClientRequest observeRequest(HttpClientRequest rq);

    HttpClientResponse observeResponse(HttpClientResponse rs);
}
