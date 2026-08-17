package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.HttpServerTestKit;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.undertow.handler.KoraRequestProcessingHttpHandler;
import io.koraframework.http.server.undertow.handler.KoraVirtualThreadDispatchHttpHandler;

class UndertowHttpServerTest extends HttpServerTestKit {

    @Override
    protected HttpServer httpServer(ValueOf<? extends HttpServerConfig> config, HttpServerRouter httpServerRouter, HttpServerTelemetry telemetry) {
        return new UndertowHttpServer(
            "test",
            valueOf(new KoraVirtualThreadDispatchHttpHandler("uvt", new KoraRequestProcessingHttpHandler(config.get(), httpServerRouter, telemetry))),
            null,
            config,
            null,
            null
        );
    }
}
