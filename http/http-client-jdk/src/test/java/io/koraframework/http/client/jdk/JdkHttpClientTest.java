package io.koraframework.http.client.jdk;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdkHttpClientTest extends HttpClientTest {

    @Test
    void transportOwnedHeadersAreNotCopiedToJdkRequest() {
        assertThat(JdkHttpClient.isRestrictedHeader("Host")).isTrue();
        assertThat(JdkHttpClient.isRestrictedHeader("Content-Length")).isTrue();
        assertThat(JdkHttpClient.isRestrictedHeader("Authorization")).isFalse();
    }

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout());
        return new JdkHttpClient(client.build());
    }
}
