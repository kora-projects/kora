package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.server.HttpHandler;
import jakarta.ws.rs.core.Application;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.camunda.rest.CamundaRest;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.camunda.rest.CamundaRestModule;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;

public interface CamundaRestUndertowModule extends CamundaRestModule {

    @Tag(CamundaRest.class)
    default HttpHandler camundaRestHttpHandler(@Tag(CamundaRest.class) All<Application> applications,
                                               CamundaRestConfig camundaRestConfig) {
        return new UndertowCamundaRestHttpHandler(applications, camundaRestConfig);
    }

    @Tag(CamundaRest.class)
    @Root
    default HttpServer camundaRestHttpServer(@Tag(CamundaRest.class) ValueOf<HttpHandler> camundaHttpHandler,
                                             ValueOf<CamundaRestConfig> camundaRestConfig,
                                             ValueOf<HttpServerConfig> httpServerConfig) {
        return new UndertowCamundaRestHttpServer(camundaHttpHandler, camundaRestConfig, httpServerConfig);
    }
}
