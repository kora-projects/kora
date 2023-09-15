package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.HttpServerModule;
import ru.tinkoff.kora.http.server.common.PrivateApiHandler;

import java.util.concurrent.TimeUnit;

public interface UndertowModule extends HttpServerModule {
    default UndertowPrivateApiHandler undertowPrivateApiHandler(PrivateApiHandler privateApiHandler) {
        return new UndertowPrivateApiHandler(privateApiHandler);
    }

    @Root
    default UndertowPrivateHttpServer undertowPrivateHttpServer(ValueOf<HttpServerConfig> configValue, ValueOf<UndertowPrivateApiHandler> privateApiHandler, XnioWorker xnioWorker) {
        return new UndertowPrivateHttpServer(configValue, privateApiHandler, xnioWorker);
    }

    default Wrapped<XnioWorker> xnioWorker(ValueOf<HttpServerConfig> configValue) {
        var threads = configValue.get().blockingThreads();
        var ioThreads = configValue.get().ioThreads();

        var worker = Xnio.getInstance(Undertow.class.getClassLoader())
            .createWorkerBuilder()
            .setCoreWorkerPoolSize(1)
            .setMaxWorkerPoolSize(threads)
            .setWorkerIoThreads(ioThreads)
            .setWorkerKeepAlive(60 * 1000)
            .setDaemon(false)
            .setWorkerName("kora-undertow")
            .build();
        return new LifecycleWrapper<>(worker, v -> {}, v -> {
            worker.shutdown();
            worker.awaitTermination(1, TimeUnit.MINUTES);
        });
    }
}
