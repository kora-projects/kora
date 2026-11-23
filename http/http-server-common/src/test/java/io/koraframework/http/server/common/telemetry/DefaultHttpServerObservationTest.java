package io.koraframework.http.server.common.telemetry;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerLoggerFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerMetricsFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerObservation;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultHttpServerObservationTest {

    private final DefaultHttpServerLoggerFactory.DefaultHttpServerLogger logger = mock(DefaultHttpServerLoggerFactory.DefaultHttpServerLogger.class);
    private final DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics metrics = mock(DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics.class);
    private final HttpServerRequest request = mock(HttpServerRequest.class);
    private final Span span = mock(Span.class);
    private final DefaultHttpServerObservation observation = new DefaultHttpServerObservation(DefaultHttpServerTelemetry.TelemetryContext.EMPTY, logger, metrics, request, System.nanoTime(), span);

    {
        when(request.pathTemplate()).thenReturn("/test");
    }

    @Test
    void returnedClientErrorDoesNotMarkSpanAsError() {
        observation.observeResponse(HttpServerResponse.of(404));
        observation.end();

        verify(span, never()).setStatus(StatusCode.ERROR);
        verify(metrics).recordEnd(same(request), argThat(rs -> rs.code() == 404), isNull(), anyLong());
        verify(span).end();
    }

    @Test
    void thrownClientErrorDoesNotMarkSpanAsError() {
        var error = HttpServerResponseException.of(404, "not found");
        observation.observeError(error);
        observation.observeResponse(error);
        observation.end();

        verify(span, never()).setStatus(StatusCode.ERROR);
        verify(metrics).recordEnd(same(request), argThat(rs -> rs.code() == 404), isNull(), anyLong());
        verify(logger).logResponse(same(request), any(), any(), anyLong(), any(), any(), same(error));
        verify(span).end();
    }

    @Test
    void thrownServerErrorMarksSpanAsError() {
        var error = HttpServerResponseException.of(503, "unavailable");
        observation.observeError(error);
        observation.observeResponse(error);
        observation.end();

        verify(span, atLeastOnce()).setStatus(StatusCode.ERROR);
        verify(metrics).recordEnd(same(request), argThat(rs -> rs.code() == 503), same(error), anyLong());
        verify(span).end();
    }
}
