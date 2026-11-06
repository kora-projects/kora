package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultSoapClientObservationTest {

    @Test
    void testSpanEndsWithTracerClock() {
        var span = Mockito.mock(Span.class, Mockito.RETURNS_SELF);
        var observation = new DefaultSoapClientObservation(
            Mockito.mock(SoapEnvelope.class),
            DefaultSoapClientTelemetry.TelemetryContext.EMPTY,
            span,
            Mockito.mock(DefaultSoapClientLoggerFactory.DefaultSoapClientLogger.class),
            Mockito.mock(DefaultSoapClientMetricsFactory.DefaultSoapClientMetrics.class)
        );

        observation.end();

        // Span.end(long, TimeUnit) expects an epoch timestamp, System.nanoTime() is not one
        verify(span, never()).end(anyLong(), any());
        verify(span).end();
    }
}
