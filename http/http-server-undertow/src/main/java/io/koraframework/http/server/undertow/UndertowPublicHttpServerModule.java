package io.koraframework.http.server.undertow;

import io.koraframework.common.annotation.FactoryModule;

public interface UndertowPublicHttpServerModule extends UndertowSystemHttpServerModule {
    @FactoryModule
    default UndertowHttpServerModule publicApi() {
        return new UndertowHttpServerModule("kora-undertow", "httpServer");
    }
}
