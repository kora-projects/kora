package io.koraframework.http.client.jdk;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;

import java.time.Duration;

class JdkHttpClientTest extends HttpClientTest {

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout());
        return new JdkHttpClient(client.build());
    }
}
