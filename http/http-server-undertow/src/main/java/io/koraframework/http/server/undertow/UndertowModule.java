package io.koraframework.http.server.undertow;

import io.undertow.Undertow;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.util.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractionException;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.server.common.HttpServerModule;
import io.koraframework.http.server.common.PrivateHttpServerConfig;
import io.koraframework.http.server.common.annotation.PrivateApi;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.NoopHttpServerTelemetry;

public interface UndertowModule extends HttpServerModule {
    @Root
    @PrivateApi
    default UndertowHttpServer undertowHttpServer(@PrivateApi ValueOf<PrivateHttpServerConfig> config,
                                                  @PrivateApi ValueOf<HttpServerHandler> handler,
                                                  XnioWorker worker,
                                                  @Nullable @PrivateApi Configurer<Undertow.Builder> configurer) {
        return new UndertowHttpServer(config, handler, "kora-undertow-private", NoopHttpServerTelemetry.INSTANCE, worker, configurer);
    }

    default Wrapped<XnioWorker> xnioWorker(ValueOf<UndertowConfig> configValue) {
        return new XnioLifecycle(configValue);
    }

    default UndertowConfig undertowHttpServerConfig(Config config, ConfigValueExtractor<UndertowConfig> extractor) {
        var value = config.get("undertow");
        var parsed = extractor.extract(value);
        if (parsed == null) {
            throw ConfigValueExtractionException.missingValueAfterParse(value);
        }
        return parsed;
    }
}
