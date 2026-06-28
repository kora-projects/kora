package io.koraframework.http.client.apache;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.util.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientModule;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.jspecify.annotations.Nullable;

public interface ApacheHttpClientModule extends HttpClientModule {

    default ApacheHttpClient apacheHttpClient(HttpClient apacheHttpClient) {
        return new ApacheHttpClient(apacheHttpClient);
    }

    default ApacheHttpClientConfig apacheHttpClientConfig(Config config, ConfigValueExtractor<ApacheHttpClientConfig> extractor) {
        return extractor.extractOrThrow(config.get("httpClient.apache"));
    }

    @DefaultComponent
    default ApacheHttpClientWrapper apacheHttpClientWrapper(HttpClientConfig baseConfig,
                                                            ApacheHttpClientConfig apacheConfig,
                                                            @Nullable Configurer<RequestConfig.Builder> requestConfigurer,
                                                            @Nullable Configurer<HttpClientBuilder> clientConfigurer) {
        return new ApacheHttpClientWrapper(baseConfig, apacheConfig, requestConfigurer, clientConfigurer);
    }
}
