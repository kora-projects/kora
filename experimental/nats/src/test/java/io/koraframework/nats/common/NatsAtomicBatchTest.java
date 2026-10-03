package io.koraframework.nats.common;

import io.koraframework.nats.common.exceptions.NatsAtomicBatchException;
import io.koraframework.nats.common.producer.*;
import io.koraframework.nats.common.producer.serializer.NatsSerializer;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NatsAtomicBatchTest {
    private final NatsClient client = mock(NatsClient.class);
    private final Connection connection = mock(Connection.class);
    private final NatsPublisherConfig publisherConfig = TestSupport.publisher(NatsPublisherConfig.Mode.JETSTREAM);
    private final NatsAtomicBatchConfig config = mock(NatsAtomicBatchConfig.class, CALLS_REAL_METHODS);

    private AtomicBatchPublisherImpl<Publisher> publisher() {
        return publisher(TestSupport.PUBLISHER_TELEMETRY);
    }

    private AtomicBatchPublisherImpl<Publisher> publisher(NatsPublisherTelemetry telemetry) {
        when(client.connection()).thenReturn(connection);
        when(config.stream()).thenReturn("EVENTS");
        try {
            var management = mock(io.nats.client.JetStreamManagement.class, RETURNS_DEEP_STUBS);
            when(client.jetStreamManagement()).thenReturn(management);
            when(management.getStreamInfo("EVENTS").getConfiguration().getAllowAtomicPublish()).thenReturn(true);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return new AtomicBatchPublisherImpl<>(client, publisherConfig, config, telemetry,
            sink -> new Publisher(client, publisherConfig, telemetry, sink));
    }

    private Message message(String body) {
        return NatsMessage.builder().subject("events.created").data(body.getBytes(StandardCharsets.UTF_8)).build();
    }

    private Message acknowledgement(Message message, int size) {
        return NatsMessage.builder().subject("reply").data(("{\"stream\":\"EVENTS\",\"seq\":" + size
            + ",\"batch\":\"" + message.getHeaders().getFirst("Nats-Batch-Id") + "\",\"count\":" + size + "}")
            .getBytes(StandardCharsets.UTF_8)).build();
    }

    private Message rejection() {
        return NatsMessage.builder().subject("reply").data("""
            {"error":{"code":400,"err_code":10071,"description":"wrong last sequence"}}
            """.getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    void commitBuffersCopiesAndPublishesOneBatch() throws Exception {
        var sent = new ArrayList<Message>();
        when(connection.request(any(Message.class), any(Duration.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            sent.add(message);
            return message.getHeaders().containsKey("Nats-Batch-Commit") ? acknowledgement(message, 2) : message("");
        });
        var publisher = publisher();
        var data = new byte[]{1, 2};
        var headers = new Headers().put("custom", "original");
        try (var batch = publisher.begin()) {
            batch.publisher().send(NatsMessage.builder().subject("events.created").headers(headers).data(data).build());
            data[0] = 9;
            headers.put("custom", "changed");
            batch.publisher().send(message("second"));
            assertThat(batch.size()).isEqualTo(2);
            verifyNoInteractions(connection);
            var ack = batch.commit();
            assertThat(ack.getBatchId()).isEqualTo(batch.id());
            assertThat(ack.getBatchSize()).isEqualTo(2);
            assertThat(batch.commit()).isSameAs(ack);
            assertThatThrownBy(() -> batch.publisher().send(message("late"))).hasMessageContaining("COMMITTED");
        }
        assertThat(sent).hasSize(2);
        assertThat(sent.getFirst().getData()).containsExactly(1, 2);
        assertThat(sent.getFirst().getHeaders().getFirst("custom")).isEqualTo("original");
        assertThat(sent.getFirst().getHeaders().getFirst("Nats-Batch-Sequence")).isEqualTo("1");
        assertThat(sent.getFirst().getHeaders().containsKey("Nats-Batch-Commit")).isFalse();
        assertThat(sent.getLast().getHeaders().getFirst("Nats-Batch-Sequence")).isEqualTo("2");
        assertThat(sent.getLast().getHeaders().getFirst("Nats-Batch-Commit")).isEqualTo("1");
        assertThat(sent).allSatisfy(message -> assertThat(message.getHeaders().getFirst("Nats-Expected-Stream")).isEqualTo("EVENTS"));
        assertThat(headers.containsKey("Nats-Batch-Id")).isFalse();
    }

    @Test
    void closingUncommittedBatchNeverPublishes() {
        var publisher = publisher();
        var batch = publisher.begin();
        batch.publisher().send(message("discard"));
        batch.close();
        batch.close();
        assertThatThrownBy(batch::commit).hasMessageContaining("ABORTED");
        verifyNoInteractions(connection);
    }

    @Test
    void callbackFailureDiscardsBatchAndPreservesCause() {
        var publisher = publisher();
        var failure = new IllegalArgumentException("application failure");
        assertThatThrownBy(() -> publisher.inBatch(events -> {
            events.send(message("discard"));
            throw failure;
        })).isSameAs(failure);
        verifyNoInteractions(connection);
    }

    @Test
    void successfulCallbackCommitsSingleMessage() throws Exception {
        when(connection.request(any(Message.class), any(Duration.class))).thenAnswer(invocation -> acknowledgement(invocation.getArgument(0), 1));
        var publisher = publisher();
        var ack = publisher.inBatch(events -> events.send(message("one")));
        assertThat(ack.getBatchSize()).isEqualTo(1);
        verify(connection, times(1)).request(any(Message.class), any(Duration.class));
    }

    @Test
    void brokerRejectionHasKnownOutcomeAndNoRetry() throws Exception {
        when(connection.request(any(Message.class), any(Duration.class))).thenReturn(message(""), rejection());
        var batch = publisher().begin();
        batch.publisher().send(message("one"));
        batch.publisher().send(message("two"));
        assertThatThrownBy(batch::commit).isInstanceOfSatisfying(NatsAtomicBatchException.class, failure -> {
            assertThat(failure.outcome()).isEqualTo(NatsAtomicBatchException.Outcome.ABORTED);
            assertThat(failure.batchId()).isEqualTo(batch.id());
            assertThat(failure.getCause()).isInstanceOf(JetStreamApiException.class);
        });
        assertThatThrownBy(batch::commit).hasMessageContaining("ABORTED");
        verify(connection, times(2)).request(any(Message.class), any(Duration.class));
        verify(connection).publish(argThat(message -> "0".equals(message.getHeaders().getFirst("Nats-Batch-Sequence"))));
    }

    @Test
    void timeoutBeforeFinalMessageAbandonsStagedBatch() throws Exception {
        when(connection.request(any(Message.class), any(Duration.class))).thenReturn(null);
        var batch = publisher().begin();
        batch.publisher().send(message("one"));
        batch.publisher().send(message("two"));
        assertThatThrownBy(batch::commit).isInstanceOfSatisfying(NatsAtomicBatchException.class,
            failure -> assertThat(failure.outcome()).isEqualTo(NatsAtomicBatchException.Outcome.ABORTED));
        verify(connection).publish(argThat(message -> "0".equals(message.getHeaders().getFirst("Nats-Batch-Sequence"))));
    }

    @Test
    void missingFinalAcknowledgementHasUnknownOutcomeAndNeverRetries() throws Exception {
        when(connection.request(any(Message.class), any(Duration.class))).thenReturn(null);
        var batch = publisher().begin();
        batch.publisher().send(message("one"));
        assertThatThrownBy(batch::commit).isInstanceOfSatisfying(NatsAtomicBatchException.class,
            failure -> assertThat(failure.outcome()).isEqualTo(NatsAtomicBatchException.Outcome.UNKNOWN));
        batch.close();
        batch.abort();
        assertThatThrownBy(batch::commit).hasMessageContaining("UNKNOWN");
        verify(connection, times(1)).request(any(Message.class), any(Duration.class));
        verify(connection, never()).publish(any(Message.class));
    }

    @Test
    void invalidFinalAcknowledgementHasUnknownOutcome() throws Exception {
        when(connection.request(any(Message.class), any(Duration.class))).thenReturn(message("{\"stream\":\"OTHER\",\"seq\":1}"));
        var batch = publisher().begin();
        batch.publisher().send(message("one"));
        assertThatThrownBy(batch::commit).isInstanceOfSatisfying(NatsAtomicBatchException.class,
            failure -> assertThat(failure.outcome()).isEqualTo(NatsAtomicBatchException.Outcome.UNKNOWN));
    }

    @Test
    void emptyBatchDoesNotPublish() {
        try (var batch = publisher().begin()) {
            assertThatThrownBy(batch::commit).hasMessageContaining("at least one message");
        }
        verifyNoInteractions(connection);
    }

    @Test
    void messageLimitAbortsPreviouslyBufferedRecords() {
        when(config.maxMessages()).thenReturn(1);
        var batch = publisher().begin();
        batch.publisher().send(message("one"));
        assertThatThrownBy(() -> batch.publisher().send(message("two"))).hasMessageContaining("maxMessages or maxBytes");
        assertThatThrownBy(batch::commit).hasMessageContaining("ABORTED");
        verifyNoInteractions(connection);
    }

    @Test
    void byteLimitRejectsOversizedMessage() {
        when(config.maxBytes()).thenReturn(1L);
        var batch = publisher().begin();
        assertThatThrownBy(() -> batch.publisher().send(message("payload"))).hasMessageContaining("maxMessages or maxBytes");
        assertThatThrownBy(batch::commit).hasMessageContaining("ABORTED");
        verifyNoInteractions(connection);
    }

    @Test
    void reservedHeadersReplyAndStreamMismatchAreRejected() {
        var publisher = publisher();
        for (var key : List.of("Nats-Batch-Id", "nats-batch-sequence", "Nats-Batch-Commit")) {
            try (var batch = publisher.begin()) {
                assertThatThrownBy(() -> batch.publisher().send(NatsMessage.builder().subject("events").headers(new Headers().put(key, "1")).build()))
                    .hasMessageContaining("headers are owned");
            }
        }
        try (var batch = publisher.begin()) {
            assertThatThrownBy(() -> batch.publisher().send(NatsMessage.builder().subject("events").replyTo("reply").build()))
                .hasMessageContaining("cannot carry replyTo");
        }
        try (var batch = publisher.begin()) {
            assertThatThrownBy(() -> batch.publisher().send(NatsMessage.builder().subject("events")
                .headers(new Headers().put("Nats-Expected-Stream", "OTHER")).build())).hasMessageContaining("different stream");
        }
        verifyNoInteractions(connection);
    }

    @Test
    void serializationFailurePoisonsBatch() {
        var batch = publisher().begin();
        batch.publisher().send(message("one"));
        var failure = new IllegalArgumentException("bad value");
        assertThatThrownBy(() -> batch.publisher().encode("two", (subject, headers, value) -> {
            throw failure;
        })).isSameAs(failure);
        assertThatThrownBy(batch::commit).hasMessageContaining("ABORTED");
        verifyNoInteractions(connection);
    }

    @Test
    void releaseDiscardsOpenBatchesAndStopsNewBatches() throws Exception {
        var publisher = publisher();
        var batch = publisher.begin();
        batch.publisher().send(message("one"));
        publisher.release();
        assertThatThrownBy(batch::commit).hasMessageContaining("ABORTED");
        assertThatThrownBy(publisher::begin).hasMessageContaining("released");
        verify(client).release();
        verifyNoInteractions(connection);
        publisher.init();
        verify(client).init();
        publisher.begin().close();
    }

    @Test
    void abortEndsRecordObservationAndBatchOperations() {
        var telemetry = mock(NatsPublisherTelemetry.class);
        var batchObservation = mock(io.koraframework.nats.common.producer.telemetry.NatsPublisherOperationObservation.class);
        var abortObservation = mock(io.koraframework.nats.common.producer.telemetry.NatsPublisherOperationObservation.class);
        var recordObservation = mock(NatsPublisherRecordObservation.class);
        when(batchObservation.span()).thenReturn(io.opentelemetry.api.trace.Span.getInvalid());
        when(abortObservation.span()).thenReturn(io.opentelemetry.api.trace.Span.getInvalid());
        when(recordObservation.span()).thenReturn(io.opentelemetry.api.trace.Span.getInvalid());
        when(telemetry.observeOperation("batch")).thenReturn(batchObservation);
        when(telemetry.observeOperation("batch.abort")).thenReturn(abortObservation);
        when(telemetry.observeSend(anyString())).thenReturn(recordObservation);
        var batch = publisher(telemetry).begin();
        batch.publisher().send(message("one"));
        batch.abort();
        batch.close();
        verify(recordObservation).onCompletion(isNull(), isA(CancellationException.class));
        verify(batchObservation).end(isA(CancellationException.class));
        verify(abortObservation).end(isNull());
    }

    @Test
    void coreModeAndInvalidLimitsAreRejected() {
        when(config.stream()).thenReturn("EVENTS");
        assertThatThrownBy(() -> new AtomicBatchPublisherImpl<>(client, TestSupport.publisher(NatsPublisherConfig.Mode.CORE), config,
            TestSupport.PUBLISHER_TELEMETRY, sink -> new Object())).hasMessageContaining("requires JETSTREAM");
        when(config.maxMessages()).thenReturn(1001);
        assertThatThrownBy(this::publisher).hasMessageContaining("maxMessages in [1, 1000]");
    }

    @Test
    void failingRecordTelemetryStillEndsRemainingRecords() {
        var telemetry = mock(NatsPublisherTelemetry.class);
        when(telemetry.observeOperation(anyString())).thenAnswer(invocation -> TestSupport.PUBLISHER_TELEMETRY.observeOperation(invocation.getArgument(0)));
        var first = mock(NatsPublisherRecordObservation.class);
        var second = mock(NatsPublisherRecordObservation.class);
        when(first.span()).thenReturn(io.opentelemetry.api.trace.Span.getInvalid());
        when(second.span()).thenReturn(io.opentelemetry.api.trace.Span.getInvalid());
        when(telemetry.observeSend(anyString())).thenReturn(first, second);
        doThrow(new IllegalStateException("telemetry failure")).when(first).onCompletion(isNull(), any());
        try (var batch = publisher(telemetry).begin()) {
            batch.publisher().send(message("one"));
            batch.publisher().send(message("two"));
            batch.abort();
        }
        verify(first).onCompletion(isNull(), any());
        verify(second).onCompletion(isNull(), any());
        verifyNoInteractions(connection);
    }

    @Test
    void batchTelemetryLinksRecordSpansAndEndsOnCommit() throws Exception {
        var spans = new java.util.concurrent.CopyOnWriteArrayList<io.opentelemetry.sdk.trace.data.SpanData>();
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        try (var provider = io.opentelemetry.sdk.trace.SdkTracerProvider.builder().addSpanProcessor(new io.opentelemetry.sdk.trace.SpanProcessor() {
            public void onStart(io.opentelemetry.context.Context parent, io.opentelemetry.sdk.trace.ReadWriteSpan span) {
            }

            public boolean isStartRequired() {
                return false;
            }

            public void onEnd(io.opentelemetry.sdk.trace.ReadableSpan span) {
                spans.add(span.toSpanData());
            }

            public boolean isEndRequired() {
                return true;
            }
        }).build()) {
            var telemetry = new io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetryFactory(
                provider.get("test"), registry, null, null).get("atomic", "Events", TestSupport.publisherTelemetry(true, true), new java.util.Properties());
            var sent = new ArrayList<Message>();
            when(connection.request(any(Message.class), any(Duration.class))).thenAnswer(invocation -> {
                Message message = invocation.getArgument(0);
                sent.add(message);
                return message.getHeaders().containsKey("Nats-Batch-Commit") ? acknowledgement(message, 2) : message("");
            });
            try (var batch = publisher(telemetry).begin()) {
                batch.publisher().send(message("one"));
                batch.publisher().send(message("two"));
                batch.commit();
                var batchSpan = spans.stream().filter(span -> span.getName().equals("batch")).findFirst().orElseThrow();
                assertThat(batchSpan.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("messaging.nats.batch.id"))).isEqualTo(batch.id());
                assertThat(batchSpan.getAttributes().get(io.opentelemetry.api.common.AttributeKey.longKey("messaging.batch.message_count"))).isEqualTo(2L);
                var records = spans.stream().filter(span -> span.getName().equals("events.created publish")).toList();
                assertThat(records).hasSize(2).allSatisfy(span -> {
                    assertThat(span.getParentSpanId()).isEqualTo(batchSpan.getSpanId());
                    assertThat(span.getTraceId()).isEqualTo(batchSpan.getTraceId());
                });
                assertThat(sent).allSatisfy(message -> assertThat(message.getHeaders().getFirst("traceparent")).contains(batchSpan.getTraceId()));
            }
            assertThat(registry.get("nats.publisher.duration").tag("operation", "batch").timer().count()).isEqualTo(1);
            assertThat(registry.get("nats.publisher.duration").tag("operation", "batch.commit").timer().count()).isEqualTo(1);
            assertThat(registry.get("nats.publisher.duration").tag("operation", "publish").timer().count()).isEqualTo(2);
        } finally {
            registry.close();
        }
    }

    private static final class Publisher extends AbstractNatsPublisher {
        private Publisher(NatsClient client, NatsPublisherConfig config, NatsPublisherTelemetry telemetry, NatsAtomicBatchSink batch) {
            super("atomic", client, config, telemetry, batch);
        }

        void send(Message message) {
            publish(message, null);
        }

        void encode(String value, NatsSerializer<String> serializer) {
            var observation = observeSend("events.created");
            try {
                var message = serializeMessage("events.created", null, new Headers(), value, serializer, observation);
                publish(message, null, observation);
            } catch (RuntimeException | Error e) {
                observation.end(e);
                throw e;
            }
        }
    }
}
