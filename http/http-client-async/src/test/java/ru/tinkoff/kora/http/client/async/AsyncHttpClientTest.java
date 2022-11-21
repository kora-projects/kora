package ru.tinkoff.kora.http.client.async;

import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.client.common.HttpClientTest;

class AsyncHttpClientTest extends HttpClientTest {

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        var module = new AsyncHttpClientModule() {};
        return new AsyncHttpClient(module.nettyAsyncHttpClient(module.nettyClientConfig(null, config)));
    }
}
