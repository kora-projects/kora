package ru.tinkoff.kora.http.client.async;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface AsyncHttpClientConfig {

    /**
     * @return Whether to follow HTTP redirects.
     */
    default boolean followRedirects() {
        return true;
    }
}
