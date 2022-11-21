package ru.tinkoff.kora.http.client.jdk;

import com.typesafe.config.Config;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.client.common.HttpClientModule;

import java.net.http.HttpClient;

public interface JdkHttpClientModule extends HttpClientModule {
    default JdkHttpClient jdkHttpClient(HttpClient client) {
        return new JdkHttpClient(client);
    }

    default JdkHttpClientConfig jdkHttpClientConfig(Config config, ConfigValueExtractor<JdkHttpClientConfig> extractor) {
        if (config.hasPath("httpClient.jdk")) {
            return extractor.extract(config.getValue("httpClient.jdk"));
        } else {
            return new JdkHttpClientConfig(null);
        }
    }

    @DefaultComponent
    default JdkHttpClientWrapper jdkHttpClientWrapper(JdkHttpClientConfig config, HttpClientConfig baseConfig) {
        return new JdkHttpClientWrapper(config, baseConfig);
    }
}
