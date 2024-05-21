package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.server.HttpHandler;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.Application;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.camunda.rest.CamundaRest;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.camunda.rest.CamundaRestModule;
import ru.tinkoff.kora.camunda.rest.telemetry.*;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;

public interface CamundaRestUndertowModule extends CamundaRestModule {

    @DefaultComponent
    default CamundaRestLoggerFactory defaultCamundaRestLoggerFactory() {
        return new DefaultCamundaRestLoggerFactory();
    }

    @DefaultComponent
    default CamundaRestTelemetryFactory defaultCamundaRestTelemetryFactory(@Nullable CamundaRestLoggerFactory logger,
                                                                           @Nullable CamundaRestMetricsFactory metrics,
                                                                           @Nullable CamundaRestTracerFactory tracer) {
        return new DefaultCamundaRestTelemetryFactory(logger, metrics, tracer);
    }

    @Tag(CamundaRest.class)
    default HttpHandler camundaRestUndertowHttpHandler(@Tag(CamundaRest.class) All<Application> applications,
                                                       CamundaRestConfig camundaRestConfig) {
        return new UndertowCamundaRestHttpHandler(applications, camundaRestConfig);
    }

    @Tag(CamundaRest.class)
    @Root
    default Lifecycle camundaRestUndertowHttpServer(@Tag(CamundaRest.class) ValueOf<HttpHandler> camundaHttpHandler,
                                                    ValueOf<CamundaRestConfig> camundaRestConfig,
                                                    CamundaRestTelemetryFactory telemetryFactory) {
        return new UndertowCamundaHttpServer(camundaRestConfig, camundaHttpHandler, telemetryFactory);
    }
}
