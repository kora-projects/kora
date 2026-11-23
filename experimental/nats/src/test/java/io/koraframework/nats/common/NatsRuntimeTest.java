package io.koraframework.nats.common;

import io.koraframework.nats.common.consumer.*;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerTelemetryFactory;
import io.koraframework.nats.common.exceptions.NatsSerializationException;
import io.koraframework.nats.common.producer.AbstractNatsPublisher;
import io.koraframework.nats.common.producer.GeneratedNatsPublisher;
import io.koraframework.nats.common.producer.NatsPublishCallback;
import io.koraframework.nats.common.producer.NatsPublisherConfig;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetryFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.*;
import io.nats.client.impl.Headers;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NatsRuntimeTest {
    private final NatsModule serializers = new NatsModule() {
    };

    private Message message(byte[] bytes) {
        return io.nats.client.impl.NatsMessage.builder().subject("events").data(bytes).build();
    }

    @Test
    void scalarSerializersRoundTrip() {
        var headers = new Headers();
        assertThat(serializers.stringNatsDeserializer().deserialize(message(serializers.stringNatsSerializer().serialize("s", headers, "привет")))).isEqualTo("привет");
        assertThat(serializers.shortNatsDeserializer().deserialize(message(serializers.shortNatsSerializer().serialize("s", headers, (short) -3)))).isEqualTo((short) -3);
        assertThat(serializers.integerNatsDeserializer().deserialize(message(serializers.integerNatsSerializer().serialize("s", headers, -42)))).isEqualTo(-42);
        assertThat(serializers.longNatsDeserializer().deserialize(message(serializers.longNatsSerializer().serialize("s", headers, Long.MAX_VALUE)))).isEqualTo(Long.MAX_VALUE);
        assertThat(serializers.floatNatsDeserializer().deserialize(message(serializers.floatNatsSerializer().serialize("s", headers, 1.25F)))).isEqualTo(1.25F);
        assertThat(serializers.doubleNatsDeserializer().deserialize(message(serializers.doubleNatsSerializer().serialize("s", headers, -1.5D)))).isEqualTo(-1.5D);
        var uuid = UUID.randomUUID();
        assertThat(serializers.uuidNatsDeserializer().deserialize(message(serializers.uuidNatsSerializer().serialize("s", headers, uuid)))).isEqualTo(uuid);
        assertThat(serializers.integerNatsSerializer().serialize("s", headers, 0x01020304)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void voidCodecUsesEmptyNativePayloadAndIgnoresReceivedBody() {
        var encoded = serializers.voidNatsSerializer().serialize("events", new Headers(), null);
        assertThat(encoded).isNull();
        assertThat(message(encoded).getData()).isEmpty();
        assertThat(serializers.voidNatsDeserializer().deserialize(message(new byte[]{1, 2}))).isNull();
    }

    @Test
    void publisherDelegatesLifecycleToTaggedClient() throws Exception {
        var client = mock(NatsClient.class);
        GeneratedNatsPublisher publisher = new Publisher(client, NatsPublisherConfig.Mode.CORE);
        publisher.init();
        publisher.release();
        verify(client).init();
        verify(client).release();
    }

    @Test
    void missingEmptyOrBlankSubjectsAreRejected() {
        var config = TestSupport.listener(null, null, null);
        when(config.subjects()).thenReturn(java.util.List.of());
        assertThatThrownBy(() -> container(config)).hasMessageContaining("at least one");
        when(config.subjects()).thenReturn(null);
        assertThatThrownBy(() -> container(config)).hasMessageContaining("at least one");
        when(config.subjects()).thenReturn(java.util.List.of("one", " "));
        assertThatThrownBy(() -> container(config)).hasMessageContaining("non-blank subject");
    }

    @Test
    void startupTimeoutStopsRetryingWorkers() throws Exception {
        var client = mock(NatsClient.class);
        doThrow(new java.io.IOException("offline")).when(client).ensureConnected();
        var config = TestSupport.listener("subject", null, null);
        when(config.initializationFailTimeout()).thenReturn(Duration.ofMillis(100));
        when(config.backoffTimeout()).thenReturn(Duration.ofMillis(10));
        var container = new NatsConsumerContainer<>("timeout", client, config, serializers.stringNatsDeserializer(),
            (NatsMessageHandler<String>) (_poll, record) -> {
            }, null, TestSupport.CONSUMER_TELEMETRY);
        assertThatThrownBy(container::init).isInstanceOf(java.util.concurrent.TimeoutException.class)
            .hasMessageContaining("initializationFailTimeout");
        assertThat(container.probe()).isNotNull();
        container.release();
        var workers = NatsConsumerContainer.class.getDeclaredField("liveWorkers");
        workers.setAccessible(true);
        assertThat(((AtomicInteger) workers.get(container)).get()).isZero();
    }

    @Test
    void startupWithoutTimeoutReturnsBeforeConnectionAndRecovers() throws Exception {
        var connecting = new java.util.concurrent.CountDownLatch(1);
        var proceed = new java.util.concurrent.CountDownLatch(1);
        var client = mock(NatsClient.class);
        doAnswer(invocation -> {
            connecting.countDown();
            proceed.await();
            return null;
        }).when(client).ensureConnected();
        var connection = mock(Connection.class);
        var subscription = mock(Subscription.class);
        when(client.connection()).thenReturn(connection);
        when(connection.subscribe("subject")).thenReturn(subscription);
        when(subscription.isActive()).thenReturn(true);
        when(subscription.getSubject()).thenReturn("subject");
        when(subscription.drain(any(Duration.class))).thenReturn(CompletableFuture.completedFuture(true));
        when(subscription.nextMessage(any(Duration.class))).thenAnswer(invocation -> {
            java.util.concurrent.TimeUnit.MILLISECONDS.sleep(10);
            return null;
        });
        var config = TestSupport.listener("subject", null, null);
        when(config.initializationFailTimeout()).thenReturn(null);
        var container = new NatsConsumerContainer<>("background", client, config, serializers.stringNatsDeserializer(),
            (NatsMessageHandler<String>) (_poll, record) -> {
            }, null, TestSupport.CONSUMER_TELEMETRY);
        container.init();
        try {
            assertThat(connecting.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(container.probe()).isNotNull();
            proceed.countDown();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> container.probe() == null);
        } finally {
            proceed.countDown();
            container.release();
        }
    }

    @Test
    void workerRetryRemovesOldSubscriptionAndRestoresReadiness() throws Exception {
        var client = mock(NatsClient.class);
        var connection = mock(Connection.class);
        var first = mock(Subscription.class);
        var recovered = mock(Subscription.class);
        when(client.connection()).thenReturn(connection);
        when(connection.subscribe("subject")).thenReturn(first, recovered);
        for (var subscription : java.util.List.of(first, recovered)) {
            when(subscription.isActive()).thenReturn(true);
            when(subscription.getSubject()).thenReturn("subject");
            when(subscription.drain(any(Duration.class))).thenReturn(CompletableFuture.completedFuture(true));
            when(subscription.nextMessage(any(Duration.class))).thenAnswer(invocation -> {
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(10);
                return null;
            });
        }
        var telemetry = mock(io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetry.class);
        when(telemetry.observeOperation(anyString())).thenReturn(TestSupport.CONSUMER_TELEMETRY.observeOperation("test"));
        when(telemetry.observePoll()).thenThrow(new IllegalStateException("poll telemetry failure"))
            .thenReturn(TestSupport.CONSUMER_TELEMETRY.observePoll());
        var config = TestSupport.listener("subject", null, null);
        when(config.backoffTimeout()).thenReturn(Duration.ofMillis(10));
        var container = new NatsConsumerContainer<>("retry", client, config, serializers.stringNatsDeserializer(),
            (NatsMessageHandler<String>) (_poll, record) -> {
            }, null, telemetry);
        container.init();
        try {
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                verify(first).unsubscribe();
                assertThat(container.probe()).isNull();
            });
            verify(connection, times(2)).subscribe("subject");
        } finally {
            container.release();
        }
    }

    @Test
    void byteBufferSerializerPreservesPositionAndLimit() {
        var buffer = ByteBuffer.wrap(new byte[]{0, 1, 2, 3});
        buffer.position(1).limit(3);
        assertThat(serializers.byteBufferNatsSerializer().serialize("s", new Headers(), buffer)).containsExactly(1, 2);
        assertThat(buffer.position()).isEqualTo(1);
        assertThat(buffer.limit()).isEqualTo(3);
    }

    @Test
    void malformedScalarFailsInsteadOfTruncating() {
        assertThatThrownBy(() -> serializers.integerNatsDeserializer().deserialize(message(new byte[5])))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("4 bytes");
    }

    @Test
    void lazyDecodeRunsOnceAndPreservesRawMessage() {
        var count = new AtomicInteger();
        var original = message("value".getBytes(StandardCharsets.UTF_8));
        var record = new NatsMessage<>(original, msg -> {
            count.incrementAndGet();
            return new String(msg.getData(), StandardCharsets.UTF_8);
        });
        assertThat(count.get()).isZero();
        assertThat(record.message()).isSameAs(original);
        assertThat(record.value()).isEqualTo("value");
        assertThat(record.value()).isEqualTo("value");
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void lazyDecodeCachesErrorAndPreservesRawMessage() {
        var original = message(new byte[0]);
        var count = new AtomicInteger();
        var record = new NatsMessage<String>(original, msg -> {
            count.incrementAndGet();
            throw new IllegalArgumentException("bad data");
        });
        assertThatThrownBy(record::value).isInstanceOf(NatsSerializationException.class).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(record::value).isInstanceOf(NatsSerializationException.class);
        assertThat(record.message()).isSameAs(original);
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void batchIsImmutableSnapshot() {
        var source = new java.util.ArrayList<NatsMessage<String>>();
        source.add(new NatsMessage<>(message(new byte[0]), msg -> ""));
        var batch = new NatsMessages<>(source);
        source.clear();
        assertThat(batch.count()).isEqualTo(1);
        assertThatThrownBy(() -> batch.messages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void parallelBroadcastIsRejected() {
        var config = TestSupport.listener("subject", null, null);
        when(config.threads()).thenReturn(2);
        assertThatThrownBy(() -> container(config)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("require a queue");
    }

    @Test
    void parallelEphemeralPullIsRejected() {
        var config = TestSupport.listener("subject", null, TestSupport.jetStream("stream", null));
        when(config.threads()).thenReturn(2);
        assertThatThrownBy(() -> container(config)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shared durable");
    }

    @Test
    void bindWithoutDurableIsRejected() {
        var js = TestSupport.jetStream("stream", null);
        when(js.bind()).thenReturn(true);
        assertThatThrownBy(() -> container(TestSupport.listener("subject", null, js)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bind requires durable");
    }

    @Test
    void disabledListenerStartsNoConnection() throws Exception {
        var config = TestSupport.listener("subject", null, null);
        when(config.threads()).thenReturn(0);
        var client = mock(NatsClient.class);
        var container = new NatsConsumerContainer<>("disabled", client, config, serializers.stringNatsDeserializer(),
            (NatsMessageHandler<String>) (_poll, record) -> {
            }, null, TestSupport.CONSUMER_TELEMETRY);
        container.init();
        container.release();
        verifyNoInteractions(client);
    }

    @Test
    void readinessIsDisabledByDefaultAndCanBeEnabledForEachConnection() {
        var defaults = new NatsConnectionConfig() {
        };
        assertThat(defaults.readinessProbe()).isFalse();
        var listenerDefaults = mock(NatsListenerConfig.class, CALLS_REAL_METHODS);
        assertThat(listenerDefaults.readinessProbe()).isFalse();
        assertThat(listenerDefaults.initializationFailTimeout()).isNull();
        var disabledProbe = new NatsClient(defaults, null);
        assertThat(disabledProbe.probe()).isNull();

        var enabledProbe = new NatsClient(new NatsConnectionConfig() {
            @Override
            public boolean readinessProbe() {
                return true;
            }
        }, null);
        assertThat(enabledProbe.probe()).isNotNull();

        var disabledClient = new NatsClient(new NatsConnectionConfig() {
            @Override
            public boolean readinessProbe() {
                return true;
            }
        }, null, false);
        assertThat(disabledClient.probe()).isNull();
    }

    @Test
    void listenerReadinessCanBeDisabledIndependentlyOfStartupTimeout() throws Exception {
        var config = TestSupport.listener("subject", null, null);
        when(config.readinessProbe()).thenReturn(false);
        when(config.initializationFailTimeout()).thenReturn(Duration.ofMillis(100));
        when(config.backoffTimeout()).thenReturn(Duration.ofMillis(10));
        var client = mock(NatsClient.class);
        doThrow(new java.io.IOException("offline")).when(client).ensureConnected();
        var container = new NatsConsumerContainer<>("disabled-probe", client, config, serializers.stringNatsDeserializer(),
            (NatsMessageHandler<String>) (_poll, record) -> {
            }, null, TestSupport.CONSUMER_TELEMETRY);
        try {
            assertThat(container.probe()).isNull();
            assertThatThrownBy(container::init).isInstanceOf(java.util.concurrent.TimeoutException.class)
                .hasMessageContaining("initializationFailTimeout");
            assertThat(container.probe()).isNull();
        } finally {
            container.release();
        }
    }

    @Test
    void zeroPollTimeoutIsRejected() {
        var config = TestSupport.listener("subject", null, null);
        when(config.pollTimeout()).thenReturn(Duration.ZERO);
        assertThatThrownBy(() -> container(config)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pollTimeout");
    }

    private NatsConsumerContainer<String> container(NatsListenerConfig config) {
        return new NatsConsumerContainer<>("test", mock(NatsClient.class), config, serializers.stringNatsDeserializer(),
            (NatsMessageHandler<String>) (_poll, record) -> {
            }, null, TestSupport.CONSUMER_TELEMETRY);
    }

    @Test
    void corePublisherCopiesHeadersAndDoesNotCallCallbackTwice() {
        var client = mock(NatsClient.class);
        var connection = mock(Connection.class);
        when(client.connection()).thenReturn(connection);
        var publisher = new Publisher(client, NatsPublisherConfig.Mode.CORE);
        var headers = new Headers().put("custom", "original");
        var message = io.nats.client.impl.NatsMessage.builder().subject("events").headers(headers).data(new byte[0]).build();
        var callback = mock(NatsPublishCallback.class);
        doThrow(new IllegalStateException("callback error")).when(callback).onCompletion(null, null);
        assertThatThrownBy(() -> publisher.callback(message, callback)).hasMessage("callback error");
        verify(callback, times(1)).onCompletion(null, null);
        var sent = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(connection).publish(sent.capture());
        assertThat(sent.getValue().getHeaders()).isNotSameAs(headers);
        assertThat(headers.keySet()).containsExactly("custom");
    }

    @Test
    void corePublisherRejectsAcknowledgedSend() {
        var publisher = new Publisher(mock(NatsClient.class), NatsPublisherConfig.Mode.CORE);
        assertThatThrownBy(() -> publisher.acknowledged(message(new byte[0]))).hasMessageContaining("requires JETSTREAM");
    }

    @Test
    void telemetryPropagatesAndExtractsTraceContextAndRecordsOnce() {
        var registry = new SimpleMeterRegistry();
        try (var provider = SdkTracerProvider.builder().build()) {
            var tracer = provider.get("test");
            var telemetry = new DefaultNatsPublisherTelemetryFactory(tracer, registry, null, null)
                .get("publisher", "Events", TestSupport.publisherTelemetry(true, true), new java.util.Properties());
            var consumerTelemetry = new DefaultNatsConsumerTelemetryFactory(tracer, registry, null, null)
                .get("listener", "Listeners.onEvent", TestSupport.consumerTelemetry(true, true), new java.util.Properties());
            var headers = new Headers();
            var sent = telemetry.observeSend("events");
            sent.observeRecord(io.nats.client.impl.NatsMessage.builder().subject("events").headers(headers).data(new byte[0]).build());
            assertThat(headers.getFirst("traceparent")).contains(sent.span().getSpanContext().getTraceId());
            var poll = consumerTelemetry.observePoll();
            var received = poll.observeRecord(io.nats.client.impl.NatsMessage.builder().subject("events").headers(headers).data(new byte[0]).build());
            assertThat(received.span().getSpanContext().getTraceId()).isEqualTo(sent.span().getSpanContext().getTraceId());
            sent.end(null);
            sent.end(new IllegalStateException("duplicate"));
            received.end(null);
            poll.end();
            assertThat(registry.get("nats.publisher.duration").tag("operation", "publish").timer().count()).isEqualTo(1);
            assertThat(registry.get("nats.consumer.duration").tag("operation", "process").timer().count()).isEqualTo(1);
        } finally {
            registry.close();
        }
    }

    @Test
    void asyncPublishFailureCompletesExceptionally() {
        var client = mock(NatsClient.class);
        var jetStream = mock(JetStream.class);
        try {
            when(client.jetStream()).thenReturn(jetStream);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        when(jetStream.publishAsync(any(Message.class))).thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("failure")));
        var publisher = new Publisher(client, NatsPublisherConfig.Mode.JETSTREAM);
        assertThatThrownBy(() -> publisher.async(message(new byte[0])).join())
            .isInstanceOf(CompletionException.class).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asyncCompletionRunsWithObservationAndMdc() throws Exception {
        var client = mock(NatsClient.class);
        var jetStream = mock(JetStream.class);
        when(client.jetStream()).thenReturn(jetStream);
        var nativeResult = new CompletableFuture<io.nats.client.api.PublishAck>();
        when(jetStream.publishAsync(any(Message.class))).thenReturn(nativeResult);
        var publisher = new Publisher(client, NatsPublisherConfig.Mode.JETSTREAM);
        var completion = publisher.async(message(new byte[0])).thenApply(ack -> {
            assertThat(io.koraframework.common.telemetry.Observation.VALUE.isBound()).isTrue();
            assertThat(io.koraframework.common.telemetry.OpentelemetryContext.VALUE.isBound()).isTrue();
            assertThat(io.koraframework.logging.common.MDC.VALUE.isBound()).isTrue();
            return ack;
        });
        var ack = mock(io.nats.client.api.PublishAck.class);
        nativeResult.complete(ack);
        assertThat(completion.join()).isSameAs(ack);
    }

    @Test
    void fatalBatchFailureNeverAcknowledgesAndFailsReadiness() throws Exception {
        var client = mock(NatsClient.class);
        var connection = mock(Connection.class);
        var js = mock(JetStream.class);
        var subscription = mock(JetStreamSubscription.class);
        when(client.connection()).thenReturn(connection);
        when(client.jetStream()).thenReturn(js);
        when(js.subscribe(eq("subject"), any(PullSubscribeOptions.class))).thenReturn(subscription);
        when(subscription.isActive()).thenReturn(true);
        var consumerInfo = mock(io.nats.client.api.ConsumerInfo.class);
        when(consumerInfo.getConsumerConfiguration()).thenReturn(
            io.nats.client.api.ConsumerConfiguration.builder().ackPolicy(io.nats.client.api.AckPolicy.Explicit).build());
        when(subscription.getConsumerInfo()).thenReturn(consumerInfo);
        var nativeMessage = mock(Message.class);
        when(nativeMessage.getSubject()).thenReturn("subject");
        when(nativeMessage.isJetStream()).thenReturn(true);
        when(subscription.fetch(anyInt(), any(Duration.class))).thenReturn(java.util.List.of(nativeMessage));
        var config = TestSupport.listener("subject", null, TestSupport.jetStream("stream", "worker"));
        var container = new NatsConsumerContainer<>("fatal", client, config, serializers.stringNatsDeserializer(),
            (NatsMessagesHandler<String>) (_poll, records) -> {
                throw new AssertionError("fatal handler error");
            },
            null, TestSupport.CONSUMER_TELEMETRY);
        container.init();
        try {
            org.awaitility.Awaitility.await().dontCatchUncaughtExceptions().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                verify(nativeMessage).nakWithDelay(any(Duration.class));
                assertThat(container.probe()).isNotNull();
            });
            verify(nativeMessage, never()).ack();
            verify(nativeMessage, never()).ackSync(any(Duration.class));
        } finally {
            container.release();
        }
    }

    private static final class Publisher extends AbstractNatsPublisher {
        Publisher(NatsClient client, NatsPublisherConfig.Mode mode) {
            super("test", client, TestSupport.publisher(mode), TestSupport.PUBLISHER_TELEMETRY);
        }

        void callback(Message message, NatsPublishCallback callback) {
            publishCallback(message, null, callback);
        }

        io.nats.client.api.PublishAck acknowledged(Message message) {
            return publishAcknowledged(message, null);
        }

        CompletableFuture<io.nats.client.api.PublishAck> async(Message message) {
            return publishAsync(message, null);
        }
    }
}
