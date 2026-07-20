package io.koraframework.http.client.jdk;

import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientFactoryModule;
import org.jspecify.annotations.Nullable;

import java.net.http.HttpClient;

public class JdkHttpClientFactoryModule extends HttpClientFactoryModule {

    private final String configPath;

    public JdkHttpClientFactoryModule(String baseConfigPath) {
        this(baseConfigPath, baseConfigPath + ".jdk");
    }

    public JdkHttpClientFactoryModule(String baseConfigPath, String configPath) {
        super(baseConfigPath);
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public JdkHttpClientConfig jdkHttpClientConfig(Config config, ConfigValueMapper<JdkHttpClientConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public JdkHttpClient jdkHttpClient(@Tag(Tag.Factory.class) HttpClient client) {
        return new JdkHttpClient(client);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public JdkHttpClientWrapper jdkHttpClientWrapper(@Tag(Tag.Factory.class) JdkHttpClientConfig config,
                                                     @Tag(Tag.Factory.class) HttpClientConfig baseConfig,
                                                     @Tag(Tag.Factory.class) @Nullable Configurer<HttpClient.Builder> clientConfigurer) {
        return new JdkHttpClientWrapper(config, baseConfig, clientConfigurer);
    }
}
