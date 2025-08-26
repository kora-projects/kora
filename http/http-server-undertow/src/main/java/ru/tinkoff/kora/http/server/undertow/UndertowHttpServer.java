package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.util.AttachmentKey;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Options;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class UndertowHttpServer implements HttpServer, ReadinessProbe, HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(UndertowHttpServer.class);

    private final AtomicReference<HttpServerState> state = new AtomicReference<>(HttpServerState.INIT);
    private final AttachmentKey<ExecutorService> executorServiceAttachmentKey = AttachmentKey.create(ExecutorService.class);
    private final ValueOf<HttpServerConfig> config;
    private final GracefulShutdownHandler gracefulShutdown;
    private final String name;
    @Nullable
    private final HttpServerTracer tracer;
    private final XnioWorker xnioWorker;
    private final ValueOf<PublicApiHandler> publicApiHandler;

    private volatile Undertow undertow;

    public UndertowHttpServer(ValueOf<HttpServerConfig> config,
                              ValueOf<PublicApiHandler> publicApiHandler,
                              String name,
                              @Nullable HttpServerTracer tracer,
                              @Nullable XnioWorker xnioWorker) {
        this.config = config;
        this.name = name;
        this.tracer = tracer;
        this.xnioWorker = xnioWorker;
        this.publicApiHandler = publicApiHandler;
        this.gracefulShutdown = new GracefulShutdownHandler(this);
    }

    @Override
    public void release() {
        logger.debug("Public HTTP Server (Undertow) stopping...");
        this.state.set(HttpServerState.SHUTDOWN);
        final long started = TimeUtils.started();
        this.gracefulShutdown.shutdown();
        final Duration shutdownAwait = this.config.get().shutdownWait();
        try {
            logger.debug("Public HTTP Server (Undertow) awaiting graceful shutdown...");
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
            .setServerOption(Options.READ_TIMEOUT, ((int) config.socketReadTimeout().toMillis()))
            .setServerOption(Options.WRITE_TIMEOUT, ((int) config.socketWriteTimeout().toMillis()))
            .setServerOption(Options.KEEP_ALIVE, config.socketKeepAliveEnabled())
            .build();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var context = Context.clear();
        var exchangeProcessor = new UndertowExchangeProcessor(this.publicApiHandler.get(), context, this.tracer);
        var executor = getOrCreateExecutor(exchange, executorServiceAttachmentKey, this.name);
        exchange.dispatch(executor, exchangeProcessor);
    }

    public static ExecutorService getOrCreateExecutor(HttpServerExchange exchange, AttachmentKey<ExecutorService> key, String serverName) {
        var connection = exchange.getConnection();
        var existingExecutor = connection.getAttachment(key);
        if (existingExecutor != null) {
            return existingExecutor;
        }
        var threadName = serverName + "-" + connection.getId();
        var executor = Executors.newSingleThreadExecutor(r -> Thread.ofVirtual().name(threadName).unstarted(r));
        connection.addCloseListener(c -> {
            var e = c.removeAttachment(key);
            if (e != null) {
                e.shutdownNow();
            }
        });
        connection.putAttachment(key, executor);
        return executor;
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
