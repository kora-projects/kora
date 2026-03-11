package io.koraframework.http.server.undertow;

import io.undertow.Undertow;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.util.Configurer;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;

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
}
