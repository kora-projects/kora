package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.util.Configurer;
import io.koraframework.http.server.common.HttpServerModule;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.system.HttpServerSystemConfig;
import io.koraframework.http.server.common.system.SystemApi;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerTelemetry;
import io.koraframework.http.server.undertow.handler.KoraRequestHttpHandler;
import io.koraframework.http.server.undertow.handler.KoraVirtualThreadHttpHandler;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;

public interface UndertowSystemHttpServerModule extends HttpServerModule {

    @Root
    @SystemApi
    default UndertowHttpServer undertowSystemHttpServer(@SystemApi ValueOf<HttpHandler> httpHandler,
                                                        XnioWorker worker,
                                                        @SystemApi ValueOf<HttpServerSystemConfig> config,
                                                        @SystemApi @Nullable Configurer<Undertow.Builder> configurer,
                                                        @SystemApi @Nullable Configurer<HttpHandler> handlerConfigurer) {
        return new UndertowHttpServer("kora-undertow-system", httpHandler, worker, config, configurer, handlerConfigurer);
    }

    @SystemApi
    @DefaultComponent
    default HttpHandler undertowSystemHttpHandler(@SystemApi HttpServerHandler systemApiHandler) {
        var handler = (HttpHandler) new KoraRequestHttpHandler(NoopHttpServerTelemetry.INSTANCE, systemApiHandler);
        handler = new KoraVirtualThreadHttpHandler("kora-undertow-system", handler);
        return handler;
    }

    @DefaultComponent
    default Wrapped<XnioWorker> xnioWorker(ValueOf<UndertowConfig> configValue, @Nullable Configurer<XnioWorker.Builder> configurer) {
        return new XnioLifecycle(configValue, configurer);
    }
}
