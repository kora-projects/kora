package ru.tinkoff.kora.http.client.ok;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface OkHttpClientConfig {

    default boolean followRedirects() {
        return true;
    }

    default HttpVersion httpVersion() {
        return HttpVersion.HTTP_1_1;
    }

    enum HttpVersion {
        HTTP_1_1,
        HTTP_2,
        HTTP_3
    }
}
