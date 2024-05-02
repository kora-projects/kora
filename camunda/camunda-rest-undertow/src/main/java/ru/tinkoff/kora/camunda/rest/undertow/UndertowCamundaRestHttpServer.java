package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.server.HttpHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServer;

import java.time.Duration;

final class UndertowCamundaRestHttpServer implements HttpServer, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(UndertowCamundaRestHttpServer.class);

    private final ValueOf<HttpHandler> camundaHttpHandler;
    private final ValueOf<CamundaRestConfig> camundaRestConfig;
    private final ValueOf<HttpServerConfig> httpServerConfig;

    private volatile UndertowHttpServer undertowHttpServer;

    public UndertowCamundaRestHttpServer(ValueOf<HttpHandler> camundaHttpHandler,
                                         ValueOf<CamundaRestConfig> camundaRestConfig,
                                         ValueOf<HttpServerConfig> httpServerConfig) {
        this.camundaHttpHandler = camundaHttpHandler;
        this.camundaRestConfig = camundaRestConfig;
        this.httpServerConfig = httpServerConfig;
    }

    @Override
    public void init() {
        logger.debug("Camunda Rest starting...");
        final long started = System.nanoTime();

        var camundaHttpServerConfig = new ValueOf<HttpServerConfig>() {
            @Override
            public HttpServerConfig get() {
                return new HttpServerConfig() {

                    @SuppressWarnings("DataFlowIssue")
                    @Override
                    public int publicApiHttpPort() {
                        return camundaRestConfig.get().port();
                    }

                    @Override
                    public HttpServerTelemetryConfig telemetry() {
                        return httpServerConfig.get().telemetry();
                    }
                };
            }

            @Override
            public void refresh() {
                camundaRestConfig.refresh();
            }
        };

        this.undertowHttpServer = new UndertowHttpServer("CamundaRest", camundaHttpServerConfig, camundaHttpHandler, null);
        this.undertowHttpServer.init();

        logger.info("Camunda Rest started in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public void release() {
        if (undertowHttpServer != null) {
            logger.debug("Camunda Rest stopping...");
            final long started = System.nanoTime();

            undertowHttpServer.release();

            logger.info("Camunda Rest stopped in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        }
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() {
        return undertowHttpServer.probe();
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public int port() {
        return camundaRestConfig.get().port();
    }
}
