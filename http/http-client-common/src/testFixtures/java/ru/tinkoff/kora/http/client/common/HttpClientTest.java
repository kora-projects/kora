package ru.tinkoff.kora.http.client.common;

import ch.qos.logback.classic.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockserver.matchers.Times;
import org.mockserver.model.Header;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static ru.tinkoff.kora.http.common.HttpMethod.POST;

@TestMethodOrder(MethodOrderer.Random.class)
public abstract class HttpClientTest extends HttpClientTestBase {
    @Test
    protected void testHappyPath() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/")
            .withMethod(POST)
            .withHeader("Content-Type", "text/plain; charset=UTF-8")
            .withBody("test-request", StandardCharsets.UTF_8);
        server.when(expectedRequest).respond(response()
            .withBody("test-response", StandardCharsets.UTF_8)
            .withHeaders(Header.header("Content-type", "text/plain; charset=UTF-8"))
        );

        var request = HttpClientRequest.post("/")
            .body(HttpBody.plaintext("test-request"))
            .build();

        call(request)
            .assertCode(200)
            .assertHeader("Content-type", "text/plain; charset=UTF-8")
            .assertBody()
            .asString(StandardCharsets.UTF_8)
            .isEqualTo("test-response");

        server.verify(expectedRequest);
    }

    @Test
    protected void requests() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        for (int i = 0; i < 100; i++) {
            testHappyPath();
            // todo assert connection pool?
        }
    }

    @Test
    protected void testLargePayload() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var responseBody = new byte[1024 * 1024 * 4];
        ThreadLocalRandom.current().nextBytes(responseBody);

        server.when(request("/")).respond(response()
            .withBody(new String(responseBody, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1)
            .withHeaders(Header.header("Content-type", "text/plain; charset=ISO_8859_1"))
        );

        var request = HttpClientRequest.post("/")
            .body(HttpBody.plaintext("test-request"))
            .build();

        call(request)
            .assertCode(200)
            .assertHeader("Content-type", "text/plain; charset=ISO_8859_1")
            .assertBody()
            .isEqualTo(responseBody);
    }


    @Test
    protected void testInvalidResponse() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/");
        server.when(expectedRequest).error(error().withDropConnection(true).withResponseBytes("test respons\r\n".getBytes(StandardCharsets.UTF_8)));

        var request = HttpClientRequest.post("/")
            .body(HttpBody.plaintext("test-request"))
            .build();

        assertThatThrownBy(() -> call(request))
            .isInstanceOf(HttpClientConnectionException.class);
    }


    @Test
    protected void testTimeout() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/");
        server.when(expectedRequest).respond(response()
            .withDelay(TimeUnit.SECONDS, 2)
            .withBody("test", StandardCharsets.ISO_8859_1)
            .withHeaders(Header.header("Content-type", "text/plain; charset=ISO_8859_1"))
        );

        var request = HttpClientRequest.post("/")
            .body(HttpBody.plaintext("test-request"))
            .requestTimeout(1000)
            .build();

        assertThatThrownBy(() -> call(request))
            .isInstanceOf(HttpClientTimeoutException.class);
    }


    @Test
    protected void testRequestTimeout() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/");
        server.when(expectedRequest).respond(response()
            .withDelay(TimeUnit.MILLISECONDS, 300)
            .withBody("test", StandardCharsets.ISO_8859_1)
            .withHeaders(Header.header("Content-type", "text/plain; charset=ISO_8859_1"))
        );

        var request = HttpClientRequest.post("/")
            .body(HttpBody.plaintext("test-request"))
            .requestTimeout(200)
            .build();

        assertThatThrownBy(() -> call(request))
            .isInstanceOf(HttpClientTimeoutException.class);
    }


    @Disabled("Something in a new version of MockServer broke this test")
    @Test
    protected void testErrorOnConnectRetried() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/");
        server.when(expectedRequest, Times.once()).error(error()
            .withDropConnection(true)
        );
        server.when(expectedRequest).respond(response()
            .withBody("test", StandardCharsets.ISO_8859_1)
            .withHeaders(Header.header("Content-type", "text/plain; charset=ISO_8859_1"))
        );

        var request = HttpClientRequest.post("/")
            .body(HttpBody.plaintext("test-request"))
            .build();

        call(request);
    }


    @Test
    protected void testConnectionError() throws Exception {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);

        var request = HttpClientRequest.post("http://google.com:1488/foo/{bar}/baz")
            .templateParam("bar", "rab")
            .body(HttpBody.plaintext("test-request"))
            .build();

        var client = this.createClient(new $HttpClientConfig_ConfigValueExtractor.HttpClientConfig_Impl(Duration.ofMillis(100), Duration.ofMillis(100), null, false));


        try {
            if (client instanceof Lifecycle lifecycle) {
                lifecycle.init();
            }
            assertThatThrownBy(() -> call(client, request))
                .isInstanceOf(HttpClientConnectionException.class);
        } finally {
            if (client instanceof Lifecycle lifecycle) {
                lifecycle.release();
            }
        }
    }

    @Test
    protected void testNoResponseBody() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/")
            .withMethod(POST);
        server.when(expectedRequest).respond(response());

        var request = HttpClientRequest.post("/").build();

        call(request)
            .assertCode(200)
            .assertBody()
            .isEmpty();

        server.verify(expectedRequest);
    }

    @Test
    protected void testRequestBodyPublisherError() {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var request = HttpClientRequest.post("/")
            .body(new HttpBodyOutput() {
                @Override
                public long contentLength() {
                    return -1;
                }

                @Override
                @Nullable
                public String contentType() {
                    return null;
                }

                @Override
                public void write(OutputStream os) throws IOException {
                    throw new RuntimeException();
                }

                @Override
                public void close() throws IOException {

                }
            })
            .build();

        assertThatThrownBy(() -> call(request)
            .assertCode(200)
            .assertBody()
            .isEmpty()
        ).isInstanceOf(HttpClientEncoderException.class);
    }
}
