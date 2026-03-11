package io.koraframework.http.client.ok;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;

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
