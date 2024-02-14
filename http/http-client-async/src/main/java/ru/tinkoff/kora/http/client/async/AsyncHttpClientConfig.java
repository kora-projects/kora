package ru.tinkoff.kora.http.client.async;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface AsyncHttpClientConfig {

    default boolean followRedirects() {
        return true;
    }
}
