package io.koraframework.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdkHttpClientTest extends HttpClientTest {

    @Test
    void transportOwnedHeadersAreNotCopiedToJdkRequest() {
        assertThat(JdkHttpClient.isRestrictedHeader("Host")).isTrue();
        assertThat(JdkHttpClient.isRestrictedHeader("Content-Length")).isTrue();
        assertThat(JdkHttpClient.isRestrictedHeader("Authorization")).isFalse();
    }

    @Test
    void readTimeoutFromConfigIsApplied() throws Exception {
        var release = new CountDownLatch(1);
        var slowServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        slowServer.createContext("/", exchange -> {
            try {
                release.await(5, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException ignored) {
            } finally {
                exchange.close();
            }
        });
        slowServer.start();
        try {
            var baseConfig = new HttpClientConfig() {
                @Override
                public Duration readTimeout() {
                    return Duration.ofMillis(200);
                }

                @Override
                public @Nullable HttpClientProxyConfig proxy() {
                    return null;
                }
            };
            var wrapper = new JdkHttpClientWrapper(new JdkHttpClientConfig() {}, baseConfig, null);
            wrapper.init();
            var client = new JdkHttpClientFactoryModule("httpClient").jdkHttpClient(wrapper.value(), baseConfig);
            var request = HttpClientRequest.get("http://localhost:" + slowServer.getAddress().getPort() + "/").build();

            assertThatThrownBy(() -> client.execute(request))
                .isInstanceOf(HttpClientTimeoutException.class);
        } finally {
            release.countDown();
            slowServer.stop(0);
        }
    }

    @Test
    void zeroReadTimeoutMeansNoTimeout() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new JdkHttpClient(java.net.http.HttpClient.newHttpClient(), Duration.ZERO);
            var request = HttpClientRequest.get("http://localhost:" + server.getAddress().getPort() + "/").build();

            try (var response = client.execute(request)) {
                assertThat(response.code()).isEqualTo(200);
            }
        } finally {
            server.stop(0);
        }
    }

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout());
        return new JdkHttpClient(client.build());
    }
}
