package io.koraframework.http.server.common.telemetry;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;

public interface HttpServerObservation extends Observation {

    void observeResultCode(HttpResultCode resultCode);

    HttpServerRequest observeRequest(HttpServerRequest request);

    HttpServerResponse observeResponse(HttpServerResponse response);
}
