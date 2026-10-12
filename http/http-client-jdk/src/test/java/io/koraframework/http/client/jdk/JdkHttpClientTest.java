package io.koraframework.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
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

    @Test
    void bodilessGetHeadDeleteHaveNoContentLength() throws Exception {
        assertThat(requestHead("GET")).doesNotContainIgnoringCase("content-length");
        assertThat(requestHead("HEAD")).doesNotContainIgnoringCase("content-length");
        assertThat(requestHead("DELETE")).doesNotContainIgnoringCase("content-length");
        assertThat(requestHead("POST")).containsIgnoringCase("content-length: 0");
        assertThat(requestHead("PUT")).containsIgnoringCase("content-length: 0");
    }

    private static String requestHead(String method) throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var head = new CompletableFuture<String>();
            Thread.ofVirtual().start(() -> {
                try (var s = server.accept()) {
                    var in = s.getInputStream();
                    var sb = new StringBuilder();
                    int c;
                    while (!sb.toString().endsWith("\r\n\r\n") && (c = in.read()) >= 0) {
                        sb.append((char) c);
                    }
                    head.complete(sb.toString());
                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                } catch (Exception e) {
                    head.completeExceptionally(e);
                }
            });
            var client = new JdkHttpClient(java.net.http.HttpClient.newHttpClient());
            var uri = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/");
            try (var response = client.execute(HttpClientRequest.of(method, uri, "/", HttpHeaders.empty(), HttpBody.empty(), null))) {
                assertThat(response.code()).isEqualTo(200);
            }
            return head.get(5, TimeUnit.SECONDS);
        }
    }

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout());
        return new JdkHttpClient(client.build());
    }
}
