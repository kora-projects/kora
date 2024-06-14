package ru.tinkoff.kora.http.server.undertow;

import io.undertow.connector.ByteBufferPool;
import jakarta.annotation.Nullable;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracerFactory;
import ru.tinkoff.kora.http.server.undertow.pool.KoraByteBufferPool;

public interface UndertowHttpServerModule extends UndertowModule {
    default UndertowPublicApiHandler undertowPublicApiHandler(PublicApiHandler publicApiHandler, @Nullable HttpServerTracerFactory tracerFactory, HttpServerConfig config) {
        var tracer = tracerFactory == null ? null : tracerFactory.get(config.telemetry().tracing());
        return new UndertowPublicApiHandler(publicApiHandler, tracer);
    }

    @Root
    default UndertowHttpServer undertowHttpServer(ValueOf<HttpServerConfig> config, ValueOf<UndertowPublicApiHandler> handler, XnioWorker worker, ByteBufferPool byteBufferPool) {
        return new UndertowHttpServer(config, handler, worker, byteBufferPool);
    }

    @DefaultComponent
    default BlockingRequestExecutor undertowBlockingRequestExecutor(XnioWorker xnioWorker) {
        return new BlockingRequestExecutor.Default(xnioWorker);
    }

    @DefaultComponent
    default ByteBufferPool undertowByteBufferPool() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        //smaller than 64mb of ram we use 512b buffers
        if (maxMemory < 64 * 1024 * 1024) {
            //use 512b buffers
            return new KoraByteBufferPool(false, 512, -1, 4);
        } else if (maxMemory < 128 * 1024 * 1024) {
            //use 1k buffers
            return new KoraByteBufferPool(true, 1024, -1, 4);
        } else {
            //use 16k buffers for best performance
            //as 16k is generally the max amount of data that can be sent in a single write() call
            return new KoraByteBufferPool(true, 1024 * 16 - 20, -1, 4);
        }
    }
}
