package io.koraframework.http.client.common;

import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;

public class HttpClientFactoryModule {

    private final String configPath;

    public HttpClientFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public HttpClientConfig httpClientConfig(Config config, ConfigValueMapper<HttpClientConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }
}
