package ru.tinkoff.kora.kafka.common.containers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.LogTruncationException;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import ru.tinkoff.kora.common.util.Either;
import ru.tinkoff.kora.kafka.common.consumer.KafkaListenerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.KafkaAssignConsumerContainer;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SuppressWarnings("try")
class KafkaAssignConsumerContainerLoopTest {
    private final TopicPartition partition = new TopicPartition("topic", 0);

    @Test
    void initialPositionFailureUsesBackoff() {
        var consumer = consumer();
        when(consumer.position(partition)).thenThrow(new TimeoutException("position timeout"));
        var container = container(config(), mockTelemetry(), (records, c, allowed) -> {});
        try (var metadata = metadata(0)) {
            var started = System.nanoTime();
            container.launchPollLoop(consumer, 0, started);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(100));
            verify(consumer, never()).poll(any(Duration.class));
            verify(consumer).close();
        } finally {
            container.release();
        }
    }

    @Test
    void durationFallbackUsesEndOffsetCapturedBeforeTimestampLookup() {
        var config = config();
        when(config.offset()).thenReturn(Either.left(Duration.ofMinutes(1)));
        var consumer = consumer();
        when(consumer.endOffsets(List.of(partition))).thenReturn(Map.of(partition, 42L)).thenReturn(Map.of(partition, 43L));
        when(consumer.offsetsForTimes(any())).thenReturn(Collections.singletonMap(partition, null));
        when(consumer.position(partition)).thenReturn(42L);
        when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config, mockTelemetry(), (records, c, allowed) -> ref.get().release());
        ref.set(container);
        try (var metadata = metadata(0)) {
            container.launchPollLoop(consumer, 0, System.nanoTime());
            var calls = inOrder(consumer);
            calls.verify(consumer).endOffsets(List.of(partition));
            calls.verify(consumer).offsetsForTimes(any());
            calls.verify(consumer).seek(partition, 42L);
            verify(consumer, never()).seekToEnd(anyCollection());
        } finally {
            container.release();
        }
    }

    @Test
    void endOffsetsFailureDoesNotRestartConsumerOrLoseBatchProgress() {
        var consumer = consumer();
        var telemetry = mockTelemetry();
        when(consumer.poll(any(Duration.class))).thenReturn(records(0, 1)).thenReturn(records(1, 2));
        when(consumer.endOffsets(anyCollection())).thenThrow(new TimeoutException("end offsets timeout"));
        var values = new ArrayList<Integer>();
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config(), telemetry, (records, c, allowed) -> {
            records.forEach(record -> values.add(record.value()));
            if (values.size() == 2) ref.get().release();
        });
        ref.set(container);
        try (var metadata = metadata(0)) {
            container.launchPollLoop(consumer, 0, System.nanoTime());
            assertThat(values).containsExactly(1, 2);
            verify(consumer, times(2)).poll(any(Duration.class));
            verify(consumer, times(1)).assign(anyCollection());
        } finally {
            container.release();
        }
    }

    @Test
    void lagIsRequestedAndReportedOnlyForOwnedPartitions() {
        var config = config();
        when(config.threads()).thenReturn(2);
        var other = new TopicPartition("topic", 2);
        var consumer = consumer();
        var telemetry = mockTelemetry();
        when(consumer.poll(any(Duration.class))).thenReturn(records(0, 1));
        when(consumer.endOffsets(List.of(other, partition))).thenReturn(Map.of(partition, 1L, other, 0L));
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config, telemetry, (records, c, allowed) -> ref.get().release());
        ref.set(container);
        try (var metadata = metadata(1, 2, 0)) {
            container.launchPollLoop(consumer, 0, System.nanoTime());
            verify(consumer).assign(List.of(other, partition));
            verify(consumer).endOffsets(List.of(other, partition));
            verify(telemetry).reportLag(partition, 0L);
            verify(telemetry).reportLag(other, 0L);
            verify(telemetry, times(2)).reportLag(any(), anyLong());
        } finally {
            container.release();
        }
    }

    @Test
    void wakeupFromHandlerDoesNotSkipUnacknowledgedBatch() {
        var first = consumer();
        var second = consumer();
        when(first.poll(any(Duration.class))).thenReturn(records(0, 1));
        when(second.poll(any(Duration.class))).thenReturn(records(0, 1));
        var values = new ArrayList<Integer>();
        var failed = new AtomicBoolean();
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config(), mockTelemetry(), (records, c, allowed) -> {
            records.forEach(record -> values.add(record.value()));
            if (failed.compareAndSet(false, true)) throw new WakeupException();
            ref.get().release();
        });
        ref.set(container);
        try (var metadata = metadata(0)) {
            container.launchPollLoop(first, 0, System.nanoTime());
            container.launchPollLoop(second, 0, System.nanoTime());
            assertThat(values).containsExactly(1, 1);
            verify(second).seek(partition, 0L);
        } finally {
            container.release();
        }
    }

    @Test
    void releaseWakesBlockedPoll() throws Exception {
        var consumer = consumer();
        var polling = new CountDownLatch(1);
        var wakeup = new CountDownLatch(1);
        doAnswer(invocation -> {
            polling.countDown();
            if (!wakeup.await(5, TimeUnit.SECONDS)) throw new AssertionError("consumer was not woken up");
            throw new WakeupException();
        }).when(consumer).poll(any(Duration.class));
        doAnswer(invocation -> { wakeup.countDown(); return null; }).when(consumer).wakeup();
        var container = container(config(), mockTelemetry(), (records, c, allowed) -> {});
        var executor = Executors.newSingleThreadExecutor();
        try {
            var loop = executor.submit(() -> {
                try (var metadata = metadata(0)) {
                    container.launchPollLoop(consumer, 0, System.nanoTime());
                }
            });
            try {
                assertThat(polling.await(5, TimeUnit.SECONDS)).isTrue();
                container.release();
                loop.get(2, TimeUnit.SECONDS);
                verify(consumer).wakeup();
                verify(consumer).close();
            } finally {
                container.release();
                wakeup.countDown();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releaseInterruptsLongBackoff() throws Exception {
        var config = config();
        when(config.backoffTimeout()).thenReturn(Duration.ofSeconds(60));
        var consumer = consumer();
        var closed = new CountDownLatch(1);
        when(consumer.position(partition)).thenThrow(new TimeoutException("position timeout"));
        doAnswer(invocation -> { closed.countDown(); return null; }).when(consumer).close();
        var container = container(config, mockTelemetry(), (records, c, allowed) -> {});
        var executor = Executors.newSingleThreadExecutor();
        try {
            var loop = executor.submit(() -> {
                try (var metadata = metadata(0)) {
                    container.launchPollLoop(consumer, 0, System.nanoTime());
                }
            });
            assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
            container.release();
            loop.get(2, TimeUnit.SECONDS);
        } finally {
            container.release();
            executor.shutdownNow();
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(5)
    void invalidPartitionRetriesWithoutBlockingHealthyPartition() {
        var config = config();
        config.driverProperties().put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
        when(config.backoffTimeout()).thenReturn(Duration.ofMillis(40));
        var c = consumer();
        var healthy = new TopicPartition("topic", 1);
        var assignments = new java.util.concurrent.atomic.AtomicReference<java.util.Collection<TopicPartition>>();
        doAnswer(i -> { assignments.set(List.copyOf(i.getArgument(0))); return null; })
            .when(c).assign(anyCollection());
        var failedAt = new java.util.concurrent.atomic.AtomicLong();
        var recovered = new java.util.concurrent.atomic.AtomicBoolean();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var healthyPolls = new java.util.concurrent.atomic.AtomicInteger();
        when(c.poll(any(Duration.class))).thenAnswer(i -> {
            if (failedAt.get() == 0) {
                failedAt.set(System.nanoTime());
                throw new OffsetOutOfRangeException(Map.of(partition, 0L));
            }
            if (assignments.get().contains(partition)) {
                assertThat(System.nanoTime() - failedAt.get()).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(40));
                if (attempts.incrementAndGet() == 1) {
                    failedAt.set(System.nanoTime());
                    throw new OffsetOutOfRangeException(Map.of(partition, 0L));
                }
                assertThat(System.nanoTime() - failedAt.get()).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(80));
                recovered.set(true);
                return records(0, 1);
            }
            healthyPolls.incrementAndGet();
            return ConsumerRecords.empty();
        });
        var reference = new java.util.concurrent.atomic.AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config, mockTelemetry(), (records, consumer, allowed) -> {
            if (recovered.get()) reference.get().release();
        });
        reference.set(container);
        try (var metadata = metadata(0, 1)) {
            container.launchPollLoop(c, 0, System.nanoTime());
            assertThat(healthyPolls.get()).isPositive();
            verify(c, times(2)).assign(List.of(healthy));
            verify(c, times(2)).seek(partition, 0L);
            verify(c, times(1)).close();
        } finally {
            container.release();
        }
    }

    @Test
    void releaseInterruptsBackoffWhenEveryPartitionIsIsolated() throws Exception {
        var config = config();
        when(config.backoffTimeout()).thenReturn(Duration.ofSeconds(60));
        var c = consumer();
        when(c.poll(any(Duration.class))).thenThrow(new OffsetOutOfRangeException(Map.of(partition, 0L)));
        var isolated = new CountDownLatch(1);
        doAnswer(i -> {
            if (((java.util.Collection<?>) i.getArgument(0)).isEmpty()) isolated.countDown();
            return null;
        }).when(c).assign(anyCollection());
        var container = container(config, mockTelemetry(), (records, consumer, allowed) -> {});
        var executor = Executors.newSingleThreadExecutor();
        try {
            var loop = executor.submit(() -> {
                try (var metadata = metadata(0)) {
                    container.launchPollLoop(c, 0, System.nanoTime());
                }
            });
            assertThat(isolated.await(5, TimeUnit.SECONDS)).isTrue();
            container.release();
            loop.get(2, TimeUnit.SECONDS);
            verify(c, times(1)).poll(any(Duration.class));
            verify(c).close();
        } finally {
            container.release();
            executor.shutdownNow();
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(5)
    void truncationWithoutEpochKeepsHealthyPartitionsRunningAndWarns() {
        var config = config();
        config.driverProperties().put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
        var c = consumer();
        var healthy = new TopicPartition("topic", 1);
        when(c.poll(any(Duration.class)))
            .thenThrow(new LogTruncationException(Map.of(partition, 5L), Map.of()))
            .thenReturn(ConsumerRecords.empty());
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config, mockTelemetry(), (records, consumer, allowed) -> ref.get().release());
        ref.set(container);
        var logger = (Logger) LoggerFactory.getLogger(KafkaAssignConsumerContainer.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try (var metadata = metadata(0, 1)) {
            container.launchPollLoop(c, 0, System.nanoTime());
            verify(c).assign(List.of(healthy));
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("isolating partition topic-0", "requires operator: true");
            });
        } finally {
            container.release();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(5)
    void retryPreservesLeaderEpochAfterHandlerFailure() {
        var first = consumer();
        var record = new ConsumerRecord<String, Integer>("topic", 0, 5L, "key", 1);
        var epochRecord = spy(record);
        when(epochRecord.leaderEpoch()).thenReturn(java.util.Optional.of(7));
        when(first.poll(any(Duration.class))).thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(epochRecord))));
        var next = consumer();
        when(next.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());
        var failed = new AtomicBoolean();
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config(), mockTelemetry(), (records, c, allowed) -> {
            if (failed.compareAndSet(false, true)) throw new IllegalStateException("handler failed");
            ref.get().release();
        });
        ref.set(container);
        try (var metadata = metadata(0)) {
            container.launchPollLoop(first, 0, System.nanoTime());
            container.launchPollLoop(next, 0, System.nanoTime());
            verify(next).seek(partition, new OffsetAndMetadata(5L, java.util.Optional.of(7), ""));
        } finally {
            container.release();
        }
    }

    @ParameterizedTest
    @CsvSource({"earliest,10", "latest,15"})
    void nativeResetIsAnchoredBeforeHandlerFailure(String policy, long resetPosition) {
        var config = config();
        config.driverProperties().put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, policy);
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(5L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(resetPosition, 1, 2));
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(partition, 10L));
        when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(partition, 17L));
        assertHandlerRetry(config, consumer, resetPosition, null, 0L, List.of(1, 2, 1, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"earliest", "latest"})
    void nativeTruncationRewindIsAnchoredBeforeHandlerFailure(String policy) {
        var config = config();
        config.driverProperties().put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, policy);
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(5L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(3L, 1, 2));
        assertHandlerRetry(config, consumer, 3L, null, 0L, List.of(1, 2, 1, 2));
    }

    @Test
    void emptyPollSavesResolvedResetPositionBeforeHandlerFailure() {
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(5L).thenReturn(15L);
        when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(partition, 10L));
        assertHandlerRetry(config(), consumer, 15L, null, 0L, List.of(1, 2));
    }

    @Test
    void unresolvedEmptyPartitionDoesNotLoseReturnedRecordsOrOtherCheckpoint() {
        var other = new TopicPartition("topic", 1);
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(5L);
        when(consumer.position(other)).thenReturn(20L);
        doThrow(new TimeoutException("reset pending")).when(consumer).position(other, Duration.ZERO);
        when(consumer.poll(any(Duration.class))).thenReturn(records(10L, 1, 2));
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(partition, 10L));
        assertHandlerRetry(config(), consumer, 10L, other, 20L, List.of(1, 2, 1, 2));
    }

    @Test
    void diagnosticFailureDoesNotPreventAnchoringEveryReturnedPartition() {
        var other = new TopicPartition("topic", 1);
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(5L);
        when(consumer.position(other)).thenReturn(20L);
        var batch = new LinkedHashMap<TopicPartition, List<ConsumerRecord<String, Integer>>>();
        batch.put(partition, List.of(new ConsumerRecord<>("topic", 0, 10L, "key", 1)));
        batch.put(other, List.of(new ConsumerRecord<>("topic", 1, 30L, "key", 2)));
        when(consumer.poll(any(Duration.class))).thenReturn(new ConsumerRecords<>(batch));
        when(consumer.beginningOffsets(anyCollection())).thenThrow(new TimeoutException("diagnostic timeout"));
        assertHandlerRetry(config(), consumer, 10L, other, 30L, List.of(1, 2, 1, 2));
    }

    @Test
    void positionDiagnosticsUseOneSharedTimeoutBudget() {
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(5L).thenReturn(15L);
        when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());
        doReturn(Map.of(partition, 10L)).when(consumer).beginningOffsets(anyCollection(), any(Duration.class));
        doReturn(Map.of(partition, 20L)).when(consumer).endOffsets(anyCollection(), any(Duration.class));
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var handled = new AtomicBoolean();
        var container = container(config(), mockTelemetry(), (records, c, allowed) -> {
            handled.set(true);
            ref.get().release();
        });
        ref.set(container);
        try (var metadata = metadata(0)) {
            container.launchPollLoop(consumer, 0, System.nanoTime());
            verify(consumer).beginningOffsets(anyCollection(), eq(Duration.ofSeconds(1)));
            var timeouts = ArgumentCaptor.forClass(Duration.class);
            verify(consumer, atMostOnce()).endOffsets(anyCollection(), timeouts.capture());
            for (var timeout : timeouts.getAllValues()) {
                assertThat(timeout).isPositive().isLessThan(Duration.ofSeconds(1));
            }
            verify(consumer, never()).beginningOffsets(anyCollection());
            verify(consumer, never()).endOffsets(anyCollection());
            assertThat(handled).isTrue();
        } finally {
            container.release();
        }
    }

    @Test
    void exhaustedDiagnosticBudgetSkipsEndLookupAndPreservesRetryPosition() {
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(5L).thenReturn(15L);
        when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());
        doAnswer(invocation -> {
            Thread.sleep(1100);
            return Map.of(partition, 10L);
        }).when(consumer).beginningOffsets(anyCollection(), any(Duration.class));
        assertHandlerRetry(config(), consumer, 15L, null, 0L, List.of(1, 2));
        verify(consumer, never()).endOffsets(anyCollection(), any(Duration.class));
    }

    @Test
    void diagnosticEndTimeoutDoesNotPreventHandlerRetry() {
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(5L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(10L, 1, 2));
        doReturn(Map.of(partition, 10L)).when(consumer).beginningOffsets(anyCollection(), any(Duration.class));
        doThrow(new TimeoutException("end lookup timeout")).when(consumer).endOffsets(anyCollection(), any(Duration.class));
        assertHandlerRetry(config(), consumer, 10L, null, 0L, List.of(1, 2, 1, 2));
    }

    @Test
    void pollFailureDoesNotAdvanceCheckpoints() {
        var first = consumer();
        when(first.position(partition)).thenReturn(5L);
        when(first.poll(any(Duration.class))).thenThrow(new TimeoutException("poll timeout"));
        var next = consumer();
        when(next.poll(any(Duration.class))).thenReturn(records(5L, 1));
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var values = new ArrayList<Integer>();
        var container = container(config(), mockTelemetry(), (records, c, allowed) -> {
            records.forEach(record -> values.add(record.value()));
            ref.get().release();
        });
        ref.set(container);
        try (var metadata = metadata(0)) {
            container.launchPollLoop(first, 0, System.nanoTime());
            container.launchPollLoop(next, 0, System.nanoTime());
            verify(next).seek(partition, 5L);
            assertThat(values).containsExactly(1);
        } finally {
            container.release();
        }
    }

    @ParameterizedTest
    @CsvSource({"5,10,10,true", "5,3,0,true", "5,10,0,false"})
    void positionChangesWarnForExpiredOrRewoundOffsetsButNotCompaction(long oldPosition, long current,
                                                                    long beginning, boolean warn) {
        var consumer = consumer();
        when(consumer.position(partition)).thenReturn(oldPosition);
        when(consumer.poll(any(Duration.class))).thenReturn(records(current, 1));
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(partition, beginning));
        when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(partition, 20L));
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config(), mockTelemetry(), (records, c, allowed) -> ref.get().release());
        ref.set(container);
        var logger = (Logger) LoggerFactory.getLogger(KafkaAssignConsumerContainer.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try (var metadata = metadata(0)) {
            container.launchPollLoop(consumer, 0, System.nanoTime());
            var warnings = appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
            if (warn) {
                assertThat(warnings).anySatisfy(event -> assertThat(event.getFormattedMessage())
                    .contains("topic-0", "from " + oldPosition + " to " + current, "[" + beginning + ", 20)"));
            } else {
                assertThat(warnings).isEmpty();
            }
        } finally {
            container.release();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"earliest", "latest", "none"})
    void consumerFactoryPreservesUserPropertiesForInterceptors(String policy) throws Exception {
        var config = config();
        config.driverProperties().put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        if (policy != null) config.driverProperties().put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, policy);
        config.driverProperties().put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, PolicyInterceptor.class.getName());
        PolicyInterceptor.observed.set(null);
        var container = container(config, mockTelemetry(), (records, c, allowed) -> {});
        var initialize = container.getClass().getDeclaredMethod("initializeConsumer");
        initialize.setAccessible(true);
        try (var consumer = (Consumer<?, ?>) initialize.invoke(container)) {
            assertThat(consumer).isNotNull();
            assertThat(PolicyInterceptor.observed.get()).isEqualTo(policy);
            assertThat(config.driverProperties().getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo(policy);
        } finally {
            container.release();
        }
    }

    public static class PolicyInterceptor implements org.apache.kafka.clients.consumer.ConsumerInterceptor<byte[], byte[]> {
        static final AtomicReference<Object> observed = new AtomicReference<>();

        @Override
        public void configure(Map<String, ?> configs) {
            observed.set(configs.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        }

        @Override
        public ConsumerRecords<byte[], byte[]> onConsume(ConsumerRecords<byte[], byte[]> records) {
            return records;
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {}

        @Override
        public void close() {}
    }

    private void assertHandlerRetry(KafkaListenerConfig config, Consumer<String, Integer> first, long expected,
                                    TopicPartition other, long otherExpected, List<Integer> expectedValues) {
        var next = consumer();
        when(next.poll(any(Duration.class))).thenReturn(records(expected, 1, 2));
        var values = new ArrayList<Integer>();
        var failed = new AtomicBoolean();
        var ref = new AtomicReference<KafkaAssignConsumerContainer<String, Integer>>();
        var container = container(config, mockTelemetry(), (records, c, allowed) -> {
            records.forEach(record -> values.add(record.value()));
            if (failed.compareAndSet(false, true)) throw new IllegalStateException("handler failure");
            ref.get().release();
        });
        ref.set(container);
        try (var metadata = other == null ? metadata(0) : metadata(0, 1)) {
            container.launchPollLoop(first, 0, System.nanoTime());
            container.launchPollLoop(next, 0, System.nanoTime());
            verify(next).seek(partition, expected);
            if (other != null) verify(next).seek(other, otherExpected);
            assertThat(values).containsExactlyElementsOf(expectedValues);
        } finally {
            container.release();
        }
    }

    private static KafkaAssignConsumerContainer<String, Integer> container(KafkaListenerConfig config,
                                                                          KafkaConsumerTelemetry<String, Integer> telemetry,
                                                                          BaseKafkaRecordsHandler<String, Integer> handler) {
        return new KafkaAssignConsumerContainer<>("test", config, "topic", new StringDeserializer(), new IntegerDeserializer(), telemetry, handler);
    }

    private ConsumerRecords<String, Integer> records(long offset, int... values) {
        var records = new ArrayList<ConsumerRecord<String, Integer>>();
        for (var i = 0; i < values.length; i++) records.add(new ConsumerRecord<>("topic", 0, offset + i, "key", values[i]));
        return new ConsumerRecords<>(Map.of(partition, records));
    }

    private static KafkaListenerConfig config() {
        var config = mock(KafkaListenerConfig.class, Mockito.CALLS_REAL_METHODS);
        when(config.driverProperties()).thenReturn(new Properties());
        when(config.backoffTimeout()).thenReturn(Duration.ofMillis(100));
        return config;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<String, Integer> consumer() {
        var consumer = (Consumer<String, Integer>) mock(Consumer.class);
        var positions = new HashMap<TopicPartition, Long>();
        doAnswer(invocation -> {
            positions.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(consumer).seek(any(TopicPartition.class), anyLong());
        when(consumer.position(any(TopicPartition.class)))
            .thenAnswer(invocation -> positions.getOrDefault(invocation.getArgument(0), 0L));
        when(consumer.position(any(TopicPartition.class), eq(Duration.ZERO)))
            .thenAnswer(invocation -> consumer.position(invocation.getArgument(0)));
        when(consumer.beginningOffsets(anyCollection())).thenAnswer(invocation -> {
            var offsets = new HashMap<TopicPartition, Long>();
            for (var partition : (java.util.Collection<TopicPartition>) invocation.getArgument(0)) offsets.put(partition, 0L);
            return offsets;
        });
        when(consumer.endOffsets(anyCollection())).thenAnswer(invocation -> {
            var offsets = new HashMap<TopicPartition, Long>();
            for (var partition : (java.util.Collection<TopicPartition>) invocation.getArgument(0)) offsets.put(partition, 1000L);
            return offsets;
        });
        when(consumer.beginningOffsets(anyCollection(), any(Duration.class)))
            .thenAnswer(invocation -> consumer.beginningOffsets(invocation.getArgument(0)));
        when(consumer.endOffsets(anyCollection(), any(Duration.class)))
            .thenAnswer(invocation -> consumer.endOffsets(invocation.getArgument(0)));
        return consumer;
    }

    @SuppressWarnings("unchecked")
    private static KafkaConsumerTelemetry<String, Integer> mockTelemetry() {
        return (KafkaConsumerTelemetry<String, Integer>) mock(KafkaConsumerTelemetry.class);
    }

    @SuppressWarnings("rawtypes")
    private static MockedConstruction<KafkaConsumer> metadata(int... partitions) {
        return mockConstruction(KafkaConsumer.class, (mock, context) -> {
            var info = new ArrayList<PartitionInfo>();
            for (var partition : partitions) info.add(new PartitionInfo("topic", partition, null, new Node[0], new Node[0]));
            when(mock.partitionsFor("topic")).thenReturn(info);
        });
    }
}
