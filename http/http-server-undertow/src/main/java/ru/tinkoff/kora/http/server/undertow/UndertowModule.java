package ru.tinkoff.kora.http.server.undertow;

import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerModule;
import ru.tinkoff.kora.http.server.common.PrivateApiHandler;

import java.util.concurrent.ExecutionException;

public interface UndertowModule extends HttpServerModule {
    default UndertowPrivateApiHandler undertowPrivateApiHandler(PrivateApiHandler privateApiHandler) {
        return new UndertowPrivateApiHandler(privateApiHandler);
    }

    @Root
    default UndertowPrivateHttpServer undertowPrivateHttpServer(ValueOf<HttpServerConfig> configValue, ValueOf<UndertowPrivateApiHandler> privateApiHandler, XnioWorker xnioWorker) {
        return new UndertowPrivateHttpServer(configValue, privateApiHandler, xnioWorker);
    }

    default Wrapped<XnioWorker> xnioWorker(ValueOf<HttpServerConfig> configValue) throws ExecutionException, InterruptedException {
        return new XnioLifecycle(configValue);
    }
}
