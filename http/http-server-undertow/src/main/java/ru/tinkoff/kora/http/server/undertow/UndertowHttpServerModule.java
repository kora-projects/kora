package ru.tinkoff.kora.http.server.undertow;

import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;

import javax.annotation.Nullable;

public interface UndertowHttpServerModule extends UndertowModule {
    default UndertowPublicApiHandler undertowPublicApiHandler(PublicApiHandler publicApiHandler, @Nullable HttpServerTracer tracer) {
        return new UndertowPublicApiHandler(publicApiHandler, tracer);
    }

    @Root
    default UndertowHttpServer undertowHttpServer(ValueOf<HttpServerConfig> config, ValueOf<UndertowPublicApiHandler> handler, XnioWorker worker) {
        return new UndertowHttpServer(config, handler, worker);
    }

    default BlockingRequestExecutor undertowBlockingRequestExecutor(XnioWorker xnioWorker) {
        return new BlockingRequestExecutor.Default(xnioWorker);
    }
}
