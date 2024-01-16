package ru.tinkoff.kora.http.client.ok;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface OkHttpClientConfig {
    default boolean followRedirects() {
        return true;
    }
}
