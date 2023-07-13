package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.client.common.HttpClientModule;

import java.net.http.HttpClient;

public interface JdkHttpClientModule extends HttpClientModule {
    default JdkHttpClient jdkHttpClient(HttpClient client) {
        return new JdkHttpClient(client);
    }

    default JdkHttpClientConfig jdkHttpClientConfig(Config config, ConfigValueExtractor<JdkHttpClientConfig> extractor) {
        return extractor.extract(config.get("httpClient.jdk"));
    }

    @DefaultComponent
    default JdkHttpClientWrapper jdkHttpClientWrapper(JdkHttpClientConfig config, HttpClientConfig baseConfig) {
        return new JdkHttpClientWrapper(config, baseConfig);
    }
}
