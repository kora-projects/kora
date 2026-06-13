package io.koraframework.bpmn.operaton.rest.undertow;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.bpmn.operaton.rest.OperatonRest;
import io.koraframework.bpmn.operaton.rest.OperatonRestConfig;
import io.koraframework.bpmn.operaton.rest.OperatonRestModule;
import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestTelemetryFactory;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.common.annotation.Root;
import io.undertow.server.HttpHandler;
import jakarta.ws.rs.core.Application;

public interface OperatonRestUndertowModule extends OperatonRestModule {

    @Tag(OperatonRest.class)
    @DefaultComponent
    default Wrapped<HttpHandler> operatonRestUndertowHttpHandler(@Tag(OperatonRest.class) All<Application> applications,
                                                                OperatonRestConfig restConfig,
                                                                OperatonRestTelemetryFactory telemetryFactory) {
        var telemetry = telemetryFactory.get(restConfig.telemetry());
        return new UndertowOperatonRestHttpHandler(applications, restConfig, telemetry);
    }

    @Tag(OperatonRest.class)
    @Root
    default Lifecycle operatonRestUndertowHttpServer(@Tag(OperatonRest.class) ValueOf<HttpHandler> operatonHttpHandler,
                                                    ValueOf<OperatonRestConfig> restConfig) {
        return new UndertowOperatonHttpServer(restConfig, operatonHttpHandler);
    }
}
