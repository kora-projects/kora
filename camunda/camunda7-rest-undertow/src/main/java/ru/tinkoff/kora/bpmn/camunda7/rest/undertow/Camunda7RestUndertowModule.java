package ru.tinkoff.kora.bpmn.camunda7.rest.undertow;

import io.undertow.server.HttpHandler;
import jakarta.ws.rs.core.Application;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.bpmn.camunda7.rest.Camunda7Rest;
import ru.tinkoff.kora.bpmn.camunda7.rest.Camunda7RestConfig;
import ru.tinkoff.kora.bpmn.camunda7.rest.Camunda7RestHttpServer;
import ru.tinkoff.kora.bpmn.camunda7.rest.Camunda7RestModule;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

import java.util.Objects;

public interface Camunda7RestUndertowModule extends Camunda7RestModule {

    @Tag(Camunda7Rest.class)
    default HttpHandler camundaRestHttpHandler(@Tag(Camunda7Rest.class) Application application,
                                               Camunda7RestConfig camundaRestConfig) {
        return new UndertowCamunda7RestHttpHandler(application, camundaRestConfig);
    }

    @Tag(PublicApiHandler.class)
    default GraphInterceptor<HttpHandler> camundaRestPublicHttpServerHandlerInterceptor(@Tag(Camunda7Rest.class) HttpHandler camundaHttpHandler,
                                                                                        Camunda7RestConfig camundaRestConfig,
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
    default Camunda7RestHttpServer camundaRestHttpServer(@Tag(Camunda7Rest.class) ValueOf<HttpHandler> camundaHttpHandler,
                                                         ValueOf<Camunda7RestConfig> camundaRestConfig,
                                                         ValueOf<HttpServerConfig> httpServerConfig) {
        if (camundaRestConfig.get().port() == null || Objects.equals(httpServerConfig.get().publicApiHttpPort(), camundaRestConfig.get().port())) {
            // camunda rest is started on public http server
            return new FakeCamunda7RestHttpServer(httpServerConfig.get().publicApiHttpPort());
        } else {
            return new UndertowCamunda7RestHttpServer(camundaHttpHandler, camundaRestConfig, httpServerConfig);
        }
    }
}
