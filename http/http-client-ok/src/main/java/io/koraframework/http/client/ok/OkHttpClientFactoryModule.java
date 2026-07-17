package io.koraframework.http.client.ok;

import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientFactoryModule;
import org.jspecify.annotations.Nullable;

public class OkHttpClientFactoryModule extends HttpClientFactoryModule {

    private final String configPath;

    public OkHttpClientFactoryModule(String baseConfigPath) {
        this(baseConfigPath, baseConfigPath + ".ok");
    }

    public OkHttpClientFactoryModule(String baseConfigPath, String configPath) {
        super(baseConfigPath);
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public OkHttpClientConfig okHttpClientConfig(Config config, ConfigValueMapper<OkHttpClientConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public OkHttpClient okHttpClient(@Tag(Tag.Factory.class) okhttp3.OkHttpClient client) {
        return new OkHttpClient(client);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public OkHttpClientWrapper okHttpClientWrapper(@Tag(Tag.Factory.class) OkHttpClientConfig config,
                                                   @Tag(Tag.Factory.class) HttpClientConfig baseConfig,
                                                   @Tag(Tag.Factory.class) @Nullable Configurer<okhttp3.OkHttpClient.Builder> configurer) {
        return new OkHttpClientWrapper(config, baseConfig, configurer);
    }
}
