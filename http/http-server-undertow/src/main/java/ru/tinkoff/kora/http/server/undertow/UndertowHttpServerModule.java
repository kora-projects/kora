package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import jakarta.annotation.Nullable;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracerFactory;

public interface UndertowHttpServerModule extends UndertowModule {

    default UndertowPublicApiHandler undertowPublicApiHandler(PublicApiHandler publicApiHandler,
                                                              @Nullable HttpServerTracerFactory tracerFactory,
                                                              HttpServerConfig config) {
        var tracer = tracerFactory == null ? null : tracerFactory.get(config.telemetry().tracing());
        return new UndertowPublicApiHandler(publicApiHandler, tracer);
    }

    @Root
    default UndertowHttpServer undertowHttpServer(ValueOf<HttpServerConfig> config,
                                                  ValueOf<UndertowPublicApiHandler> handler,
                                                  @Tag(Undertow.class) XnioWorker worker,
                                                  @Tag(Undertow.class) ByteBufferPool byteBufferPool) {
        return new UndertowHttpServer(config, handler, worker, byteBufferPool);
    }

    @DefaultComponent
    default BlockingRequestExecutor undertowBlockingRequestExecutor(@Tag(Undertow.class) XnioWorker xnioWorker) {
        return new BlockingRequestExecutor.Default(xnioWorker);
    }

    @Tag(Undertow.class)
    @DefaultComponent
    default Wrapped<ByteBufferPool> undertowPublicByteBufferPool() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        final boolean directBuffers;
        final int bufferSize;
        final int maxPoolSize = -1; //TODO investigate PlatformDependent#estimateMaxDirectMemory() analogs to check for direct buffer max memory limit or MaxDirectMemorySize flag detection
        //smaller than 64mb of ram we use 512b buffers
        if (maxMemory < 64 * 1024 * 1024) {
            // use 512b buffers
            bufferSize = 512;
            directBuffers = false;
        } else if (maxMemory < 128 * 1024 * 1024) {
            //use 1k buffers
            bufferSize = 1024;
            directBuffers = true;
        } else {
            //use 16k buffers for best performance
            //as 16k is generally the max amount of data that can be sent in a single write() call
            bufferSize = 1024 * 16 - 20;
            directBuffers = true;
        }

        DefaultByteBufferPool pool = new DefaultByteBufferPool(directBuffers, bufferSize, maxPoolSize, 4);
        return new LifecycleWrapper<>(pool, p -> {}, ByteBufferPool::close);
    }
}
