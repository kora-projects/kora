package ru.tinkoff.kora.http.client.ok;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface OkHttpClientConfig {

    /**
     * @return Whether to follow HTTP redirects.
     */
    default boolean followRedirects() {
        return true;
    }

    /**
     * @return Whether to retry a request after a connection failure, this can affect the maximum connection establishment time.
     */
    default boolean retryOnConnectionFailure() {
        return true;
    }

    /**
     * @return Maximum HTTP protocol version to use.
     */
    default HttpVersion httpVersion() {
        return HttpVersion.HTTP_1_1;
    }

    enum HttpVersion {
        HTTP_1_1,
        HTTP_2,
        HTTP_3
    }
}
