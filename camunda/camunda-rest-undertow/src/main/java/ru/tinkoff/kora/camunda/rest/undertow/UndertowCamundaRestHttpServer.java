package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.server.HttpHandler;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.camunda.rest.CamundaRestHttpServer;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServer;

final class UndertowCamundaRestHttpServer implements CamundaRestHttpServer {

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
    }

    @Override
    public void release() {
        if (undertowHttpServer != null) {
            undertowHttpServer.release();
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
