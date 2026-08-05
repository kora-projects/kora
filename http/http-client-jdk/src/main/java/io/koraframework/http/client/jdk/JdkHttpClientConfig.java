package io.koraframework.http.client.jdk;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.net.http.HttpClient;

@ConfigMapper
public interface JdkHttpClientConfig {

    /**
     * @return Whether to follow HTTP redirects.
     */
    default boolean followRedirects() {
        return true;
    }

    /**
     * @return Which HTTP protocol version to use.
     */
    default HttpClient.Version httpVersion() {
        return HttpClient.Version.HTTP_1_1;
    }
}
