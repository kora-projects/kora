package io.koraframework.http.client.jdk;

import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientModule;

import java.net.http.HttpClient;

public interface JdkHttpClientModule extends HttpClientModule {
    default JdkHttpClient jdkHttpClient(HttpClient client) {
        return new JdkHttpClient(client);
    }

    default JdkHttpClientConfig jdkHttpClientConfig(Config config, ConfigValueExtractor<JdkHttpClientConfig> extractor) {
        return extractor.extract(config.get("httpClient.jdk"));
    }

    default ConfigValueExtractor<HttpClient.Version> jdkHttpClientVersionExtractor() {
        return value -> {
            if (value instanceof ConfigValue.NullValue) {
                return null;
            }
            var str = value.asString();
            return HttpClient.Version.valueOf(str);
        };
    }

    @DefaultComponent
    default JdkHttpClientWrapper jdkHttpClientWrapper(JdkHttpClientConfig config, HttpClientConfig baseConfig) {
        return new JdkHttpClientWrapper(config, baseConfig);
    }
}
