package io.koraframework.nats.common;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.nats.common.consumer.NatsMessage;
import io.koraframework.nats.common.consumer.NatsMessages;
import io.koraframework.nats.common.consumer.NatsReplies;
import io.koraframework.nats.common.consumer.telemetry.impl.*;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetryFactory;
import io.koraframework.nats.common.producer.telemetry.impl.NoopNatsPublisherTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.*;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NatsTelemetryTest {
    private static final class Registry extends SimpleMeterRegistry implements AutoCloseable {
    }

    @Test
    void consumerCloseRemovesOnlyItsOwnPendingGauges() {
        try (var registry = new Registry()) {
            var factory = new DefaultNatsConsumerTelemetryFactory(null, registry, null, null);
            var first = factory.get("listeners.first", "First", TestSupport.consumerTelemetry(false, true), new Properties());
            var second = factory.get("listeners.second", "Second", TestSupport.consumerTelemetry(false, true), new Properties());
            var publisher = new DefaultNatsPublisherTelemetryFactory(null, registry, null, null)
                .get("publisher", "Publisher", TestSupport.publisherTelemetry(false, true), new Properties());
            first.reportPending("events", 1, 8);
            second.reportPending("events", 2, 16);
            publisher.observeSend("events").onCompletion(null, null);

            first.close();
            assertThat(registry.find("nats.consumer.pending.messages").tag("config", "listeners.first").gauge()).isNull();
            assertThat(registry.get("nats.consumer.pending.messages").tag("config", "listeners.second").gauge().value()).isEqualTo(2);
            assertThat(registry.get("nats.publisher.duration").tag("operation", "publish").timer().count()).isEqualTo(1);
            second.close();
            assertThat(registry.find("nats.consumer.pending.messages").gauge()).isNull();
        }
    }


    @Test
    void consumerBodyConverterMasksJsonFieldsWithoutDecodingPayload() {
        var rules = io.koraframework.logging.common.masking.MaskingPathRules.builder()
            .mask("user.password", value -> "***").build();
        var converter = new DefaultNatsConsumerBodyConverter(List.of(
            new io.koraframework.logging.common.masking.raw.JsonDataMasker(rules)));
        @SuppressWarnings("unchecked")
        var reader = (io.koraframework.json.common.JsonReader<String>) mock(io.koraframework.json.common.JsonReader.class);
        var decoder = new io.koraframework.nats.common.consumer.deserializer.JsonNatsDeserializer<>(reader);
        var raw = "{\"user\":{\"password\":\"secret\",\"name\":\"Alice\"},\"password\":\"visible\"}";
        var message = io.nats.client.impl.NatsMessage.builder().subject("events").data(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)).build();
        assertThat(converter.convert(message, decoder))
            .isEqualTo("{\"user\":{\"password\":\"***\",\"name\":\"Alice\"},\"password\":\"visible\"}");
        assertThat(converter.convert(message, new NatsModule() {
        }.stringNatsDeserializer())).isEqualTo(raw);
        verifyNoInteractions(reader);
    }

    @Test
    void consumerObservationLogsWithActualDeserializerBeforeDecode() {
        var logger = mock(DefaultNatsConsumerLoggerFactory.DefaultNatsConsumerLogger.class);
        var context = mock(DefaultNatsConsumerTelemetry.TelemetryContext.class);
        var metrics = mock(DefaultNatsConsumerMetricsFactory.DefaultNatsConsumerMetrics.class);
        var message = io.nats.client.impl.NatsMessage.builder().subject("events").data(new byte[]{1}).build();
        var decoder = new NatsModule() {
        }.stringNatsDeserializer();
        var observation = new DefaultNatsConsumerRecordObservation(context, logger, metrics, Span.getInvalid(), message);
        observation.observeDeserializer(decoder);
        observation.observeHandle();
        verify(logger).record(message, decoder);
    }

    @Test
    void disabledClientDoesNotOpenConnection() throws Exception {
        var client = new NatsClient(new NatsConnectionConfig() {
        },
            options -> options.server("nats://127.0.0.1:1"), false);
        client.init();
        assertThat(client.probe()).isNull();
        assertThatThrownBy(client::connection).hasMessage("NATS client is not initialized");
        client.release();
    }

    @Test
    void disabledFactoriesReturnIndependentNoops() {
        assertThat(new DefaultNatsPublisherTelemetryFactory(null, null, null, null)
            .get("p", "Publisher", TestSupport.publisherTelemetry(false, false), new Properties())).isSameAs(NoopNatsPublisherTelemetry.INSTANCE);
        assertThat(new DefaultNatsConsumerTelemetryFactory(null, null, null, null)
            .get("c", "Listener", TestSupport.consumerTelemetry(false, false), new Properties())).isSameAs(NoopNatsConsumerTelemetry.INSTANCE);
    }

    @Test
    void pollRecordDecodeAndAckFailuresAreObserved() throws Exception {
        try (var registry = new Registry(); var traces = SdkTracerProvider.builder().build()) {
            var telemetry = new DefaultNatsConsumerTelemetryFactory(traces.get("test"), registry, null, null)
                .get("cluster-a.listener", "Events.onEvent", TestSupport.consumerTelemetry(true, true), new Properties());
            var poll = telemetry.observePoll();
            var message = mock(Message.class);
            when(message.getSubject()).thenReturn("events");
            when(message.getData()).thenReturn(new byte[0]);
            doThrow(new IllegalStateException("ack failed")).when(message).ack();
            var observation = poll.observeRecord(message);
            var record = new NatsMessage<String>(message, m -> {
                throw new IllegalArgumentException("decode failed");
            }, observation);
            poll.observeRecords(new NatsMessages<>(List.of(record)));
            observation.observeHandle();
            assertThatThrownBy(record::value).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(record::ack).hasMessage("ack failed");
            observation.end();
            poll.end();
            assertThat(registry.get("nats.consumer.duration").tag("operation", "poll").timer().count()).isEqualTo(1);
            assertThat(registry.get("nats.consumer.duration").tag("operation", "process").tag("error", IllegalStateException.class.getName()).timer().count()).isEqualTo(1);
            assertThat(registry.get("nats.consumer.errors").tag("operation", "ack").counter().count()).isEqualTo(1);
            telemetry.reportPending("events", 4, 12);
            telemetry.reportPending("events", 2, 6);
            telemetry.reportLag("stream", "worker", 3);
            assertThat(registry.get("nats.consumer.pending.messages").gauge().value()).isEqualTo(2);
            assertThat(registry.get("nats.consumer.lag").gauge().value()).isEqualTo(3);
            telemetry.close();
            assertThat(registry.find("nats.consumer.pending.messages").gauge()).isNull();
        }
    }

    @Test
    void configurerMayReplaceBuilderAndCallbacksKeepAllNativeArguments() {
        var listener = mock(ConnectionListener.class);
        var errorListener = mock(ErrorListener.class);
        var client = new NatsClient(new NatsConnectionConfig() {
        }, ignored ->
            new Options.Builder().server("nats://127.0.0.1:4555").connectionName("specific")
                .connectionListener(listener).errorListener(errorListener));
        assertThat(client.options().getConnectionName()).isEqualTo("specific");
        assertThat(client.options().getServers()).extracting(Object::toString).containsExactly("nats://127.0.0.1:4555");
        var connection = mock(Connection.class);
        client.options().getConnectionListener().connectionEvent(connection, ConnectionListener.Events.RECONNECTED, 12L, "details");
        verify(listener).connectionEvent(connection, ConnectionListener.Events.RECONNECTED, 12L, "details");
        client.options().getErrorListener().slowConsumerDetected(connection, null);
        verify(errorListener).slowConsumerDetected(connection, null);
        assertThat(client.options().getConnectionListener()).isSameAs(listener);
        assertThat(client.options().getErrorListener()).isSameAs(errorListener);
        assertThat(client.options().getConnectionListener().equals(client.options().getConnectionListener())).isTrue();
    }

    @Test
    void listenerReplyHasTraceContextAndFailureTelemetry() {
        try (var registry = new Registry(); var traces = SdkTracerProvider.builder().build()) {
            var telemetry = new DefaultNatsConsumerTelemetryFactory(traces.get("test"), registry, null, null)
                .get("listener", "Listener.onEvent", TestSupport.consumerTelemetry(true, true), new Properties());
            var message = io.nats.client.impl.NatsMessage.builder().subject("request").replyTo("response").headers(new Headers()).data(new byte[0]).build();
            var poll = telemetry.observePoll();
            var record = new NatsMessage<>(message, m -> "request", poll.observeRecord(message));
            var client = mock(NatsClient.class);
            var connection = mock(Connection.class);
            when(client.connection()).thenReturn(connection);
            NatsReplies.reply(record, client, "response", (subject, headers, value) -> {
                assertThat(Observation.VALUE.isBound()).isTrue();
                return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            });
            var sent = org.mockito.ArgumentCaptor.forClass(Message.class);
            verify(connection).publish(sent.capture());
            assertThat(sent.getValue().getHeaders().getFirst("traceparent")).isNotBlank();
            assertThatThrownBy(() -> NatsReplies.reply(record, client, "bad", (subject, headers, value) -> {
                throw new IllegalArgumentException("reply serialization");
            })).hasMessage("reply serialization");
            assertThat(registry.get("nats.consumer.errors").tag("operation", "reply").counter().count()).isEqualTo(1);
            record.observation().end();
            poll.end();
        }
    }
}
