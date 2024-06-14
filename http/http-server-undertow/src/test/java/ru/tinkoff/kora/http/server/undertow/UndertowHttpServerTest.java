package ru.tinkoff.kora.http.server.undertow;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.server.common.*;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.undertow.pool.KoraByteBufferPool;

class UndertowHttpServerTest extends HttpServerTestKit {

    @Override
    protected HttpServer httpServer(ValueOf<HttpServerConfig> config, PublicApiHandler publicApiHandler) {
        return new UndertowHttpServer(
            config,
            valueOf(new UndertowPublicApiHandler(publicApiHandler, null)),
            null,
            new KoraByteBufferPool(false, 1024)
        );
    }

    @Override
    protected PrivateHttpServer privateHttpServer(ValueOf<HttpServerConfig> config, PrivateApiHandler privateApiHandler) {
        return new UndertowPrivateHttpServer(config, valueOf(new UndertowPrivateApiHandler(privateApiHandler)), null, new KoraByteBufferPool(false, 1024));
    }
}
