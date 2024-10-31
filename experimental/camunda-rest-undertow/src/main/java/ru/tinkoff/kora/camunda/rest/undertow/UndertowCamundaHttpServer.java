package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTelemetryFactory;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;

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
            }
        });
    }

    @Override
    public void init() {
        if (this.config.get().enabled()) {
            logger.debug("Camunda HTTP Server (Undertow) starting...");
            final long started = TimeUtils.started();
            this.gracefulShutdown.start();
            this.undertow = Undertow.builder()
                .addHttpListener(this.config.get().port(), "0.0.0.0", this.gracefulShutdown)
                .build();

            this.undertow.start();
            this.state.set(HttpServerState.RUN);
            logger.info("Camunda HTTP Server (Undertow) started in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public void release() {
        if (this.undertow != null) {
            this.state.set(HttpServerState.SHUTDOWN);
            try {
                Thread.sleep(this.config.get().shutdownWait().toMillis());
            } catch (InterruptedException e) {
                // ignore
            }
            logger.debug("Camunda HTTP Server (Undertow) stopping...");
            final long started = TimeUtils.started();
            this.gracefulShutdown.shutdown();
            try {
                logger.debug("Camunda HTTP Server (Undertow) awaiting graceful shutdown...");
                this.gracefulShutdown.awaitShutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
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
