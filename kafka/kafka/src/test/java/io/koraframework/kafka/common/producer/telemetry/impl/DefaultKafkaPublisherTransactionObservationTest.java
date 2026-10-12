package io.koraframework.kafka.common.producer.telemetry.impl;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultKafkaPublisherTransactionObservationTest {

    private static final AttributeKey<String> OPERATION = AttributeKey.stringKey("messaging.operation.name");

    private final List<SpanData> spans = new CopyOnWriteArrayList<>();
    private final List<String> logs = new CopyOnWriteArrayList<>();

    @Test
    void abortedTransactionIsReportedAsRollback() {
        var observation = observation();
        observation.observeRollback(null);
        observation.end();

        var span = spans.getFirst();
        assertThat(span.getAttributes().get(OPERATION)).isEqualTo("rollback");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(logs).containsExactly("rollbackStart", "rollbackEnd");
    }

    @Test
    void committedTransactionIsReportedAsCommit() {
        var observation = observation();
        observation.observeCommit();
        observation.end();

        var span = spans.getFirst();
        assertThat(span.getAttributes().get(OPERATION)).isEqualTo("commit");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(logs).containsExactly("commitStart", "end:null");
    }

    private DefaultKafkaPublisherTransactionObservation observation() {
        var exporter = new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> s) {
                spans.addAll(s);
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
        var tracer = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()
            .get("test");
        var context = DefaultKafkaPublisherTelemetry.TelemetryContext.EMPTY;
        var logger = new DefaultKafkaPublisherLoggerFactory.DefaultKafkaPublisherLogger(NOPLogger.NOP_LOGGER, value -> "***", context) {
            @Override
            public void logTxCommitStart() {
                logs.add("commitStart");
            }

            @Override
            public void logTxRollbackStart(@Nullable Throwable error) {
                logs.add("rollbackStart");
            }

            @Override
            public void logTxRollbackEnd() {
                logs.add("rollbackEnd");
            }

            @Override
            public void logTxEnd(@Nullable Throwable error) {
                logs.add("end:" + error);
            }
        };
        return new DefaultKafkaPublisherTransactionObservation(context, logger, tracer.spanBuilder("producer transaction").startSpan());
    }
}
