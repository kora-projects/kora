package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface JdkHttpClientConfig {
    default int threads() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }
}
