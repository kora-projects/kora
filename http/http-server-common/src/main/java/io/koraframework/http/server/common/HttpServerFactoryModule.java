package io.koraframework.http.server.common;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.server.common.interceptor.HttpServerInterceptor;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.router.HttpServerHandler;

public class HttpServerFactoryModule {

    private final String configPath;

    public HttpServerFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public HttpServerConfig config(Config config, ConfigValueMapper<HttpServerConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public HttpServerHandler handler(@Tag(Tag.Factory.class) All<HttpServerRequestHandler> handlers,
                                     @Tag(Tag.Factory.class) All<HttpServerInterceptor> interceptors,
                                     @Tag(Tag.Factory.class) HttpServerConfig config) {
        return new HttpServerHandler(handlers, interceptors, config);
    }
}
