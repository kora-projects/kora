package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
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
