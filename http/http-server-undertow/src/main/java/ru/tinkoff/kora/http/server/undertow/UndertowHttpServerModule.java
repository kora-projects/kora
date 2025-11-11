package ru.tinkoff.kora.http.server.undertow;

import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryFactory;

public interface UndertowHttpServerModule extends UndertowModule {
    @Root
    default UndertowHttpServer undertowHttpServer(ValueOf<HttpServerConfig> config,
                                                  ValueOf<PublicApiHandler> handler,
                                                  HttpServerTelemetryFactory telemetryFactory,
                                                  XnioWorker worker) {
        var telemetry = telemetryFactory.get(config.get().telemetry());
        return new UndertowHttpServer(config, handler, "kora-undertow", telemetry, worker);
    }

    default UndertowHttpServerConfig undertowHttpServerConfig(Config config, ConfigValueExtractor<UndertowHttpServerConfig> extractor) {
        return extractor.extract(config.get("httpServer"));
    }
}
