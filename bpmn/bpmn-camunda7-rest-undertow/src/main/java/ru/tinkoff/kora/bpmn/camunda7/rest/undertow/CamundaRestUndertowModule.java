package ru.tinkoff.kora.bpmn.camunda7.rest.undertow;

import io.undertow.server.HttpHandler;
import jakarta.ws.rs.core.Application;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaRest;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaRestConfig;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaRestHttpServer;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaRestModule;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

import java.util.Objects;

public interface CamundaRestUndertowModule extends CamundaRestModule {

    @Tag(CamundaRest.class)
    default HttpHandler camundaRestHttpHandler(@Tag(CamundaRest.class) Application application,
                                               CamundaRestConfig camundaRestConfig) {
        return new CamundaRestUndertowHttpHandler(application, camundaRestConfig);
    }

    @Tag(PublicApiHandler.class)
    default GraphInterceptor<HttpHandler> camundaRestPublicHttpServerHandlerInterceptor(@Tag(CamundaRest.class) HttpHandler camundaHttpHandler,
                                                                                        CamundaRestConfig camundaRestConfig,
                                                                                        HttpServerConfig httpServerConfig) {
        return new GraphInterceptor<>() {

            @Override
            public HttpHandler init(HttpHandler value) {
                // replace public HttpHandler only if camunda port same as public server port
                if (camundaRestConfig.port() == null || Objects.equals(httpServerConfig.publicApiHttpPort(), camundaRestConfig.port())) {
                    return exchange -> {
                        final String path = exchange.getRelativePath();
                        if (path.startsWith(camundaRestConfig.path())) {
                            camundaHttpHandler.handleRequest(exchange);
                        } else {
                            value.handleRequest(exchange);
                        }
                    };
                } else {
                    return value;
                }
            }

            @Override
            public HttpHandler release(HttpHandler value) {
                return value;
            }
        };
    }

    @Root
    default CamundaRestHttpServer camundaRestHttpServer(@Tag(CamundaRest.class) ValueOf<HttpHandler> camundaHttpHandler,
                                                        ValueOf<CamundaRestConfig> camundaRestConfig,
                                                        ValueOf<HttpServerConfig> httpServerConfig) {
        if (camundaRestConfig.get().port() == null || Objects.equals(httpServerConfig.get().publicApiHttpPort(), camundaRestConfig.get().port())) {
            // camunda rest is started on public http server
            return new FakeCamundaRestHttpServer(httpServerConfig.get().publicApiHttpPort());
        } else {
            return new UndertowCamundaRestHttpServer(camundaHttpHandler, camundaRestConfig, httpServerConfig);
        }
    }
}
