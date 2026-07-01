package io.koraframework.camunda.rest.undertow;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.camunda.rest.CamundaRest;
import io.koraframework.camunda.rest.CamundaRestConfig;
import io.koraframework.camunda.rest.CamundaRestModule;
import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetryFactory;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.common.annotation.Root;
import io.undertow.server.HttpHandler;
import jakarta.ws.rs.core.Application;

/**
 * Use module: `io.koraframework:experimental.operaton.rest.undertow` as a replacement for deprecated Camunda 7 engine
 * <a href="https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/">Camunda 7 EOL</a>
 * <a href="https://operaton.org/">Operaton BPMN Engine</a>
 */
@Deprecated
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
