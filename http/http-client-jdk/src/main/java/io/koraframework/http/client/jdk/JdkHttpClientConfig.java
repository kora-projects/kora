package io.koraframework.http.client.jdk;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.net.http.HttpClient;

@ConfigValueExtractor
public interface JdkHttpClientConfig {

    default boolean followRedirects() {
        return true;
    }

    default int threads() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }

    default HttpClient.Version httpVersion() {
        return HttpClient.Version.HTTP_1_1;
    }
}
