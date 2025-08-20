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
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.interceptor.TelemetryInterceptor;
import ru.tinkoff.kora.http.client.common.request.DefaultHttpClientRequest;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.telemetry.DefaultHttpClientTelemetry;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientLogger;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.opentelemetry.module.http.client.OpentelemetryHttpClientTracer;

import java.io.IOException;
import java.net.URI;

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
    private final HttpClient baseClient = this.createClient(new $HttpClientConfig_ConfigValueExtractor.HttpClientConfig_Impl(ofMillis(100), ofMillis(500000), null, false));

    private final HttpClient client = this.baseClient
        .with(new TelemetryInterceptor(new DefaultHttpClientTelemetry(
            new OpentelemetryHttpClientTracer(tracer),
            this.metrics,
            this.logger
        )))
        .with((ctx, chain, request) -> chain.process(ctx, new DefaultHttpClientRequest(
            request.method(),
            URI.create("http://localhost:" + server.getPort() + request.uri().toString()),
            request.uriTemplate(),
            request.headers(),
            request.body(),
            request.requestTimeout()
        )));

    protected abstract HttpClient createClient(HttpClientConfig config);

    @BeforeEach
    void setUp() throws Exception {
        ctx.getLogger("ROOT").setLevel(Level.OFF);
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.ALL);
        OpentelemetryContext.set(Context.current(), rootTelemetry.add(rootSpan));
        if (this.baseClient instanceof Lifecycle lifecycle) {
            lifecycle.init();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (this.baseClient instanceof Lifecycle lifecycle) {
            lifecycle.release();
        }
        server.stop();
        Context.clear();
        Mockito.clearInvocations(metrics, logger);
    }

    protected ResponseWithBody call(HttpClient client, HttpClientRequest request) {
        try (var response = client.execute(request);
             var body = response.body();
             var is = body.asInputStream()) {
            return new ResponseWithBody(response, is.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected ResponseWithBody call(HttpClientRequest request) {
        try (var response = client.execute(request);
             var body = response.body();
             var is = body.asInputStream()) {
            return new ResponseWithBody(response, is.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
