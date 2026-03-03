package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.common.util.Configurer;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerModule;
import ru.tinkoff.kora.http.server.common.PrivateHttpServerConfig;
import ru.tinkoff.kora.http.server.common.annotation.PrivateApi;
import ru.tinkoff.kora.http.server.common.router.HttpServerHandler;
import ru.tinkoff.kora.http.server.common.telemetry.NoopHttpServerTelemetry;

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
