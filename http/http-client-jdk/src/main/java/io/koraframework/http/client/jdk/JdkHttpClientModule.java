package io.koraframework.http.client.jdk;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.client.common.HttpClientModule;

import java.net.http.HttpClient;

public interface JdkHttpClientModule extends HttpClientModule {

    @FactoryModule
    default JdkHttpClientFactoryModule jdkHttpClientFactory() {
        return new JdkHttpClientFactoryModule("httpClient");
    }

    @DefaultComponent
    default ConfigValueMapper<HttpClient.Version> jdkHttpClientVersionExtractor() {
        return value -> {
            if (value instanceof ConfigValue.NullValue) {
                return null;
            }
            var str = value.asString();
            return HttpClient.Version.valueOf(str);
        };
    }

}
