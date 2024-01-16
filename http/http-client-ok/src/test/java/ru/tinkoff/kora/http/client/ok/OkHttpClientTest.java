package ru.tinkoff.kora.http.client.ok;

import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.client.common.HttpClientTest;

class OkHttpClientTest extends HttpClientTest {
    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        return new OkHttpClient(new okhttp3.OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout())
            .readTimeout(config.readTimeout())
            .build()
        );
    }
}
