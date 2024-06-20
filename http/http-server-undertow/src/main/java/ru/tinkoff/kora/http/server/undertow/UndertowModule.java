package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerModule;
import ru.tinkoff.kora.http.server.common.PrivateApiHandler;
import ru.tinkoff.kora.http.server.undertow.pool.KoraByteBufferPool;

import java.util.concurrent.ExecutionException;

public interface UndertowModule extends HttpServerModule {
    default UndertowPrivateApiHandler undertowPrivateApiHandler(PrivateApiHandler privateApiHandler) {
        return new UndertowPrivateApiHandler(privateApiHandler);
    }

    @Root
    default UndertowPrivateHttpServer undertowPrivateHttpServer(ValueOf<HttpServerConfig> configValue,
                                                                ValueOf<UndertowPrivateApiHandler> privateApiHandler,
                                                                @Tag(Undertow.class) XnioWorker xnioWorker,
                                                                ByteBufferPool byteBufferPool) {
        return new UndertowPrivateHttpServer(configValue, privateApiHandler, xnioWorker, byteBufferPool);
    }

    @Tag(Undertow.class)
    default Wrapped<XnioWorker> xnioWorker(ValueOf<HttpServerConfig> configValue) throws ExecutionException, InterruptedException {
        return new XnioLifecycle(configValue);
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
