package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.util.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractionException;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.server.common.HttpServerModule;
import io.koraframework.http.server.common.system.HttpServerSystemConfig;
import io.koraframework.http.server.common.system.SystemApi;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.NoopHttpServerTelemetry;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;

public interface UndertowSystemHttpServerModule extends HttpServerModule {

    @Root
    @SystemApi
    default UndertowHttpServer undertowSystemHttpServer(@SystemApi HttpHandler httpHandler,
                                                         XnioWorker worker,
                                                         @SystemApi ValueOf<HttpServerSystemConfig> config,
                                                         @SystemApi @Nullable Configurer<Undertow.Builder> configurer) {
        return new UndertowHttpServer("kora-undertow-system", httpHandler, worker, config, configurer);
    }

    @SystemApi
    @DefaultComponent
    default HttpHandler undertowSystemHttpHandler(@SystemApi ValueOf<HttpServerHandler> handler) {
        return new UndertowHttpHandler("kora-undertow-system", handler, NoopHttpServerTelemetry.INSTANCE);
    }

    @SystemApi
    @DefaultComponent
    default Wrapped<XnioWorker> undertowSystemHttpServerXnioWorker(@SystemApi @Nullable Configurer<XnioWorker.Builder> configurer) {
        return new XnioLifecycle(new ValueOf<>() {
            @Override
            public UndertowConfig get() {
                return new UndertowConfig() {
                    @Override
                    public int ioThreads() {return 1;}
                };
            }

            @Override
            public void refresh() {}
        }, configurer);
    }
}
