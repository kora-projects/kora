package io.koraframework.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
    void proxyTunnel407HasReadableBody() throws Exception {
        try (var proxy = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                try (var sock = proxy.accept()) {
                    readHead(sock.getInputStream());
                    writeResponse(sock.getOutputStream(), "407 Proxy Authentication Required", "denied", "Proxy-Authenticate: Basic realm=\"proxy\"\r\n");
                } catch (IOException ignored) {
                }
            });
            var proxyConfig = new HttpClientConfig.HttpClientProxyConfig() {
                @Override
                public String host() {
                    return "127.0.0.1";
                }

                @Override
                public int port() {
                    return proxy.getLocalPort();
                }

                @Override
                public @Nullable List<String> nonProxyHosts() {
                    return null;
                }

                @Override
                public @Nullable String user() {
                    return null;
                }

                @Override
                public @Nullable String password() {
                    return null;
                }
            };
            var baseConfig = new HttpClientConfig() {
                @Override
                public @Nullable HttpClientProxyConfig proxy() {
                    return proxyConfig;
                }
            };
            var wrapper = new JdkHttpClientWrapper(new JdkHttpClientConfig() {}, baseConfig, null);
            wrapper.init();
            try {
                var client = new JdkHttpClientFactoryModule("httpClient").jdkHttpClient(wrapper.value(), baseConfig);
                var response = client.execute(HttpClientRequest.get("https://upstream.example/x").build());
                assertThat(response.code()).isEqualTo(407);
                try (var body = response.body().asInputStream()) {
                    assertThat(body).isNotNull();
                    body.readAllBytes();
                }
                response.close();
            } finally {
                wrapper.release();
            }
        }
    }

    @Test
    void releaseClosesPooledConnections() throws Exception {
        var closed = new CountDownLatch(1);
        try (var ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                try (var sock = ss.accept()) {
                    var in = sock.getInputStream();
                    readHead(in);
                    writeResponse(sock.getOutputStream(), "200 OK", "ok", "");
                    while (in.read() >= 0) ;
                } catch (IOException ignored) {
                } finally {
                    closed.countDown();
                }
            });
            var baseConfig = new HttpClientConfig() {
                @Override
                public @Nullable HttpClientProxyConfig proxy() {
                    return null;
                }
            };
            var wrapper = new JdkHttpClientWrapper(new JdkHttpClientConfig() {}, baseConfig, null);
            wrapper.init();
            var client = new JdkHttpClientFactoryModule("httpClient").jdkHttpClient(wrapper.value(), baseConfig);
            try (var response = client.execute(HttpClientRequest.get("http://127.0.0.1:" + ss.getLocalPort() + "/").build())) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().asInputStream().readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("ok");
            }
            wrapper.release();
            assertThat(closed.await(3, TimeUnit.SECONDS)).as("pooled connection closed after release").isTrue();
        }
    }

    @Test
    void releaseDoesNotHangOnUnconsumedResponse() throws Exception {
        var serverDone = new CountDownLatch(1);
        try (var ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                try (var sock = ss.accept()) {
                    var in = sock.getInputStream();
                    readHead(in);
                    var out = sock.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\npartial".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    while (in.read() >= 0) ;
                } catch (IOException ignored) {
                } finally {
                    serverDone.countDown();
                }
            });
            var baseConfig = new HttpClientConfig() {
                @Override
                public @Nullable HttpClientProxyConfig proxy() {
                    return null;
                }
            };
            var wrapper = new JdkHttpClientWrapper(new JdkHttpClientConfig() {}, baseConfig, null);
            wrapper.init();
            var client = new JdkHttpClientFactoryModule("httpClient").jdkHttpClient(wrapper.value(), baseConfig);
            var response = client.execute(HttpClientRequest.get("http://127.0.0.1:" + ss.getLocalPort() + "/").build());
            assertThat(response.code()).isEqualTo(200);

            var released = new CountDownLatch(1);
            Thread.ofVirtual().start(() -> {
                wrapper.release();
                released.countDown();
            });
            assertThat(released.await(10, TimeUnit.SECONDS)).as("release() returns with an open response").isTrue();
            assertThat(serverDone.await(3, TimeUnit.SECONDS)).as("connection closed after release").isTrue();
        }
    }

    private static void readHead(InputStream in) throws IOException {
        var head = new StringBuilder();
        int c;
        while (!head.toString().endsWith("\r\n\r\n") && (c = in.read()) >= 0) {
            head.append((char) c);
        }
    }

    private static void writeResponse(OutputStream out, String status, String body, String extraHeaders) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + status + "\r\nContent-Length: " + bytes.length + "\r\n" + extraHeaders + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout());
        return new JdkHttpClient(client.build());
    }
}
