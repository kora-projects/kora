package io.koraframework.camunda.rest.undertow;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.camunda.rest.CamundaRest;
import io.koraframework.camunda.rest.CamundaRestConfig;
import io.koraframework.camunda.rest.CamundaRestModule;
import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetryFactory;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.common.annotation.Root;
import io.undertow.server.HttpHandler;
import jakarta.ws.rs.core.Application;

public interface CamundaRestUndertowModule extends CamundaRestModule {

    @Tag(CamundaRest.class)
    @DefaultComponent
    default Wrapped<HttpHandler> camundaRestUndertowHttpHandler(@Tag(CamundaRest.class) All<Application> applications,
                                                                CamundaRestConfig camundaRestConfig,
                                                                CamundaRestTelemetryFactory telemetryFactory) {
        var telemetry = telemetryFactory.get(camundaRestConfig.telemetry());
        return new UndertowCamundaRestHttpHandler(applications, camundaRestConfig, telemetry);
    }

    @Tag(CamundaRest.class)
    @Root
    default Lifecycle camundaRestUndertowHttpServer(@Tag(CamundaRest.class) ValueOf<HttpHandler> camundaHttpHandler,
                                                    ValueOf<CamundaRestConfig> camundaRestConfig) {
        return new UndertowCamundaHttpServer(camundaRestConfig, camundaHttpHandler);
    }
}
