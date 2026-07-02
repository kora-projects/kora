package io.koraframework.http.client.ok;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientModule;

public interface OkHttpClientModule extends HttpClientModule {

    default OkHttpClient okHttpClient(okhttp3.OkHttpClient client) {
        return new OkHttpClient(client);
    }

    default OkHttpClientConfig okHttpClientConfig(Config config, ConfigValueMapper<OkHttpClientConfig> mapper) {
        return mapper.mapOrThrow(config.get("httpClient.ok"));
    }

    @DefaultComponent
    default OkHttpClientWrapper okHttpClientWrapper(OkHttpClientConfig config, HttpClientConfig baseConfig, All<Configurer<okhttp3.OkHttpClient.Builder>> configurers) {
        return new OkHttpClientWrapper(config, baseConfig, configurers);
    }
}
