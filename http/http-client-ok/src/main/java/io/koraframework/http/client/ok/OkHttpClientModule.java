package io.koraframework.http.client.ok;

import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.http.client.common.HttpClientModule;

public interface OkHttpClientModule extends HttpClientModule {

    @FactoryModule
    default OkHttpClientFactoryModule okHttpClientFactory() {
        return new OkHttpClientFactoryModule("httpClient");
    }
}
