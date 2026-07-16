package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.annotation.Tag;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.HttpServerFactoryModule;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.koraframework.http.server.undertow.handler.KoraRequestProcessingHttpHandler;
import io.koraframework.http.server.undertow.handler.KoraVirtualThreadDispatchHttpHandler;
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

    @Root
    @Tag(Tag.Factory.class)
    public UndertowHttpServer server(XnioWorker worker,
                                     @Tag(Tag.Factory.class) ValueOf<HttpHandler> httpHandler,
                                     @Tag(Tag.Factory.class) ValueOf<HttpServerConfig> config,
                                     @Tag(Tag.Factory.class) @Nullable Configurer<Undertow.Builder> configurer,
                                     @Tag(Tag.Factory.class) @Nullable Configurer<HttpHandler> handlerConfigurer) {
        return new UndertowHttpServer(this.name, httpHandler, worker, config, configurer, handlerConfigurer);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public HttpHandler handler(@Tag(Tag.Factory.class) HttpServerConfig config,
                               @Tag(Tag.Factory.class) HttpServerHandler publicApiHandler,
                               HttpServerTelemetryFactory telemetryFactory) {
        var telemetry = telemetryFactory.get(config.telemetry());
        var handler = (HttpHandler) new KoraRequestProcessingHttpHandler(telemetry, publicApiHandler);
        handler = new KoraVirtualThreadDispatchHttpHandler(this.name, handler);
        return handler;
    }
}
