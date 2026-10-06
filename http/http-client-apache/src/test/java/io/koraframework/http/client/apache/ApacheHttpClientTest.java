package io.koraframework.http.client.apache;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.$HttpClientConfig_ConfigValueMapper;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.common.body.HttpBody;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @Timeout(20)
    void redirectsDisabledAlsoWithRequestTimeout() throws Exception {
        try (var srv = new RawServer((target, out) -> respond(out, target.equals("/redirect")
            ? "HTTP/1.1 302 Found\r\nLocation: /target\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            : "HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\ntarget"))) {
            var wrapper = wrapper(Duration.ofSeconds(5), false);
            try {
                var client = new ApacheHttpClient(wrapper.value());
                assertThat(call(client, HttpClientRequest.get(srv.url("/redirect")).build()).code()).isEqualTo(302);
                assertThat(call(client, HttpClientRequest.get(srv.url("/redirect")).requestTimeout(3000).build()).code()).isEqualTo(302);
            } finally {
                wrapper.release();
            }
        }
    }

    @Test
    @Timeout(30)
    void requestTimeoutBoundsTricklingResponse() throws Exception {
        try (var srv = new RawServer((target, out) -> {
            for (var b : "HTTP/1.1 200 OK\r\nX-Padding: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\r\nContent-Length: 2\r\n\r\nok".getBytes(StandardCharsets.ISO_8859_1)) {
                out.write(b);
                out.flush();
                Thread.sleep(100);
            }
        })) {
            var wrapper = wrapper(Duration.ofSeconds(60), true);
            try {
                var client = new ApacheHttpClient(wrapper.value());
                var started = System.nanoTime();
                assertThatThrownBy(() -> call(client, HttpClientRequest.get(srv.url("/")).requestTimeout(1000).build()))
                    .isInstanceOf(HttpClientTimeoutException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
            } finally {
                wrapper.release();
            }
        }
    }

    @Test
    @Timeout(20)
    void contentTypeHeaderVsBody() throws Exception {
        try (var srv = new RawServer((target, out) -> respond(out, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"))) {
            var wrapper = wrapper(Duration.ofSeconds(5), true);
            try {
                var client = new ApacheHttpClient(wrapper.value());
                call(client, HttpClientRequest.post(srv.url("/"))
                    .header("Content-Type", "application/xml")
                    .body(HttpBody.json("{}"))
                    .build());
                assertThat(srv.headers).map(String::toLowerCase).filteredOn(h -> h.startsWith("content-type:"))
                    .containsExactly("content-type: application/json");
            } finally {
                wrapper.release();
            }
        }
    }

    private static ApacheHttpClientWrapper wrapper(Duration readTimeout, boolean followRedirects) {
        var wrapper = new ApacheHttpClientWrapper(
            new $HttpClientConfig_ConfigValueMapper.HttpClientConfig_Impl(Duration.ofSeconds(2), readTimeout, null, false),
            new ApacheHttpClientConfig() {
                @Override
                public boolean followRedirects() {
                    return followRedirects;
                }
            }, null, null);
        wrapper.init();
        return wrapper;
    }

    private static void respond(OutputStream out, String response) throws IOException {
        out.write(response.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private interface RawHandler {
        void handle(String target, OutputStream out) throws Exception;
    }

    /** Minimal HTTP/1.1 server: answers one request per connection and records request header lines. */
    private static final class RawServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final List<String> headers = new CopyOnWriteArrayList<>();

        RawServer(RawHandler handler) throws IOException {
            Thread.ofVirtual().start(() -> {
                while (!socket.isClosed()) {
                    try {
                        var s = socket.accept();
                        Thread.ofVirtual().start(() -> {
                            try (s) {
                                var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
                                var target = in.readLine().split(" ")[1];
                                var contentLength = 0;
                                for (var line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                                    headers.add(line);
                                    if (line.toLowerCase().startsWith("content-length:")) {
                                        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                                    }
                                }
                                in.skip(contentLength);
                                handler.handle(target, s.getOutputStream());
                            } catch (Exception ignored) {
                            }
                        });
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        String url(String path) {
            return "http://127.0.0.1:" + socket.getLocalPort() + path;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
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
