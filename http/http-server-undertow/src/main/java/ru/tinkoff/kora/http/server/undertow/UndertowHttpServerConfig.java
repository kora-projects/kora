package ru.tinkoff.kora.http.server.undertow;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface UndertowHttpServerConfig {

    /**
     * @return Enables virtual threads for blocking request processing instead of the blockingThreads pool, requires Java 21+.
     */
    default boolean virtualThreadsEnabled() {
        return false;
    }
}
