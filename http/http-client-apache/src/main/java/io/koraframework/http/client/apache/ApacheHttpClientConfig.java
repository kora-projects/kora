package io.koraframework.http.client.apache;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.http.client.common.HttpClientConfig;

@ConfigMapper
public interface ApacheHttpClientConfig extends HttpClientConfig {

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
