package io.koraframework.http.client.apache;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.http.client.common.HttpClientConfig;

@ConfigValueExtractor
public interface ApacheHttpClientConfig extends HttpClientConfig {

    default boolean followRedirects() {
        return true;
    }

    default int maxRedirects() {
        return 3;
    }
}
