package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class UndertowHttpServer implements HttpServer, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(UndertowHttpServer.class);

    private final AtomicReference<HttpServerState> state = new AtomicReference<>(HttpServerState.INIT);
    private final String name;
    private final ValueOf<HttpServerConfig> config;
    private final GracefulShutdownHandler gracefulShutdown;
    private final XnioWorker xnioWorker;

    private volatile Undertow undertow;

    public UndertowHttpServer(String name,
                              ValueOf<HttpServerConfig> config,
                              ValueOf<HttpHandler> publicApiHandler,
                              @Nullable XnioWorker xnioWorker) {
        this.name = name;
        this.config = config;
        this.xnioWorker = xnioWorker;
        this.gracefulShutdown = new GracefulShutdownHandler(exchange -> publicApiHandler.get().handleRequest(exchange));
    }

    @Override
    public void release() {
        logger.debug("{} HTTP Server (Undertow) stopping...", name);
        this.state.set(HttpServerState.SHUTDOWN);
        try {
            //TODO ожидать после shutdown уже мб?
            Thread.sleep(this.config.get().shutdownWait().toMillis());
        } catch (InterruptedException e) {
            // ignore
        }
        final long started = TimeUtils.started();
        this.gracefulShutdown.shutdown();
        try {
            logger.debug("{} HTTP Server (Undertow) awaiting graceful shutdown...", name);
            this.gracefulShutdown.awaitShutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (this.undertow != null) {
            this.undertow.stop();
            this.undertow = null;
        }
        logger.info("{} HTTP Server (Undertow) stopped in {}", name, TimeUtils.tookForLogging(started));
    }

    @Override
    public void init() {
        logger.debug("{} HTTP Server (Undertow) starting...", name);
        final long started = TimeUtils.started();
        this.gracefulShutdown.start();
        this.undertow = this.createServer();
        this.undertow.start();
        this.state.set(HttpServerState.RUN);
        var data = StructuredArgument.marker("port", this.port());
        logger.info(data, "{} HTTP Server (Undertow) started in {}", name, TimeUtils.tookForLogging(started));
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
            case INIT -> new ReadinessProbeFailure(name + " HTTP Server (Undertow) init");
            case RUN -> null;
            case SHUTDOWN -> new ReadinessProbeFailure(name + "HTTP Server (Undertow) shutdown");
        };
    }

    private enum HttpServerState {
        INIT, RUN, SHUTDOWN
    }
}
