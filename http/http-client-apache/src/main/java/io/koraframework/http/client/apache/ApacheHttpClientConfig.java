package io.koraframework.http.client.apache;

import io.koraframework.config.common.annotation.ConfigMapper;

@ConfigMapper
public interface ApacheHttpClientConfig {

    default boolean followRedirects() {
        return true;
    }

    default int maxRedirects() {
        return 3;
    }

    default int maxConnections() {
        return Runtime.getRuntime().availableProcessors() * 250;
    }
}
