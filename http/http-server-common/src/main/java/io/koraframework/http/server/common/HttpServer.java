package io.koraframework.http.server.common;

import io.koraframework.application.graph.Lifecycle;

public interface HttpServer extends Lifecycle {
    int port();
}
