package ru.tinkoff.kora.http.client.ok;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.util.Configurer;
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
    default OkHttpClientWrapper okHttpClientWrapper(OkHttpClientConfig config, HttpClientConfig baseConfig, All<Configurer<okhttp3.OkHttpClient.Builder>> configurers) {
        return new OkHttpClientWrapper(config, baseConfig, configurers);
    }
}
