package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import jakarta.annotation.Nullable;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracerFactory;
import ru.tinkoff.kora.http.server.undertow.pool.KoraByteBufferPool;

public interface UndertowHttpServerModule extends UndertowModule {

    @Tag(PublicApiHandler.class)
    default HttpHandler undertowPublicApiHandler(PublicApiHandler publicApiHandler,
                                                              @Nullable HttpServerTracerFactory tracerFactory,
                                                              HttpServerConfig config) {
        var tracer = tracerFactory == null ? null : tracerFactory.get(config.telemetry().tracing());
        return new UndertowPublicApiHandler(publicApiHandler, tracer);
    }

    @Root
    default UndertowHttpServer undertowHttpServer(ValueOf<HttpServerConfig> config,
                                                  @Tag(PublicApiHandler.class) ValueOf<HttpHandler> handler,
                                                 @Tag(Undertow.class) XnioWorker worker,
                                                  ByteBufferPool byteBufferPool) {
        return new UndertowHttpServer("Public", config, handler, worker, byteBufferPool);
    }

    @DefaultComponent
    default BlockingRequestExecutor undertowBlockingRequestExecutor(@Tag(Undertow.class) XnioWorker xnioWorker) {
        return new BlockingRequestExecutor.Default(xnioWorker);
    }
}
