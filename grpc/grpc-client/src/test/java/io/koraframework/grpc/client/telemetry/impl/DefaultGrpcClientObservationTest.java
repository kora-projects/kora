package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultGrpcClientObservationTest {

    private static final MethodDescriptor.Marshaller<Object> MARSHALLER = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(Object value) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Object parse(InputStream stream) {
            return new Object();
        }
    };

    private static final MethodDescriptor<Object, Object> METHOD = MethodDescriptor.newBuilder(MARSHALLER, MARSHALLER)
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("Service/method")
        .build();

    private StatusCode spanStatusAfterClose(Status status) {
        try (var tracerProvider = SdkTracerProvider.builder().build()) {
            var span = tracerProvider.get("test").spanBuilder("Service/method").startSpan();
            var context = DefaultGrpcClientTelemetry.TelemetryContext.EMPTY;
            var logger = new DefaultGrpcClientLoggerFactory.DefaultGrpcClientLogger(NOPLogger.NOP_LOGGER, NOPLogger.NOP_LOGGER, context);
            var metrics = new DefaultGrpcClientMetricsFactory.DefaultGrpcClientMetrics(context);
            var observation = new DefaultGrpcClientObservation(METHOD, context, span, logger, metrics);

            observation.observeStart(new Metadata());
            observation.observeClose(status, new Metadata());
            observation.end();

            return ((ReadableSpan) span).toSpanData().getStatus().getStatusCode();
        }
    }

    @Test
    void nonOkStatusWithoutCauseMarksSpanAsError() {
        assertThat(spanStatusAfterClose(Status.NOT_FOUND.withDescription("missing"))).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void okStatusMarksSpanAsOk() {
        assertThat(spanStatusAfterClose(Status.OK)).isEqualTo(StatusCode.OK);
    }
}
