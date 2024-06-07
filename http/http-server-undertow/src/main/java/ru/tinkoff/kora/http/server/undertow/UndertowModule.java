package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpHandler;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerModule;
import ru.tinkoff.kora.http.server.common.PrivateApiHandler;

import java.util.concurrent.ExecutionException;

public interface UndertowModule extends HttpServerModule {

    @Tag(PrivateApiHandler.class)
    default HttpHandler undertowPrivateApiHandler(PrivateApiHandler privateApiHandler) {
        return new UndertowPrivateApiHandler(privateApiHandler);
    }

    @Root
    default UndertowPrivateHttpServer undertowPrivateHttpServer(ValueOf<HttpServerConfig> configValue,
                                                                @Tag(PrivateApiHandler.class) ValueOf<HttpHandler> privateApiHandler,
                                                                XnioWorker xnioWorker) {
        return new UndertowPrivateHttpServer(configValue, privateApiHandler, xnioWorker);
    }

    default Wrapped<XnioWorker> xnioWorker(ValueOf<HttpServerConfig> configValue) throws ExecutionException, InterruptedException {
        return new XnioLifecycle(configValue);
    }
}
