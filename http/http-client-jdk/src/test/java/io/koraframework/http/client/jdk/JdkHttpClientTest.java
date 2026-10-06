package io.koraframework.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.exception.HttpClientEncoderException;
import io.koraframework.http.client.common.exception.HttpClientException;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.request.form.MultipartWriterUtils;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.body.JsonHttpBodyOutput;
import io.koraframework.http.common.form.FormMultipart;
import io.koraframework.json.common.JsonWriter;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

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
    void streamingBodyWithReusedBufferIsSentIntact() throws Exception {
        var data = randomBytes(4 * 1024 * 1024);
        var body = HttpBodyOutput.octetStream(os -> {
            var buf = new byte[16 * 1024];
            var in = new ByteArrayInputStream(data);
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        });

        assertThat(postToEchoServer(body).get("/")).isEqualTo(data);
    }

    @Test
    void inputStreamBodyIsSentIntact() throws Exception {
        var data = randomBytes(1024 * 1024);

        assertThat(postToEchoServer(HttpBodyOutput.octetStream(new ByteArrayInputStream(data))).get("/")).isEqualTo(data);
    }

    @Test
    void streamingBodyIsNotReplayedOnRedirect() throws Exception {
        var received = new ConcurrentHashMap<String, byte[]>();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                received.put(exchange.getRequestURI().getPath(), exchange.getRequestBody().readAllBytes());
                if (exchange.getRequestURI().getPath().equals("/redirect")) {
                    exchange.getResponseHeaders().add("Location", "/target");
                    exchange.sendResponseHeaders(307, -1);
                } else {
                    exchange.sendResponseHeaders(200, -1);
                }
            }
        });
        server.start();
        try {
            var client = new JdkHttpClient(java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build());
            var data = "streamed-body".getBytes(StandardCharsets.UTF_8);
            var request = HttpClientRequest.post("http://localhost:" + server.getAddress().getPort() + "/redirect")
                .body(HttpBodyOutput.octetStream(new ByteArrayInputStream(data)))
                .build();

            assertThatThrownBy(() -> client.execute(request).close())
                .isInstanceOf(HttpClientEncoderException.class);
            assertThat(received.get("/redirect")).isEqualTo(data);
            assertThat(received).doesNotContainKey("/target");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void replayableStreamingBodyIsResentOnRedirect() throws Exception {
        JsonWriter<String> writer = (gen, v) -> {
            gen.writeStartObject();
            gen.writeStringProperty("name", v);
            gen.writeEndObject();
        };
        assertThat(postWithRedirect(new JsonHttpBodyOutput<>(writer, "bob"))).isEqualTo("{\"name\":\"bob\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(postWithRedirect(HttpBodyOutput.octetStream(os -> os.write("hello".getBytes(StandardCharsets.UTF_8))))).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        var multipart = MultipartWriterUtils.write("b0undary", List.of(new FormMultipart.FormPart.MultipartData("field", "value")));
        assertThat(new String(postWithRedirect(multipart), StandardCharsets.UTF_8))
            .isEqualTo("--b0undary\r\ncontent-disposition: form-data; name=\"field\"\r\ncontent-type: text/plain;charset=utf-8\r\n\r\nvalue\r\n--b0undary--");
    }

    private static byte[] postWithRedirect(HttpBodyOutput body) throws Exception {
        var received = new ConcurrentHashMap<String, byte[]>();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                received.put(exchange.getRequestURI().getPath(), exchange.getRequestBody().readAllBytes());
                if (exchange.getRequestURI().getPath().equals("/redirect")) {
                    exchange.getResponseHeaders().add("Location", "/target");
                    exchange.sendResponseHeaders(307, -1);
                } else {
                    exchange.sendResponseHeaders(200, -1);
                }
            }
        });
        server.start();
        try {
            var client = new JdkHttpClient(java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build());
            var request = HttpClientRequest.post("http://localhost:" + server.getAddress().getPort() + "/redirect").body(body).build();
            try (var rs = client.execute(request)) {
                assertThat(rs.code()).isEqualTo(200);
            }
            return received.get("/target");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void postIsNotResentAfterBodyWasSent() throws Exception {
        var requests = new AtomicInteger();
        try (var serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                while (!serverSocket.isClosed()) {
                    try (var socket = serverSocket.accept()) {
                        var in = socket.getInputStream();
                        var head = new StringBuilder();
                        int c;
                        while (!head.toString().endsWith("\r\n\r\n") && (c = in.read()) >= 0) {
                            head.append((char) c);
                        }
                        var contentLength = Pattern.compile("(?i)content-length: *(\\d+)").matcher(head);
                        if (contentLength.find()) {
                            in.readNBytes(Integer.parseInt(contentLength.group(1)));
                        }
                        requests.incrementAndGet();
                        socket.setSoLinger(true, 0);
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            var client = new JdkHttpClient(java.net.http.HttpClient.newHttpClient());
            var request = HttpClientRequest.post("http://127.0.0.1:" + serverSocket.getLocalPort() + "/pay")
                .body(HttpBody.json("{\"amount\":100}"))
                .build();

            assertThatThrownBy(() -> client.execute(request).close())
                .isInstanceOf(HttpClientException.class);
            Thread.sleep(300);
            assertThat(requests).hasValue(1);
        }
    }

    @Test
    void interruptedRequestKeepsInterruptStatus() throws Exception {
        try (var serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            var client = new JdkHttpClient(java.net.http.HttpClient.newHttpClient());
            var request = HttpClientRequest.get("http://127.0.0.1:" + serverSocket.getLocalPort() + "/slow").build();
            var error = new AtomicReference<Throwable>();
            var interrupted = new AtomicBoolean();
            var thread = Thread.ofVirtual().start(() -> {
                try {
                    client.execute(request).close();
                } catch (Throwable e) {
                    error.set(e);
                }
                interrupted.set(Thread.currentThread().isInterrupted());
            });
            try (var socket = serverSocket.accept()) {
                Thread.sleep(200);
                thread.interrupt();
                thread.join(5000);
            }

            assertThat(error.get()).isInstanceOf(HttpClientException.class);
            assertThat(interrupted).isTrue();
        }
    }

    private static byte[] randomBytes(int size) {
        var bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    private static Map<String, byte[]> postToEchoServer(HttpBodyOutput body) throws IOException {
        var received = new ConcurrentHashMap<String, byte[]>();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                received.put(exchange.getRequestURI().getPath(), exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, -1);
            }
        });
        server.start();
        try {
            var client = new JdkHttpClient(java.net.http.HttpClient.newHttpClient());
            var request = HttpClientRequest.post("http://localhost:" + server.getAddress().getPort() + "/").body(body).build();
            try (var response = client.execute(request)) {
                assertThat(response.code()).isEqualTo(200);
            }
            return received;
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
