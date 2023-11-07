package ru.tinkoff.kora.http.server.undertow;

import jakarta.annotation.Nullable;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracerFactory;

public interface UndertowHttpServerModule extends UndertowModule {
    default UndertowPublicApiHandler undertowPublicApiHandler(PublicApiHandler publicApiHandler, @Nullable HttpServerTracerFactory tracerFactory, HttpServerConfig config) {
        var tracer = tracerFactory == null ? null : tracerFactory.get(config.telemetry().tracing());
        return new UndertowPublicApiHandler(publicApiHandler, tracer);
    }

    @Root
    default UndertowHttpServer undertowHttpServer(ValueOf<HttpServerConfig> config, ValueOf<UndertowPublicApiHandler> handler, XnioWorker worker) {
        return new UndertowHttpServer(config, handler, worker);
    }

    @DefaultComponent
    default BlockingRequestExecutor undertowBlockingRequestExecutor(XnioWorker xnioWorker) {
        return new BlockingRequestExecutor.Default(xnioWorker);
    }
}
