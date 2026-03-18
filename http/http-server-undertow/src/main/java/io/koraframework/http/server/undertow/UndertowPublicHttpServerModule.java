package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.util.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractionException;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;

public interface UndertowPublicHttpServerModule extends UndertowSystemHttpServerModule {

    @Root
    default UndertowHttpServer undertowPublicHttpServer(HttpHandler httpHandler,
                                                        XnioWorker worker,
                                                        ValueOf<HttpServerConfig> config,
                                                        @Nullable Configurer<Undertow.Builder> configurer) {
        return new UndertowHttpServer("kora-undertow", httpHandler, worker, config, configurer);
    }

    @DefaultComponent
    default HttpHandler undertowPublicHttpHandler(ValueOf<HttpServerConfig> config,
                                                  ValueOf<HttpServerHandler> publicApiHandler,
                                                  HttpServerTelemetryFactory telemetryFactory) {
        var telemetry = telemetryFactory.get(config.get().telemetry());
        return new UndertowHttpHandler("kora-undertow", publicApiHandler, telemetry);
    }

    @DefaultComponent
    default Wrapped<XnioWorker> xnioWorker(ValueOf<UndertowConfig> configValue,
                                           @Nullable Configurer<XnioWorker.Builder> configurer) {
        return new XnioLifecycle(configValue, configurer);
    }

    default UndertowConfig undertowHttpServerConfig(Config config, ConfigValueExtractor<UndertowConfig> extractor) {
        var value = config.get("httpServer.undertow");
        var parsed = extractor.extract(value);
        if (parsed == null) {
            throw ConfigValueExtractionException.missingValueAfterParse(value);
        }
        return parsed;
    }
}
