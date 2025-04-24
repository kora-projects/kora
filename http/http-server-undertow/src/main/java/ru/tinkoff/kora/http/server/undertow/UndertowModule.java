package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerModule;
import ru.tinkoff.kora.http.server.common.PrivateApiHandler;

public interface UndertowModule extends HttpServerModule {

    default UndertowPrivateApiHandler undertowPrivateApiHandler(PrivateApiHandler privateApiHandler) {
        return new UndertowPrivateApiHandler(privateApiHandler);
    }

    @Root
    default UndertowPrivateHttpServer undertowPrivateHttpServer(ValueOf<HttpServerConfig> configValue,
                                                                ValueOf<UndertowPrivateApiHandler> privateApiHandler,
                                                                @Tag(Undertow.class) XnioWorker xnioWorker,
                                                                @Tag(UndertowPrivateHttpServer.class) ByteBufferPool byteBufferPool) {
        return new UndertowPrivateHttpServer(configValue, privateApiHandler, xnioWorker, byteBufferPool);
    }

    @Tag(Undertow.class)
    default Wrapped<XnioWorker> xnioWorker(ValueOf<HttpServerConfig> configValue) {
        return new XnioLifecycle(configValue);
    }

    @Tag(UndertowPrivateHttpServer.class)
    @DefaultComponent
    default Wrapped<ByteBufferPool> undertowPrivateByteBufferPool() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        final boolean directBuffers;
        final int bufferSize;
        final int maxPoolSize;
        //smaller than 64mb of ram we use 512b buffers
        if (maxMemory < 64 * 1024 * 1024) {
            // use 512b buffers
            maxPoolSize = 32;
            bufferSize = 512;
            directBuffers = false;
        } else if (maxMemory < 128 * 1024 * 1024) {
            //use 1k buffers
            maxPoolSize = 16;
            bufferSize = 1024;
            directBuffers = true;
        } else {
            //use 16k buffers for best performance
            //as 16k is generally the max amount of data that can be sent in a single write() call
            maxPoolSize = 8;
            bufferSize = 1024 * 16 - 20;
            directBuffers = true;
        }

        DefaultByteBufferPool pool = new DefaultByteBufferPool(directBuffers, bufferSize, maxPoolSize, 4);
        return new LifecycleWrapper<>(pool, p -> {}, ByteBufferPool::close);
    }
}
