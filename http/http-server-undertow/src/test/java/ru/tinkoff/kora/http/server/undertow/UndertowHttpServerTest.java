package ru.tinkoff.kora.http.server.undertow;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerTestKit;
import ru.tinkoff.kora.http.server.common.router.HttpServerHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;

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
