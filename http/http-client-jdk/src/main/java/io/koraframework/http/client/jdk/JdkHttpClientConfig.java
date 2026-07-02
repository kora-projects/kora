package io.koraframework.http.client.jdk;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.net.http.HttpClient;

@ConfigMapper
public interface JdkHttpClientConfig {

    default boolean followRedirects() {
        return true;
    }

    default HttpClient.Version httpVersion() {
        return HttpClient.Version.HTTP_1_1;
    }
}
