package io.koraframework.http.server.undertow;

import io.koraframework.common.annotation.FactoryModule;

public interface UndertowPublicHttpServerModule extends UndertowSystemHttpServerModule {

    @FactoryModule
    default UndertowHttpServerFactoryModule undertowPublicApi() {
        return new UndertowHttpServerFactoryModule("kora-undertow", "httpServer");
    }
}
