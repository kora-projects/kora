package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTelemetry;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTelemetryFactory;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.concurrent.atomic.AtomicReference;

final class UndertowCamundaHttpServer implements Lifecycle, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(UndertowCamundaHttpServer.class);

    private final AtomicReference<HttpServerState> state = new AtomicReference<>(HttpServerState.INIT);
    private final String name;
    private final ValueOf<CamundaRestConfig> config;
    private final CamundaRestTelemetry telemetry;
    private final GracefulShutdownHandler gracefulShutdown;

    private volatile Undertow undertow;

    public UndertowCamundaHttpServer(ValueOf<CamundaRestConfig> config,
                                     ValueOf<UndertowCamundaRestHttpHandler> publicApiHandler,
                                     CamundaRestTelemetryFactory telemetryFactory) {
        this.name = "Camunda";
        this.config = config;
        this.telemetry = telemetryFactory.get(config.get().telemetry());
        this.gracefulShutdown = new GracefulShutdownHandler(ex -> {
            var telemetryContext = telemetry.get(ex.getRequestMethod().toString(), ex.getRelativePath(), ex.getRequestHeaders());

            ex.addExchangeCompleteListener((ext, listener) -> {
                telemetryContext.close(ext.getStatusCode(), null);
                listener.proceed();
            });

            try {
                publicApiHandler.get().handleRequest(ex);
            } catch (Exception e) {
                telemetryContext.close(ex.getStatusCode(), e);
                throw e;
            }
        });
    }

    @Override
    public void init() {
        if (this.config.get().enabled()) {
            logger.debug("{} HTTP Server (Undertow) starting...", name);
            final long started = TimeUtils.started();
            this.gracefulShutdown.start();
            this.undertow = Undertow.builder()
                .addHttpListener(this.config.get().port(), "0.0.0.0", this.gracefulShutdown)
                .build();

            this.undertow.start();
            this.state.set(HttpServerState.RUN);
            logger.info("{} HTTP Server (Undertow) started in {}", name, TimeUtils.tookForLogging(started));
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
            logger.debug("{} HTTP Server (Undertow) stopping...", name);
            final long started = TimeUtils.started();
            this.gracefulShutdown.shutdown();
            try {
                logger.debug("{} HTTP Server (Undertow) awaiting graceful shutdown...", name);
                this.gracefulShutdown.awaitShutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            this.undertow.stop();
            this.undertow = null;
            logger.info("{} HTTP Server (Undertow) stopped in {}", name, TimeUtils.tookForLogging(started));
        }
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
