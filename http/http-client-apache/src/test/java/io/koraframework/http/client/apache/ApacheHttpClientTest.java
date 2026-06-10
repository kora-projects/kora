package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;

import java.util.concurrent.TimeUnit;

public class ApacheHttpClientTest extends HttpClientTest {

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        ApacheHttpClient httpClient = new ApacheHttpClient(HttpClientBuilder.create()
            .setDefaultRequestConfig(RequestConfig.custom()
                .setResponseTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build())
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .build())
                .build())
            .build());
        return httpClient;
    }
}
