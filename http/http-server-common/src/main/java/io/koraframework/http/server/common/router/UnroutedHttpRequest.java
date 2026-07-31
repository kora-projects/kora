package io.koraframework.http.server.common.router;

import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;

import java.util.List;
import java.util.Map;

public interface UnroutedHttpRequest {

    String host();

    String scheme();

    String method();

    String path();

    HttpHeaders headers();

    Map<String, List<String>> queryParams();

    HttpBodyInput body();

    long requestStartTimeInNanos();
}
