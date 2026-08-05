package io.koraframework.http.client.ok;

import io.koraframework.config.common.annotation.ConfigMapper;

@ConfigMapper
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
