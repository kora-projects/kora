package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.annotation.Tag;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.HttpServerFactoryModule;
import io.koraframework.http.server.common.admission.HttpServerAdmission;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.koraframework.http.server.undertow.handler.KoraRequestProcessingHttpHandler;
import io.koraframework.http.server.undertow.handler.KoraVirtualThreadPerConnectionDispatchHttpHandler;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;

public class UndertowHttpServerFactoryModule extends HttpServerFactoryModule {

    private final String name;

    public UndertowHttpServerFactoryModule(String name, String configPath) {
        super(configPath);
        this.name = name;
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public XnioWorker xnioWorker(XnioWorker worker) {
        return worker;
    }

    @Root
    @Tag(Tag.Factory.class)
    public UndertowHttpServer server(@Tag(Tag.Factory.class) XnioWorker worker,
                                     ValueOf<UndertowConfig> undertowConfig,
                                     @Tag(Tag.Factory.class) ValueOf<HttpHandler> httpHandler,
                                     @Tag(Tag.Factory.class) ValueOf<HttpServerConfig> httpServerConfig,
                                     @Tag(Tag.Factory.class) @Nullable Configurer<Undertow.Builder> configurer) {
        return new UndertowHttpServer(this.name, undertowConfig, httpHandler, worker, httpServerConfig, configurer);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public HttpHandler handler(ValueOf<UndertowConfig> undertowConfig,
                               @Tag(Tag.Factory.class) HttpServerConfig httpServerConfig,
                               @Tag(Tag.Factory.class) HttpServerRouter httpServerRouter,
                               HttpServerTelemetryFactory telemetryFactory,
                               @Tag(Tag.Factory.class) @Nullable HttpServerAdmission admission) {
        var telemetry = telemetryFactory.get(this.name, httpServerConfig.port(), httpServerConfig.telemetry());
        var handler = (HttpHandler) new KoraRequestProcessingHttpHandler(undertowConfig, httpServerConfig, httpServerRouter, telemetry, admission);
        handler = new KoraVirtualThreadPerConnectionDispatchHttpHandler(this.name, handler);
        return handler;
    }
}
