package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.system.SystemApi;
import io.koraframework.http.server.common.system.SystemHttpServerConfig;
import io.koraframework.http.server.common.system.SystemHttpServerModule;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;

public interface UndertowSystemHttpServerModule extends SystemHttpServerModule {

    @FactoryModule
    @SystemApi
    default UndertowHttpServerFactoryModule undertowSystemApi(@SystemApi SystemHttpServerConfig systemHttpServerConfig) {
        return new UndertowHttpServerFactoryModule("kora-undertow-system", "httpServer.system") {
            @Override
            public HttpServerConfig config(Config config, ConfigValueMapper<HttpServerConfig> mapper) {
                return systemHttpServerConfig;
            }
        };
    }

    default UndertowConfig undertowHttpServerConfig(Config config, ConfigValueMapper<UndertowConfig> mapper) {
        return mapper.mapOrThrow(config.get("httpServer.undertow"));
    }

    @DefaultComponent
    default Wrapped<XnioWorker> xnioWorker(ValueOf<UndertowConfig> configValue, @Nullable Configurer<XnioWorker.Builder> configurer) {
        return new XnioLifecycle(configValue, configurer);
    }
}
