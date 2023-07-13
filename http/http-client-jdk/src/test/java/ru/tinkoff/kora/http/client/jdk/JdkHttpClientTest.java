package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.client.common.HttpClientTest;

import java.time.Duration;

class JdkHttpClientTest extends HttpClientTest {

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout());
        return new JdkHttpClient(client.build());
    }
}
