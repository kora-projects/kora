package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.net.http.HttpClient;

@ConfigValueExtractor
public interface JdkHttpClientConfig {

    /**
     * @return Whether to follow HTTP redirects.
     */
    default boolean followRedirects() {
        return true;
    }

    /**
     * @return Number of threads for the HTTP client.
     */
    default int threads() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }

    /**
     * @return Which HTTP protocol version to use.
     */
    default HttpClient.Version httpVersion() {
        return HttpClient.Version.HTTP_1_1;
    }
}
