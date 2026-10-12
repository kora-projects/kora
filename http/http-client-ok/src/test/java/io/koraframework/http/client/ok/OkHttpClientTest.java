package io.koraframework.http.client.ok;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.exception.HttpClientTimeoutException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OkHttpClientTest extends HttpClientTest {
    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        return new OkHttpClient(new okhttp3.OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout())
            .readTimeout(config.readTimeout())
            .build()
        );
    }

    @Test
    void readTimeoutIsTimeoutException() throws Exception {
        withRawServer(s -> {}, port -> {
            var client = new OkHttpClient(new okhttp3.OkHttpClient.Builder().readTimeout(Duration.ofMillis(200)).build());
            for (int i = 0; i < 5; i++) {
                var request = HttpClientRequest.get("http://127.0.0.1:" + port + "/" + i).build();
                assertThatThrownBy(() -> client.execute(request).close())
                    .isInstanceOf(HttpClientTimeoutException.class);
            }
        });
    }

    @Test
    void headersSizeCountsDistinctNames() throws Exception {
        withRawServer(s -> {
            try {
                s.getInputStream().read(new byte[8192]);
                s.getOutputStream().write(("HTTP/1.1 200 OK\r\nX-B: 1\r\nx-b: 2\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok")
                    .getBytes(StandardCharsets.ISO_8859_1));
                s.getOutputStream().flush();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, port -> {
            var client = new OkHttpClient(new okhttp3.OkHttpClient.Builder().build());
            try (var rs = client.execute(HttpClientRequest.get("http://127.0.0.1:" + port + "/").build())) {
                var names = new ArrayList<String>();
                for (var e : rs.headers()) names.add(e.getKey());
                assertThat(rs.headers().getAll("X-B")).containsExactly("1", "2");
                assertThat(rs.headers().size()).isEqualTo(names.size()).isEqualTo(rs.headers().names().size()).isEqualTo(3);
            }
        });
    }

    interface PortConsumer {
        void accept(int port) throws Exception;
    }

    private static void withRawServer(Consumer<Socket> handler, PortConsumer test) throws Exception {
        var sockets = new ArrayList<Socket>();
        try (var ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                try {
                    while (true) {
                        var s = ss.accept();
                        synchronized (sockets) {
                            sockets.add(s);
                        }
                        handler.accept(s);
                    }
                } catch (Exception ignored) {
                }
            });
            test.accept(ss.getLocalPort());
        } finally {
            synchronized (sockets) {
                for (var s : sockets) s.close();
            }
        }
    }
}
