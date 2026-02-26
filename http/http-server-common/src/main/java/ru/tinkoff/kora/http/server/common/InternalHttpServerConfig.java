package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface InternalHttpServerConfig extends HttpServerConfig {

    @Override
    default int port() {
        return 8090;
    }
}
