package ru.tinkoff.kora.http.client.common;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.mockserver.integration.ClientAndServer;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.client.HttpClientTestBaseKt;
import ru.tinkoff.kora.http.client.common.interceptor.RootUriInterceptor;
import ru.tinkoff.kora.http.client.common.interceptor.TelemetryInterceptor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.BlockingHttpResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.DefaultHttpClientTelemetry;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientLogger;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.opentelemetry.module.http.client.OpentelemetryHttpClientTracer;

import java.io.IOException;

import static java.time.Duration.ofMillis;

@TestMethodOrder(MethodOrderer.Random.class)
public abstract class HttpClientTestBase {
    protected static final LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    protected final ClientAndServer server = ClientAndServer.startClientAndServer(0);
    protected final HttpClientLogger logger = Mockito.mock(HttpClientLogger.class);
    protected final HttpClientMetrics metrics = Mockito.mock(HttpClientMetrics.class);
    protected final Tracer tracer = SdkTracerProvider.builder().build().tracerBuilder("kora").build();
    protected final Span rootSpan = tracer
        .spanBuilder("test")
        .setSpanKind(SpanKind.INTERNAL)
        .setNoParent()
        .startSpan();
    protected final OpentelemetryContext rootTelemetry = OpentelemetryContext.get(Context.current());
    private final HttpClient baseClient = this.createClient(new $HttpClientConfig_ConfigValueExtractor.HttpClientConfig_Impl(ofMillis(100), ofMillis(500), null, false));

    private final HttpClient client = this.baseClient
        .with(new TelemetryInterceptor(new DefaultHttpClientTelemetry(
            new OpentelemetryHttpClientTracer(tracer),
            this.metrics,
            this.logger
        )))
        .with(new RootUriInterceptor("http://localhost:" + server.getPort()));

    protected abstract HttpClient createClient(HttpClientConfig config);

    @BeforeEach
    void setUp() {
        ctx.getLogger("ROOT").setLevel(Level.OFF);
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.ALL);
        OpentelemetryContext.set(Context.current(), rootTelemetry.add(rootSpan));
        if (this.baseClient instanceof Lifecycle lifecycle) {
            lifecycle.init().block();
        }
    }

    @AfterEach
    void tearDown() {
        if (this.baseClient instanceof Lifecycle lifecycle) {
            lifecycle.release().block();
        }
        server.stop();
        Context.clear();
        Mockito.clearInvocations(metrics, logger);
    }


    protected enum CallType {
        BLOCKING, REACTIVE, KOTLIN
    }

    protected ResponseWithBody call(HttpClientTest.CallType type, HttpClientRequest request) {
        return this.call(this.client, type, request);
    }

    protected ResponseWithBody call(HttpClient client, HttpClientTest.CallType type, HttpClientRequest request) {
        return switch (type) {
            case BLOCKING -> this.callBlocking(client, request);
            case REACTIVE -> this.callReactive(client, request);
            case KOTLIN -> HttpClientTestBaseKt.call(client, request);
        };
    }

    private ResponseWithBody callReactive(HttpClient client, HttpClientRequest request) {
        try {
            return Mono.usingWhen(
                    client.execute(request),
                    response -> ReactorUtils.toByteArrayMono(response.body()).map(body -> new ResponseWithBody(response, body)),
                    HttpClientResponse::close
                )
                .block();
        } catch (Exception e) {
            var unwrapped = Exceptions.unwrap(e);
            if (unwrapped instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(unwrapped);
        }
    }

    private ResponseWithBody callBlocking(HttpClient client, HttpClientRequest request) {
        try (var response = BlockingHttpResponse.from(client.execute(request));
             var body = response.body()) {
            return new ResponseWithBody(response, body.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
