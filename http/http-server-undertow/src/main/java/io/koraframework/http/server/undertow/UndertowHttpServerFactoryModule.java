package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.*;
import io.koraframework.common.*;
import io.koraframework.common.annotation.*;
import io.koraframework.http.server.common.*;
import io.koraframework.http.server.common.router.*;
import io.koraframework.http.server.common.telemetry.*;
import io.koraframework.http.server.undertow.handler.*;
import io.undertow.*;
import io.undertow.server.*;
import org.jspecify.annotations.*;
import org.xnio.*;

public class UndertowHttpServerFactoryModule extends HttpServerFactoryModule {

    private final String name;

    public UndertowHttpServerFactoryModule(String name, String configPath) {
        super(configPath);
        this.name = name;
    }

    @Root
    @Tag(Tag.Factory.class)
    public UndertowHttpServer server(XnioWorker worker,
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
                               HttpServerTelemetryFactory telemetryFactory) {
        var telemetry = telemetryFactory.get(name, httpServerConfig.port(), httpServerConfig.telemetry());
        var handler = (HttpHandler) new KoraRequestProcessingHttpHandler(undertowConfig, httpServerConfig, httpServerRouter, telemetry);
        handler = new KoraVirtualThreadGlobalDispatchHttpHandler(name, handler);
//        handler = new KoraPlatformThreadGlobalDispatchHttpHandler(name, handler);
//        handler = new KoraVirtualThreadPerConnectionDispatchHttpHandler(name, handler);
        return handler;
    }
}
