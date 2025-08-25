package ru.tinkoff.kora.http.server.undertow;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.server.common.*;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

class UndertowHttpServerTest extends HttpServerTestKit {

    @Override
    protected HttpServer httpServer(ValueOf<HttpServerConfig> config, PublicApiHandler publicApiHandler) {
        return new UndertowHttpServer(
            config,
            valueOf(publicApiHandler),
            "test",
            null,
            null
        );
    }

    @Override
    protected PrivateHttpServer privateHttpServer(ValueOf<HttpServerConfig> config, PrivateApiHandler privateApiHandler) {
        return new UndertowPrivateHttpServer(config, valueOf(new UndertowPrivateApiHandler(privateApiHandler)), null);
    }
}
