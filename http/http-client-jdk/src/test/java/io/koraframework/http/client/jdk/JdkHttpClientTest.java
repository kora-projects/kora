package io.koraframework.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.exception.HttpClientConnectionException;
import io.koraframework.http.client.common.exception.HttpClientException;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.common.body.HttpBody;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class JdkHttpClientTest extends HttpClientTest {

    @Test
    void transportOwnedHeadersAreNotCopiedToJdkRequest() {
        assertThat(JdkHttpClient.isRestrictedHeader("Host")).isTrue();
        assertThat(JdkHttpClient.isRestrictedHeader("Content-Length")).isTrue();
        assertThat(JdkHttpClient.isRestrictedHeader("Transfer-Encoding")).isTrue();
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
    void requestTimeoutBoundsStalledBody() throws Exception {
        var release = new CountDownLatch(1);
        try (var server = new RawServer((head, out) -> {
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nabc".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            release.await(15, TimeUnit.SECONDS);
            return false;
        })) {
            var client = jdkClient(config(Duration.ofSeconds(60)));
            var request = HttpClientRequest.get(server.url()).requestTimeout(500).build();

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                try (var response = client.execute(request); var body = response.body().asInputStream()) {
                    assertThatThrownBy(body::readAllBytes).isInstanceOf(HttpClientTimeoutException.class);
                }
            });
        } finally {
            release.countDown();
        }
    }

    @Test
    void droppedConnectionIsConnectionException() throws Exception {
        try (var server = new RawServer((head, out) -> false)) {
            var client = jdkClient(config(Duration.ofSeconds(5)));

            assertThatThrownBy(() -> client.execute(HttpClientRequest.get(server.url()).build()))
                .isInstanceOf(HttpClientConnectionException.class);
            assertThatThrownBy(() -> client.execute(HttpClientRequest.post(server.url()).body(HttpBody.json("{}")).build()))
                .isInstanceOf(HttpClientConnectionException.class);
        }
    }

    @Test
    void callerTransferEncodingHeaderDoesNotBreakFraming() throws Exception {
        try (var server = new RawServer((head, out) -> {
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            return true;
        })) {
            var client = jdkClient(config(Duration.ofSeconds(5)));
            var request = HttpClientRequest.post(server.url())
                .header("Transfer-Encoding", "chunked")
                .body(HttpBody.plaintext("test"))
                .build();

            try (var response = client.execute(request)) {
                assertThat(response.code()).isEqualTo(200);
            }
            assertThat(server.requests.getFirst()).containsEntry("content-length", "4").doesNotContainKey("transfer-encoding");
        }
    }

    @Test
    void invalidHeaderValueIsHttpClientException() {
        var client = jdkClient(config(Duration.ofSeconds(5)));
        var request = HttpClientRequest.get("http://localhost:1/").header("X-A", "a\nb").build();

        assertThatThrownBy(() -> client.execute(request)).isInstanceOf(HttpClientException.class);
    }

    private static HttpClient jdkClient(HttpClientConfig baseConfig) {
        var wrapper = new JdkHttpClientWrapper(new JdkHttpClientConfig() {}, baseConfig, null);
        wrapper.init();
        return new JdkHttpClientFactoryModule("httpClient").jdkHttpClient(wrapper.value(), baseConfig);
    }

    private static HttpClientConfig config(Duration readTimeout) {
        return new HttpClientConfig() {
            @Override
            public Duration readTimeout() {
                return readTimeout;
            }

            @Override
            public @Nullable HttpClientProxyConfig proxy() {
                return null;
            }
        };
    }

    private interface RawHandler {
        /** @return false to close the connection */
        boolean handle(Map<String, String> head, OutputStream out) throws Exception;
    }

    /** Minimal HTTP/1.1 server that records request heads (lower-cased header names) and reads Content-Length bodies */
    private static final class RawServer implements AutoCloseable {
        final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();
        private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());

        RawServer(RawHandler handler) throws IOException {
            Thread.ofVirtual().start(() -> {
                while (!socket.isClosed()) {
                    try {
                        var s = socket.accept();
                        Thread.ofVirtual().start(() -> {
                            try (s) {
                                var in = new BufferedInputStream(s.getInputStream());
                                var out = s.getOutputStream();
                                String line;
                                while ((line = readLine(in)) != null) {
                                    var head = new TreeMap<String, String>();
                                    head.put(":request", line);
                                    while ((line = readLine(in)) != null && !line.isEmpty()) {
                                        var i = line.indexOf(':');
                                        head.put(line.substring(0, i).trim().toLowerCase(Locale.ROOT), line.substring(i + 1).trim());
                                    }
                                    var cl = head.get("content-length");
                                    if (cl != null) {
                                        in.readNBytes(Integer.parseInt(cl));
                                    }
                                    requests.add(head);
                                    if (!handler.handle(head, out)) {
                                        return;
                                    }
                                    out.flush();
                                }
                            } catch (Exception ignored) {
                            }
                        });
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        int port() {
            return socket.getLocalPort();
        }

        String url() {
            return "http://127.0.0.1:" + port() + "/";
        }

        @Nullable
        private static String readLine(InputStream in) throws IOException {
            var sb = new StringBuilder();
            int c;
            while ((c = in.read()) >= 0 && c != '\n') {
                if (c != '\r') {
                    sb.append((char) c);
                }
            }
            return c < 0 && sb.isEmpty() ? null : sb.toString();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout());
        return new JdkHttpClient(client.build());
    }
}
