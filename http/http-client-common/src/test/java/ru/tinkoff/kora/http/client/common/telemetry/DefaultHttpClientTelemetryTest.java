package ru.tinkoff.kora.http.client.common.telemetry;

import com.typesafe.config.ConfigFactory;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.config.common.extractor.*;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;
import ru.tinkoff.kora.config.hocon.HoconConfigFactory;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.impl.DefaultHttpClientLogger;
import ru.tinkoff.kora.http.client.common.telemetry.impl.DefaultHttpClientMetrics;
import ru.tinkoff.kora.http.client.common.telemetry.impl.DefaultHttpClientTelemetry;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.body.StreamingHttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultHttpClientTelemetryTest {
    private HttpClientTelemetryConfig config(String str) {
        var config = HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(str));
        var extractor = new $HttpClientTelemetryConfig_ConfigValueExtractor(
            new $HttpClientTelemetryConfig_HttpClientLoggerConfig_ConfigValueExtractor(new SetConfigValueExtractor<>(new StringConfigValueExtractor())),
            new $HttpClientTelemetryConfig_HttpClientTracingConfig_ConfigValueExtractor(new MapConfigValueExtractor<>(new StringConfigValueExtractor())),
            new $HttpClientTelemetryConfig_HttpClientMetricsConfig_ConfigValueExtractor(new DurationArrayConfigValueExtractor(new DurationConfigValueExtractor()), new MapConfigValueExtractor<>(new StringConfigValueExtractor()))
        );
        return extractor.extract(config.root());
    }

    private Tracer tracer = Mockito.mock(Tracer.class);
    private SpanBuilder spanBuilder = Mockito.mock(SpanBuilder.class, Mockito.RETURNS_SELF);
    private Span span = Mockito.mock(Span.class, Mockito.RETURNS_SELF);
    private DefaultHttpClientLogger logger = Mockito.mock(DefaultHttpClientLogger.class);
    private DefaultHttpClientMetrics metrics = Mockito.mock(DefaultHttpClientMetrics.class);

    @BeforeEach
    void setUp() {
        when(tracer.spanBuilder(any())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
    }


    @Test
    void testMetricsWithFullBody() throws IOException {
        var config = config("""
            logging.enabled = true
            tracing.enabled = true
            metrics.enabled = true
            """);
        var telemetry = new DefaultHttpClientTelemetry(config, tracer, logger, metrics);
        var rq = HttpClientRequest.of("POST", URI.create("http://localhost:8080/"), "/", HttpHeaders.of(), HttpBody.plaintext("test"), Duration.ZERO);

        var observation = telemetry.observe(rq);
        try (var trqBody = observation.observeRequest(rq).body()) {
            trqBody.write(new ByteArrayOutputStream());
        }
        var rs = new HttpClientResponse.Default(200, HttpHeaders.of(), HttpBody.plaintext("test"), () -> {});
        try (var trs = observation.observeResponse(rs)) {
            assertThat(trs).isSameAs(rs);
            trs.body().asInputStream().readAllBytes();
        }
        observation.end();

        verify(metrics).recordSuccess(same(rq), same(rs), anyLong());
    }

    @Test
    void testMetricsWithStreamingBody() throws IOException {
        var config = config("""
            logging.enabled = true
            tracing.enabled = true
            metrics.enabled = true
            """);
        var telemetry = new DefaultHttpClientTelemetry(config, tracer, logger, metrics);
        var rq = HttpClientRequest.of("POST", URI.create("http://localhost:8080/"), "/", HttpHeaders.of(), HttpBodyOutput.of("text/plain", os -> os.write("test".getBytes(StandardCharsets.UTF_8))), Duration.ZERO);

        when(logger.logResponseBody()).thenReturn(true);
        when(logger.logRequestBody()).thenReturn(true);
        var observation = telemetry.observe(rq);
        try (var trqBody = observation.observeRequest(rq).body()) {
            trqBody.write(new ByteArrayOutputStream());
        }
        var rs = new HttpClientResponse.Default(200, HttpHeaders.of(), new StreamingHttpBodyInput("text/plain", 4, new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))), () -> {});
        try (var trs = observation.observeResponse(rs)) {
            assertThat(trs).isNotSameAs(rs); // wraped telemetry
            trs.body().asInputStream().readAllBytes();
        }
        observation.end();

        verify(metrics).recordSuccess(same(rq), same(rs), anyLong());
    }
}
