package io.koraframework.http.client.apache;

import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientFactoryModule;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.jspecify.annotations.Nullable;

public class ApacheHttpClientFactoryModule extends HttpClientFactoryModule {

    private final String configPath;

    public ApacheHttpClientFactoryModule(String baseConfigPath) {
        this(baseConfigPath, baseConfigPath + ".apache");
    }

    public ApacheHttpClientFactoryModule(String baseConfigPath, String configPath) {
        super(baseConfigPath);
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public ApacheHttpClientConfig apacheHttpClientConfig(Config config, ConfigValueMapper<ApacheHttpClientConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public ApacheHttpClient apacheHttpClient(@Tag(Tag.Factory.class) org.apache.hc.client5.http.classic.HttpClient apacheHttpClient) {
        return new ApacheHttpClient(apacheHttpClient);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public ApacheHttpClientWrapper apacheHttpClientWrapper(@Tag(Tag.Factory.class) HttpClientConfig baseConfig,
                                                           @Tag(Tag.Factory.class) ApacheHttpClientConfig apacheConfig,
                                                           @Tag(Tag.Factory.class) @Nullable Configurer<RequestConfig.Builder> requestConfigurer,
                                                           @Tag(Tag.Factory.class) @Nullable Configurer<HttpClientBuilder> clientConfigurer) {
        return new ApacheHttpClientWrapper(baseConfig, apacheConfig, requestConfigurer, clientConfigurer);
    }
}
