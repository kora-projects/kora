package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.common.body.HttpBody;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ApacheHttpClientTest extends HttpClientTest {

    @Test
    void transportFramingHeadersAreOwnedByApacheEntity() {
        var request = HttpClientRequest.post("/")
            .header("Content-Length", "4")
            .header("Transfer-Encoding", "chunked")
            .body(HttpBody.plaintext("test"))
            .build();

        var apacheRequest = ((ApacheHttpClient) createClient(new HttpClientConfig() {
            @Override
            public HttpClientProxyConfig proxy() {
                return null;
            }
        })).convertToApacheRequest(request);

        assertThat(apacheRequest.containsHeader("Content-Length")).isFalse();
        assertThat(apacheRequest.containsHeader("Transfer-Encoding")).isFalse();
        assertThat(apacheRequest.getEntity().getContentLength()).isEqualTo(4);
    }

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
