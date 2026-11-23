package io.koraframework.http.client.ok;

import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.request.HttpClientRequest;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
    void getAllOfAbsentHeaderIsNull() {
        var headers = new OkHttpHeaders(Headers.of("a", "1"));

        assertThat(headers.getAll("x-missing")).isNull();
        assertThat(headers.getAll("A")).containsExactly("1");
    }

    @Test
    void notModifiedWithContentLengthHasEmptyBody() throws Exception {
        var rawResponse = "HTTP/1.1 304 Not Modified\r\nContent-Length: 10\r\nETag: \"x\"\r\n\r\n";
        try (var ss = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                try (var s = ss.accept()) {
                    var in = s.getInputStream();
                    var head = new StringBuilder();
                    while (!head.toString().endsWith("\r\n\r\n")) {
                        var c = in.read();
                        if (c < 0) return;
                        head.append((char) c);
                    }
                    s.getOutputStream().write(rawResponse.getBytes(StandardCharsets.ISO_8859_1));
                    s.getOutputStream().flush();
                    Thread.sleep(5_000); // keep the connection open: the client must not wait for the advertised body
                } catch (IOException | InterruptedException ignored) {
                }
            });
            var client = new OkHttpClient(new okhttp3.OkHttpClient.Builder().readTimeout(Duration.ofSeconds(2)).build());
            var request = HttpClientRequest.get("http://127.0.0.1:" + ss.getLocalPort() + "/").build();

            try (var rs = client.execute(request); var body = rs.body(); var is = body.asInputStream()) {
                assertThat(rs.code()).isEqualTo(304);
                assertThat(body.contentLength()).isZero();
                assertThat(is.readAllBytes()).isEmpty();
                assertThat(rs.toString()).contains("bodyLength=0");
            }
        }
    }
}
