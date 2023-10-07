package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import io.undertow.server.handlers.GracefulShutdownHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class UndertowHttpServer implements HttpServer, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(UndertowHttpServer.class);

    private final AtomicReference<HttpServerState> state = new AtomicReference<>(HttpServerState.INIT);
    private final ValueOf<HttpServerConfig> config;
    private final ValueOf<UndertowPublicApiHandler> publicApiHandler;
    private final GracefulShutdownHandler gracefulShutdown;
    private final XnioWorker xnioWorker;
    private volatile Undertow undertow;

    public UndertowHttpServer(ValueOf<HttpServerConfig> config, ValueOf<UndertowPublicApiHandler> publicApiHandler, @Nullable XnioWorker xnioWorker) {
        this.config = config;
        this.publicApiHandler = publicApiHandler;
        this.xnioWorker = xnioWorker;
        this.gracefulShutdown = new GracefulShutdownHandler(exchange -> this.publicApiHandler.get().handleRequest(exchange));
    }

    @Override
    public void release() {
        this.state.set(HttpServerState.SHUTDOWN);
        try {
            Thread.sleep(this.config.get().shutdownWait().toMillis());
        } catch (InterruptedException e) {
        }
        logger.debug("Public HTTP Server (Undertow) stopping...");
        final long started = System.nanoTime();
        this.gracefulShutdown.shutdown();
        try {
            logger.debug("Public HTTP Server (Undertow) awaiting graceful shutdown...");
            this.gracefulShutdown.awaitShutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (this.undertow != null) {
            this.undertow.stop();
            this.undertow = null;
        }
        logger.info("Public HTTP Server (Undertow) stopped in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public void init() {
        logger.debug("Public HTTP Server (Undertow) starting...");
        final long started = System.nanoTime();
        this.gracefulShutdown.start();
        this.undertow = this.createServer();
        this.undertow.start();
        this.state.set(HttpServerState.RUN);
        var data = StructuredArgument.marker("port", this.port());
        logger.info(data, "Public HTTP Server (Undertow) started in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    private Undertow createServer() {
        return Undertow.builder()
            .addHttpListener(this.config.get().publicApiHttpPort(), "0.0.0.0", this.gracefulShutdown)
            .setWorker(this.xnioWorker)
            .build();
    }

    @Override
    public int port() {
        if (this.undertow == null) {
            return -1;
        }
        var infos = this.undertow.getListenerInfo();
        var address = (InetSocketAddress) infos.get(0).getAddress();
        return address.getPort();
    }

    @Override
    public ReadinessProbeFailure probe() {
        return switch (this.state.get()) {
            case INIT -> new ReadinessProbeFailure("Public HTTP Server (Undertow) init");
            case RUN -> null;
            case SHUTDOWN -> new ReadinessProbeFailure("Public HTTP Server (Undertow) shutdown");
        };
    }

    private enum HttpServerState {
        INIT, RUN, SHUTDOWN
    }
}
