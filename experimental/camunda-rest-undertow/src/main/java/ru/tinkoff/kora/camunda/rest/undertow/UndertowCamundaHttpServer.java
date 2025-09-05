package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.net.BindException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

final class UndertowCamundaHttpServer implements Lifecycle, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(UndertowCamundaHttpServer.class);

    private final AtomicReference<HttpServerState> state = new AtomicReference<>(HttpServerState.INIT);
    private final ValueOf<CamundaRestConfig> config;
    private final GracefulShutdownHandler gracefulShutdown;

    private volatile Undertow undertow;

    UndertowCamundaHttpServer(ValueOf<CamundaRestConfig> config, ValueOf<HttpHandler> publicApiHandler) {
        this.config = config;

        this.gracefulShutdown = new GracefulShutdownHandler(exch -> {
            try {
                publicApiHandler.get().handleRequest(exch);
            } catch (Exception e) {
                exch.setStatusCode(500);
                exch.endExchange();
                e.printStackTrace();
            }
        });
    }

    @Override
    public void init() {
        if (this.config.get().enabled()) {
            try {
                logger.debug("Camunda HTTP Server (Undertow) starting...");
                final long started = TimeUtils.started();
                this.gracefulShutdown.start();
                this.undertow = Undertow.builder()
                    .addHttpListener(this.config.get().port(), "0.0.0.0", this.gracefulShutdown)
                    .build();

                this.undertow.start();
                this.state.set(HttpServerState.RUN);
                logger.info("Camunda HTTP Server (Undertow) started in {}", TimeUtils.tookForLogging(started));
            } catch (Exception e) {
                if (e.getCause() instanceof BindException be) {
                    throw new RuntimeException("Camunda HTTP Server (Undertow) failed to start, cause port '%s' is already in use"
                        .formatted(config.get().port()), be);
                } else {
                    throw new RuntimeException("Camunda HTTP Server (Undertow) failed to start on port '%s', due to: %s"
                        .formatted(config.get().port(), e.getMessage()), e);
                }
            }
        }
    }

    @Override
    public void release() {
        if (this.undertow != null) {
            this.state.set(HttpServerState.SHUTDOWN);
            final long started = TimeUtils.started();
            this.gracefulShutdown.shutdown();
            final Duration shutdownAwait = this.config.get().shutdownWait();
            try {
                logger.debug("Camunda HTTP Server (Undertow) awaiting graceful shutdown...");
                if (!this.gracefulShutdown.awaitShutdown(shutdownAwait.toMillis())) {
                    logger.warn("Camunda HTTP Server (Undertow) failed completing graceful shutdown in {}", shutdownAwait);
                }
            } catch (InterruptedException e) {
                logger.warn("Camunda HTTP Server (Undertow) failed completing graceful shutdown in {}", shutdownAwait, e);
            }

            this.undertow.stop();
            this.undertow = null;
            logger.info("Camunda HTTP Server (Undertow) stopped in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public ReadinessProbeFailure probe() {
        return switch (this.state.get()) {
            case INIT -> new ReadinessProbeFailure("Camunda HTTP Server (Undertow) init");
            case RUN -> null;
            case SHUTDOWN -> new ReadinessProbeFailure("CamundaHTTP Server (Undertow) shutdown");
        };
    }

    private enum HttpServerState {
        INIT, RUN, SHUTDOWN
    }
}
