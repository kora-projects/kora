package ru.tinkoff.kora.http.server.undertow;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface UndertowHttpServerConfig {
    default boolean virtualThreadsEnabled() {
        return false;
    }
}
