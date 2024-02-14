package ru.tinkoff.kora.http.client.ok;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.client.common.HttpClientModule;

public interface OkHttpClientModule extends HttpClientModule {

    default OkHttpClient okHttpClient(okhttp3.OkHttpClient client) {
        return new OkHttpClient(client);
    }

    default OkHttpClientConfig okHttpClientConfig(Config config, ConfigValueExtractor<OkHttpClientConfig> extractor) {
        return extractor.extract(config.get("httpClient.ok"));
    }

    @DefaultComponent
    default OkHttpClientWrapper okHttpClientWrapper(OkHttpClientConfig config, HttpClientConfig baseConfig) {
        return new OkHttpClientWrapper(config, baseConfig);
    }
}
