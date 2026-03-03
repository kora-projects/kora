package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.common.util.Configurer;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.InternalHttpServerConfig;
import ru.tinkoff.kora.http.server.common.NoopHttpServer;
import ru.tinkoff.kora.http.server.common.annotation.InternalApi;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.router.HttpServerHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryFactory;

public interface UndertowHttpServerModule extends UndertowModule {
    @Root
    default UndertowHttpServer undertowHttpServer(ValueOf<HttpServerConfig> config,
                                                  ValueOf<HttpServerHandler> handler,
                                                  HttpServerTelemetryFactory telemetryFactory,
                                                  XnioWorker worker,
                                                  @Nullable Configurer<Undertow.Builder> configurer) {
        var telemetry = telemetryFactory.get(config.get().telemetry());
        return new UndertowHttpServer(config, handler, "kora-undertow", telemetry, worker, configurer);
    }

    @Root
    @InternalApi
    default HttpServer internalApiUndertowHttpServer(@Tag(InternalApi.class) All<HttpServerRequestHandler> handlers,
                                                     @InternalApi ValueOf<InternalHttpServerConfig> config,
                                                     @InternalApi ValueOf<HttpServerHandler> handler,
                                                     HttpServerTelemetryFactory telemetryFactory,
                                                     XnioWorker worker,
                                                     @Nullable @InternalApi Configurer<Undertow.Builder> configurer) {
        if (handlers.isEmpty()) {
            return NoopHttpServer.INSTANCE;
        }
        var telemetry = telemetryFactory.get(config.get().telemetry());
        return new UndertowHttpServer(config, handler, "kora-undertow-internal", telemetry, worker, configurer);
    }
}
