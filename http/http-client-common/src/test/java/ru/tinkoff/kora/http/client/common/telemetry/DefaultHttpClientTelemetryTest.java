package ru.tinkoff.kora.http.client.common.telemetry;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.StreamingHttpBodyInput;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;

class DefaultHttpClientTelemetryTest {
    @Test
    void testNoop() {
        var telemetry = new DefaultHttpClientTelemetry(null, null, null);
        var rq = Mockito.mock(HttpClientRequest.class);

        var ctx = telemetry.get(Context.clear(), rq);

        Assertions.assertThat(telemetry.isEnabled()).isFalse();
        Assertions.assertThat(ctx).isNull();
    }

    @Test
    void testMetricsWithFullBody() throws IOException {
        var metrics = Mockito.mock(HttpClientMetrics.class);
        var telemetry = new DefaultHttpClientTelemetry(null, metrics, null);
        var rq = Mockito.mock(HttpClientRequest.class);
        Mockito.when(rq.uri()).thenReturn(URI.create("http://localhost:8080/"));
        Mockito.when(rq.uriTemplate()).thenReturn("/");
        Mockito.when(rq.method()).thenReturn("POST");

        try (var rs = Mockito.mock(HttpClientResponse.class)) {
            Mockito.when(rs.body()).thenReturn(HttpBody.plaintext("test"));
            var ctx = telemetry.get(Context.clear(), rq);

            Assertions.assertThat(telemetry.isEnabled()).isTrue();
            Assertions.assertThat(ctx).isNotNull();

            try (var wrappedRs = ctx.close(rs, null)) {
                Assertions.assertThat(wrappedRs).isSameAs(rs);
            }
        }
        Mockito.verify(metrics).record(anyInt(), any(HttpResultCode.class), anyString(), anyString(), anyString(), anyString(), any(), anyLong(), any());
    }

    @Test
    void testMetricsWithStreamingBody() throws IOException {
        var metrics = Mockito.mock(HttpClientMetrics.class);
        var telemetry = new DefaultHttpClientTelemetry(null, metrics, null);
        var rq = Mockito.mock(HttpClientRequest.class);
        Mockito.when(rq.uri()).thenReturn(URI.create("http://localhost:8080/"));
        Mockito.when(rq.uriTemplate()).thenReturn("/");
        Mockito.when(rq.method()).thenReturn("POST");

        try (var rs = Mockito.mock(HttpClientResponse.class)) {
            Mockito.when(rs.body()).thenReturn(new StreamingHttpBodyInput("text/plain", 4, new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))));
            var ctx = telemetry.get(Context.clear(), rq);

            Assertions.assertThat(telemetry.isEnabled()).isTrue();
            Assertions.assertThat(ctx).isNotNull();

            try (var wrappedRs = ctx.close(rs, null)) {
                Assertions.assertThat(wrappedRs).isNotSameAs(rs);
            }
        }
        Mockito.verify(metrics).record(anyInt(), any(HttpResultCode.class), anyString(), anyString(), anyString(), anyString(), any(), anyLong(), any());
    }

    @Test
    void testLogging() throws IOException {
        var logger = Mockito.mock(HttpClientLogger.class);
        Mockito.when(logger.logResponse()).thenReturn(true);
        Mockito.when(logger.logResponseBody()).thenReturn(true);
        var telemetry = new DefaultHttpClientTelemetry(null, null, logger);
        var rq = Mockito.mock(HttpClientRequest.class);
        Mockito.when(rq.uri()).thenReturn(URI.create("http://localhost:8080/"));
        Mockito.when(rq.uriTemplate()).thenReturn("/");
        Mockito.when(rq.method()).thenReturn("POST");

        try (var rs = Mockito.mock(HttpClientResponse.class)) {
            Mockito.when(rs.body()).thenReturn(HttpBody.plaintext("test"));
            Mockito.when(rs.code()).thenReturn(200);
            var ctx = telemetry.get(Context.clear(), rq);

            Assertions.assertThat(telemetry.isEnabled()).isTrue();
            Assertions.assertThat(ctx).isNotNull();

            try (var wrappedRs = ctx.close(rs, null)) {
                Assertions.assertThat(wrappedRs).isSameAs(rs);
            }
        }
        Mockito.verify(logger).logResponse(any(), eq(HttpResultCode.SUCCESS), anyString(), anyString(), anyString(), anyString(), anyLong(), any(), eq("test"), any());
    }

    @Test
    void testLoggingWithStreamingBody() throws IOException {
        var logger = Mockito.mock(HttpClientLogger.class);
        Mockito.when(logger.logResponse()).thenReturn(true);
        Mockito.when(logger.logResponseBody()).thenReturn(true);
        var telemetry = new DefaultHttpClientTelemetry(null, null, logger);
        var rq = Mockito.mock(HttpClientRequest.class);
        Mockito.when(rq.uri()).thenReturn(URI.create("http://localhost:8080/"));
        Mockito.when(rq.uriTemplate()).thenReturn("/");
        Mockito.when(rq.method()).thenReturn("POST");

        try (var rs = Mockito.mock(HttpClientResponse.class)) {
            Mockito.when(rs.body()).thenReturn(new StreamingHttpBodyInput("text/plain", 4, new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))));
            Mockito.when(rs.code()).thenReturn(200);
            var ctx = telemetry.get(Context.clear(), rq);

            Assertions.assertThat(telemetry.isEnabled()).isTrue();
            Assertions.assertThat(ctx).isNotNull();

            try (var wrappedRs = ctx.close(rs, null)) {
                Assertions.assertThat(wrappedRs).isNotSameAs(rs);
            }
        }
        Mockito.verify(logger).logResponse(any(), eq(HttpResultCode.SUCCESS), anyString(), anyString(), anyString(), anyString(), anyLong(), any(), eq("test"), any());
    }
}
