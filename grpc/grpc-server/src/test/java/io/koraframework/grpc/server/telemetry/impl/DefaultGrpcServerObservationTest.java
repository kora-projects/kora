package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultGrpcServerObservationTest {

    private final List<@Nullable Object> loggedRequests = new ArrayList<>();
    private final List<@Nullable Object> loggedResponses = new ArrayList<>();

    private final DefaultGrpcServerLoggerFactory.DefaultGrpcServerLogger logger = new DefaultGrpcServerLoggerFactory.DefaultGrpcServerLogger(DefaultGrpcServerTelemetry.TelemetryContext.EMPTY, NOPLogger.NOP_LOGGER, NOPLogger.NOP_LOGGER) {
        @Override
        public void logRequest(String service, String method, Metadata requestHeaders, @Nullable Object requestMessage) {
            loggedRequests.add(requestMessage);
        }

        @Override
        public void logResponse(String service, String method, @Nullable Status status, @Nullable Throwable error, @Nullable Object responseMessage, long processingTimeNanos) {
            loggedResponses.add(responseMessage);
        }
    };

    private DefaultGrpcServerObservation observation(Span span) {
        var metrics = new DefaultGrpcServerMetricsFactory.DefaultGrpcServerMetrics(DefaultGrpcServerTelemetry.TelemetryContext.EMPTY);
        return new DefaultGrpcServerObservation(DefaultGrpcServerTelemetry.TelemetryContext.EMPTY, "Service", "method", new Metadata(), span, logger, metrics);
    }

    @Test
    void requestLogGetsRequestMessageAndResponseLogGetsResponseMessage() {
        var observation = observation(Span.getInvalid());

        observation.observeStart();
        observation.observeReceiveMessage("REQUEST");
        observation.observeHalfClosed();
        observation.observeSendMessage("RESPONSE");
        observation.observeClose(Status.OK, new Metadata());
        observation.end();

        assertThat(loggedRequests).containsExactly("REQUEST");
        assertThat(loggedResponses).containsExactly("RESPONSE");
    }

    @Test
    void requestIsLoggedOnceWhenNoMessageWasReceived() {
        var observation = observation(Span.getInvalid());

        observation.observeStart();
        observation.observeError(new IllegalStateException());
        observation.end();

        assertThat(loggedRequests).containsExactly((Object) null);
        assertThat(loggedResponses).containsExactly((Object) null);
    }

    @Test
    void nonOkStatusWithoutCauseMarksSpanAsError() {
        try (var tracerProvider = SdkTracerProvider.builder().build()) {
            var span = tracerProvider.get("test").spanBuilder("Service/method").startSpan();
            var observation = observation(span);

            observation.observeStart();
            observation.observeReceiveMessage("REQUEST");
            observation.observeClose(Status.NOT_FOUND.withDescription("missing"), new Metadata());
            observation.end();

            assertThat(((ReadableSpan) span).toSpanData().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        }
    }

    @Test
    void okStatusMarksSpanAsOk() {
        try (var tracerProvider = SdkTracerProvider.builder().build()) {
            var span = tracerProvider.get("test").spanBuilder("Service/method").startSpan();
            var observation = observation(span);

            observation.observeStart();
            observation.observeClose(Status.OK, new Metadata());
            observation.end();

            assertThat(((ReadableSpan) span).toSpanData().getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        }
    }
}
