package io.koraframework.http.client.ok;

import com.sun.net.httpserver.HttpServer;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.HttpClientConfig;
import io.koraframework.http.client.common.HttpClientTest;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.request.form.MultipartWriterUtils;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.body.JsonHttpBodyOutput;
import io.koraframework.http.common.form.FormMultipart;
import io.koraframework.json.common.JsonWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class OkHttpClientTest extends HttpClientTest {
    private final ConcurrentHashMap<String, byte[]> receivedBodies = new ConcurrentHashMap<>();
    private HttpServer jdkServer;

    @Override
    protected HttpClient createClient(HttpClientConfig config) {
        return new OkHttpClient(new okhttp3.OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout())
            .readTimeout(config.readTimeout())
            .build()
        );
    }

    @BeforeEach
    void startJdkServer() throws IOException {
        jdkServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        jdkServer.createContext("/", exchange -> {
            try (exchange) {
                var path = exchange.getRequestURI().getPath();
                receivedBodies.put(path, exchange.getRequestBody().readAllBytes());
                switch (path) {
                    case "/redirect" -> {
                        exchange.getResponseHeaders().add("Location", "/target");
                        exchange.sendResponseHeaders(307, -1);
                    }
                    case "/multi-header" -> {
                        exchange.getResponseHeaders().add("Set-Cookie", "a=1");
                        exchange.getResponseHeaders().add("Set-Cookie", "b=2");
                        exchange.sendResponseHeaders(200, -1);
                    }
                    default -> exchange.sendResponseHeaders(200, -1);
                }
            }
        });
        jdkServer.start();
    }

    @AfterEach
    void stopJdkServer() {
        jdkServer.stop(0);
    }

    private String jdkServerUri(String path) {
        return "http://localhost:" + jdkServer.getAddress().getPort() + path;
    }

    @Test
    void testRedirectWithStreamedBodyIsNotFollowed() {
        var client = new OkHttpClient(new okhttp3.OkHttpClient());
        var request = HttpClientRequest.post(jdkServerUri("/redirect"))
            .body(HttpBodyOutput.octetStream(new ByteArrayInputStream("test-request".getBytes(StandardCharsets.UTF_8))))
            .build();

        call(client, request).assertCode(307);
        assertThat(receivedBodies).doesNotContainKey("/target");
    }

    @Test
    void testRedirectWithFullBodyIsFollowed() {
        var client = new OkHttpClient(new okhttp3.OkHttpClient());
        var request = HttpClientRequest.post(jdkServerUri("/redirect"))
            .body(HttpBody.plaintext("test-request"))
            .build();

        call(client, request).assertCode(200);
        assertThat(receivedBodies.get("/target")).asString(StandardCharsets.UTF_8).isEqualTo("test-request");
    }

    @Test
    void testRedirectWithJsonBodyIsFollowed() {
        var client = new OkHttpClient(new okhttp3.OkHttpClient());
        JsonWriter<String> writer = (gen, value) -> gen.writeString(value);
        var request = HttpClientRequest.post(jdkServerUri("/redirect"))
            .body(new JsonHttpBodyOutput<>(writer, "test-request"))
            .build();

        call(client, request).assertCode(200);
        assertThat(receivedBodies.get("/target")).asString(StandardCharsets.UTF_8).isEqualTo("\"test-request\"");
    }

    @Test
    void testRedirectWithMultipartStreamPartIsNotFollowed() {
        var client = new OkHttpClient(new okhttp3.OkHttpClient());
        var part = FormMultipart.file("f", "f.bin", HttpBodyOutput.octetStream(new ByteArrayInputStream("test-request".getBytes(StandardCharsets.UTF_8))));
        var request = HttpClientRequest.post(jdkServerUri("/redirect"))
            .body(MultipartWriterUtils.write("bnd", List.of(part)))
            .build();

        call(client, request).assertCode(307);
        assertThat(receivedBodies.get("/redirect")).asString(StandardCharsets.UTF_8).contains("test-request");
        assertThat(receivedBodies).doesNotContainKey("/target");
    }

    @Test
    void testRedirectWithMultipartBytePartIsFollowed() {
        var client = new OkHttpClient(new okhttp3.OkHttpClient());
        var part = FormMultipart.file("f", "f.bin", "application/octet-stream", "test-request".getBytes(StandardCharsets.UTF_8));
        var request = HttpClientRequest.post(jdkServerUri("/redirect"))
            .body(MultipartWriterUtils.write("bnd", List.of(part)))
            .build();

        call(client, request).assertCode(200);
        assertThat(receivedBodies.get("/target")).asString(StandardCharsets.UTF_8).contains("test-request");
    }

    @Test
    void testGetFirstReturnsFirstValueOfRepeatedHeader() {
        var client = new OkHttpClient(new okhttp3.OkHttpClient());

        var response = call(client, HttpClientRequest.get(jdkServerUri("/multi-header")).build());

        assertThat(response.headers().getAll("set-cookie")).containsExactly("a=1", "b=2");
        assertThat(response.headers().getFirst("SET-COOKIE")).isEqualTo("a=1");
    }
}
