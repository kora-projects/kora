package io.koraframework.http.client.apache;

import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientModule;
import org.apache.hc.client5.http.classic.HttpClient;

public interface ApacheHttpClientModule extends HttpClientModule {

    default ApacheHttpClient apacheHttpClient(HttpClient apacheHttpClient) {
        return new ApacheHttpClient(apacheHttpClient);
    }

    default ApacheHttpClientConfig apacheHttpClientConfig(Config config, ConfigValueExtractor<ApacheHttpClientConfig> extractor) {
        return extractor.extract(config.get("httpClient.apache"));
    }

    @DefaultComponent
    default ApacheHttpClientWrapper apacheHttpClientWrapper(HttpClientConfig baseConfig,
                                                            ApacheHttpClientConfig apacheConfig) {
        return new ApacheHttpClientWrapper(baseConfig, apacheConfig);
    }
}
