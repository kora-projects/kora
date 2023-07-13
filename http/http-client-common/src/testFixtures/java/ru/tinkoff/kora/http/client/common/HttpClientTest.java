package ru.tinkoff.kora.http.client.common;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockserver.matchers.Times;
import org.mockserver.model.Header;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.http.client.common.interceptor.TelemetryInterceptor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.telemetry.DefaultHttpClientTelemetry;
import ru.tinkoff.kora.opentelemetry.module.http.client.OpentelemetryHttpClientTracer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static ru.tinkoff.kora.http.common.HttpMethod.POST;

@TestMethodOrder(MethodOrderer.Random.class)
public abstract class HttpClientTest extends HttpClientTestBase {
    @ParameterizedTest
    @EnumSource
    protected void testHappyPath(CallType type) {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/")
            .withMethod(POST)
            .withHeader("traceparent", "00-" + rootSpan.getSpanContext().getTraceId() + ".*")
            .withHeader("Content-Type", "text/plain; charset=UTF-8")
            .withBody("test-request", StandardCharsets.UTF_8);
        server.when(expectedRequest).respond(response()
            .withBody("test-response", StandardCharsets.UTF_8)
            .withHeaders(Header.header("Content-type", "text/plain; charset=UTF-8"))
        );
        doNothing().when(this.logger).logRequest(any(), any(), any(), any(), any(), any());
        doNothing().when(this.logger).logResponse(any(), any(), anyLong(), any(), any(), any(), any(), any());
        when(this.logger.logRequest()).thenReturn(true);
        when(this.logger.logRequestHeaders()).thenReturn(true);
        when(this.logger.logRequestBody()).thenReturn(true);
        when(this.logger.logResponse()).thenReturn(true);
        when(this.logger.logResponseHeaders()).thenReturn(true);
        when(this.logger.logRequestBody()).thenReturn(true);

        var request = HttpClientRequest.post("/")
            .header("content-type", "text/plain; charset=UTF-8")
            .body("test-request".getBytes(StandardCharsets.UTF_8))
            .build();

        call(type, request)
            .assertCode(200)
            .assertHeader("Content-type", "text/plain; charset=UTF-8")
            .assertBody()
            .asString(StandardCharsets.UTF_8)
            .isEqualTo("test-response");

        server.verify(expectedRequest);
        var authority = "localhost:" + server.getPort();
        verify(this.logger).logRequest(eq(authority), eq(POST), eq("POST /"), eq("http://" + authority + "/"), ArgumentMatchers.argThat(a ->
                a.getFirst("traceparent").startsWith("00-" + rootSpan.getSpanContext().getTraceId())
                && !a.getFirst("traceparent").contains(rootSpan.getSpanContext().getSpanId())
                && a.getFirst("content-type").equals("text/plain; charset=UTF-8")),
            eq("test-request"));
        verify(this.metrics).record(eq(200), ArgumentMatchers.longThat(l -> l > 0), eq("POST"), eq("localhost"), eq("http"), eq("/"));
    }

    @ParameterizedTest
    @EnumSource
    protected void requests(CallType type) {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        for (int i = 0; i < 100; i++) {
            testHappyPath(type);
            Mockito.clearInvocations(metrics, logger);
            // todo assert connection pool?
        }
    }

    @ParameterizedTest
    @EnumSource
    protected void testLargePayload(CallType type) {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var responseBody = new byte[1024 * 1024 * 4];
        ThreadLocalRandom.current().nextBytes(responseBody);

        server.when(request("/")).respond(response()
            .withBody(new String(responseBody, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1)
            .withHeaders(Header.header("Content-type", "text/plain; charset=ISO_8859_1"))
        );

        var request = HttpClientRequest.post("/")
            .header("content-type", "text/plain; charset=UTF-8")
            .body("test-request".getBytes(StandardCharsets.UTF_8))
            .build();

        call(type, request)
            .assertCode(200)
            .assertHeader("Content-type", "text/plain; charset=ISO_8859_1")
            .assertBody()
            .isEqualTo(responseBody);
    }

    @ParameterizedTest
    @EnumSource
    protected void testInvalidResponse(CallType type) {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/");
        server.when(expectedRequest).error(error().withDropConnection(true).withResponseBytes("test respons\r\n".getBytes(StandardCharsets.UTF_8)));

        var request = HttpClientRequest.post("/")
            .header("content-type", "text/plain; charset=UTF-8")
            .body("test-request".getBytes(StandardCharsets.UTF_8))
            .build();

        assertThatThrownBy(() -> call(type, request))
            .isInstanceOf(HttpClientConnectionException.class);
    }

    @ParameterizedTest
    @EnumSource
    protected void testTimeout(CallType type) {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/");
        server.when(expectedRequest).respond(response()
            .withDelay(TimeUnit.SECONDS, 2)
            .withBody("test", StandardCharsets.ISO_8859_1)
            .withHeaders(Header.header("Content-type", "text/plain; charset=ISO_8859_1"))
        );

        var request = HttpClientRequest.post("/")
            .header("content-type", "text/plain; charset=UTF-8")
            .body("test-request".getBytes(StandardCharsets.UTF_8))
            .requestTimeout(1000)
            .build();

        assertThatThrownBy(() -> call(type, request))
            .isInstanceOf(HttpClientTimeoutException.class);

        verify(this.metrics).record(
            eq(-1),
            AdditionalMatchers.gt(Duration.ofMillis(500).toNanos()),
            eq("POST"),
            eq("localhost"),
            eq("http"),
            eq("/")
        );
    }


    @ParameterizedTest
    @EnumSource
    protected void testRequestTimeout(CallType type) {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/");
        server.when(expectedRequest).respond(response()
            .withDelay(TimeUnit.MILLISECONDS, 300)
            .withBody("test", StandardCharsets.ISO_8859_1)
            .withHeaders(Header.header("Content-type", "text/plain; charset=ISO_8859_1"))
        );

        var request = HttpClientRequest.post("/")
            .header("content-type", "text/plain; charset=UTF-8")
            .body("test-request".getBytes(StandardCharsets.UTF_8))
            .requestTimeout(200)
            .build();

        assertThatThrownBy(() -> call(type, request))
            .isInstanceOf(HttpClientTimeoutException.class);

        verify(this.metrics).record(
            eq(-1),
            AdditionalMatchers.gt(Duration.ofMillis(200).toNanos()),
            eq("POST"),
            eq("localhost"),
            eq("http"),
            eq("/")
        );
    }

    @ParameterizedTest
    @EnumSource
    @Disabled("Something in a new version of MockServer broke this test")
    protected void testErrorOnConnectRetried(CallType type) {
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
            .header("content-type", "text/plain; charset=UTF-8")
            .body("test-request".getBytes(StandardCharsets.UTF_8))
            .build();

        call(type, request);

        verify(this.metrics).record(
            eq(200),
            AdditionalMatchers.lt(Duration.ofMillis(500).toNanos()),
            eq("POST"),
            eq("localhost"),
            eq("http"),
            eq("/")
        );
    }

    @ParameterizedTest
    @EnumSource
    protected void testConnectionError(CallType type) {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);

        var request = HttpClientRequest.post("http://google.com:1488/")
            .header("content-type", "text/plain; charset=UTF-8")
            .body("test-request".getBytes(StandardCharsets.UTF_8))
            .build();

        var base = this.createClient(new $HttpClientConfig_ConfigValueExtractor.HttpClientConfig_Impl(Duration.ofMillis(100), Duration.ofMillis(100), null, false));
        var client = base
            .with(new TelemetryInterceptor(new DefaultHttpClientTelemetry(
                new OpentelemetryHttpClientTracer(this.tracer),
                this.metrics,
                this.logger
            )));

        try {
            if (base instanceof Lifecycle lifecycle) {
                lifecycle.init().block();
            }
            assertThatThrownBy(() -> call(client, type, request))
                .isInstanceOf(HttpClientConnectionException.class);
        } finally {
            if (base instanceof Lifecycle lifecycle) {
                lifecycle.release().block();
            }
        }

        verify(this.metrics).record(
            eq(-1),
            ArgumentMatchers.longThat(l -> l > 0),
            eq("POST"),
            eq("google.com"),
            eq("http"),
            eq("/")
        );
    }

    @ParameterizedTest
    @EnumSource
    protected void testNoResponseBody(CallType type) {
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.OFF);
        var expectedRequest = request("/")
            .withMethod(POST)
            .withHeader("traceparent", "00-" + rootSpan.getSpanContext().getTraceId() + ".*");
        server.when(expectedRequest).respond(response());

        var request = HttpClientRequest.post("/").build();

        call(type, request)
            .assertCode(200)
            .assertBody()
            .isEmpty();

        server.verify(expectedRequest);
    }
}
