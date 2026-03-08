package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.HttpServerTestKit;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;

class UndertowHttpServerTest extends HttpServerTestKit {

    @Override
    protected HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerHandler httpServerHandler, HttpServerTelemetry telemetry) {
        return new UndertowHttpServer(
            config,
            valueOf(httpServerHandler),
            "test",
            telemetry,
            null,
            null
        );
    }
}
