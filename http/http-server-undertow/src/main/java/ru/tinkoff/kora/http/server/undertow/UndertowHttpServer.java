package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.handlers.GracefulShutdownHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Options;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class UndertowHttpServer implements HttpServer, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(UndertowHttpServer.class);

    private final AtomicReference<HttpServerState> state = new AtomicReference<>(HttpServerState.INIT);
    private final ValueOf<HttpServerConfig> config;
    private final GracefulShutdownHandler gracefulShutdown;
    private final XnioWorker xnioWorker;
    private final ByteBufferPool byteBufferPool;

    private volatile Undertow undertow;

    public UndertowHttpServer(ValueOf<HttpServerConfig> config,
                              ValueOf<UndertowPublicApiHandler> publicApiHandler,
                              @Nullable XnioWorker xnioWorker,
                              ByteBufferPool byteBufferPool) {
        this.config = config;
        this.xnioWorker = xnioWorker;
        this.byteBufferPool = byteBufferPool;
        this.gracefulShutdown = new GracefulShutdownHandler(exchange -> publicApiHandler.get().handleRequest(exchange));
    }

    @Override
    public void release() {
        logger.debug("Public HTTP Server (Undertow) stopping...");
        this.state.set(HttpServerState.SHUTDOWN);
        final long started = TimeUtils.started();
        this.gracefulShutdown.shutdown();
        final Duration shutdownAwait = this.config.get().shutdownWait();
        try {
            logger.debug("Public HTTP Server (Undertow) awaiting graceful shutdown in {} maximum...", TimeUtils.durationForLogging(shutdownAwait));
            if (!this.gracefulShutdown.awaitShutdown(shutdownAwait.toMillis())) {
                logger.warn("Public HTTP Server (Undertow) failed completing graceful shutdown in {}", shutdownAwait);
            }
        } catch (InterruptedException e) {
            logger.warn("Public HTTP Server (Undertow) failed completing graceful shutdown in {}", shutdownAwait, e);
        }

        if (this.undertow != null) {
            this.undertow.stop();
            this.undertow = null;
        }
        logger.info("Public HTTP Server (Undertow) stopped in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void init() {
        try {
            logger.debug("Public HTTP Server (Undertow) starting...");
            final long started = TimeUtils.started();
            this.gracefulShutdown.start();
            this.undertow = this.createServer();
            this.undertow.start();
            this.state.set(HttpServerState.RUN);
            var data = StructuredArgument.marker("port", this.port());
            logger.info(data, "Public HTTP Server (Undertow) started in {}", TimeUtils.tookForLogging(started));
        } catch (Exception e) {
            if (e.getCause() instanceof BindException be) {
                throw new RuntimeException("Public HTTP Server (Undertow) failed to start, cause port '%s' is already in use"
                    .formatted(config.get().publicApiHttpPort()), be);
            } else {
                throw new RuntimeException("Public HTTP Server (Undertow) failed to start on port '%s', due to: %s"
                    .formatted(config.get().publicApiHttpPort(), e.getMessage()), e);
            }
        }
    }

    private Undertow createServer() {
        var config = this.config.get();
        return Undertow.builder()
            .addHttpListener(config.publicApiHttpPort(), "0.0.0.0", this.gracefulShutdown)
            .setWorker(this.xnioWorker)
            .setByteBufferPool(this.byteBufferPool)
            .setServerOption(Options.READ_TIMEOUT, ((int) config.socketReadTimeout().toMillis()))
            .setServerOption(Options.WRITE_TIMEOUT, ((int) config.socketWriteTimeout().toMillis()))
            .setServerOption(Options.KEEP_ALIVE, config.socketKeepAliveEnabled())
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
