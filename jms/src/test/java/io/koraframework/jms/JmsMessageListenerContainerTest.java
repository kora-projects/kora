package io.koraframework.jms;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.util.Size;
import io.koraframework.jms.telemetry.JmsConsumerObservation;
import io.koraframework.jms.telemetry.JmsConsumerTelemetry;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerTelemetry;
import io.koraframework.jms.telemetry.impl.NoopJmsConsumerTelemetry;
import io.koraframework.jms.util.JmsUtils;
import io.opentelemetry.api.trace.Span;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class JmsMessageListenerContainerTest {

    record Config(
        int threads,
        boolean readinessProbe,
        @Nullable Duration initializationFailTimeout,
        Duration shutdownWait,
        Size maxBodySize
    ) implements JmsListenerContainerConfig {

        Config(int threads, boolean readinessProbe, @Nullable Duration initializationFailTimeout, Duration shutdownWait) {
            this(threads, readinessProbe, initializationFailTimeout, shutdownWait, JmsListenerContainerConfig.DEFAULT_MAX_BODY_SIZE);
        }

        public String queueName() {
            return "test-queue";
        }

        public Duration pollTimeout() {
            return Duration.ofMillis(20);
        }

        public Duration backoffTimeout() {
            return Duration.ofMillis(10);
        }

        public Duration maxBackoffTimeout() {
            return Duration.ofMillis(50);
        }

        public JmsConsumerTelemetryConfig telemetry() {
            return DefaultJmsConsumerTelemetry.TelemetryContext.EMPTY.config();
        }
    }

    static void await(BooleanSupplier condition) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!condition.getAsBoolean()) {
                Thread.sleep(5);
            }
        });
    }

    static final class Broker {

        final ConnectionFactory factory = mock(ConnectionFactory.class);
        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
        final List<Peer> peers = new CopyOnWriteArrayList<>();
        final AtomicReference<JMSException> connectFailure = new AtomicReference<>();
        final Message wakeup = mock(Message.class);

        Broker() throws Exception {
            when(factory.createConnection()).thenAnswer(invocation -> {
                var failure = connectFailure.get();
                if (failure != null)
                    throw failure;
                var peer = new Peer();
                peers.add(peer);
                return peer.connection;
            });
        }

        final class Peer {

            final Connection connection = mock(Connection.class);
            final Session session = mock(Session.class);
            final MessageConsumer consumer = mock(MessageConsumer.class);
            final AtomicReference<ExceptionListener> exceptions = new AtomicReference<>();

            Peer() throws Exception {
                var queue = mock(Queue.class);
                when(connection.createSession(true, Session.SESSION_TRANSACTED)).thenReturn(session);
                when(session.createQueue("test-queue")).thenReturn(queue);
                when(session.createConsumer(queue)).thenReturn(consumer);
                doAnswer(invocation -> {
                    exceptions.set(invocation.getArgument(0));
                    return null;
                }).when(connection).setExceptionListener(any());
                when(consumer.receive(anyLong())).thenAnswer(invocation -> {
                    var message = messages.poll(invocation.<Long>getArgument(0), TimeUnit.MILLISECONDS);
                    return message == wakeup ? null : message;
                });
                doAnswer(invocation -> {
                    messages.offer(wakeup);
                    return null;
                }).when(consumer).close();
            }
        }
    }

    private JmsMessageListenerContainer container(
        Broker broker,
        JmsListenerContainerConfig config,
        JmsMessageListener listener,
        JmsConsumerTelemetry telemetry
    ) {
        return new JmsMessageListenerContainer(broker.factory, config, listener, (c, q) -> telemetry);
    }

    private JmsMessageListenerContainer container(Broker broker, JmsMessageListener listener) {
        return container(
            broker,
            new Config(1, true, Duration.ofSeconds(3), Duration.ofSeconds(2)),
            listener,
            NoopJmsConsumerTelemetry.INSTANCE
        );
    }

    @Test
    void defaultsDisableReadinessAndStartupWaitAndZeroThreadsDisableListener() throws Exception {
        var config = new JmsListenerContainerConfig() {

            public String queueName() {
                return "queue";
            }

            public int threads() {
                return 0;
            }

            public JmsConsumerTelemetryConfig telemetry() {
                return DefaultJmsConsumerTelemetry.TelemetryContext.EMPTY.config();
            }
        };
        assertThat(config.readinessProbe()).isFalse();
        assertThat(config.initializationFailTimeout()).isNull();
        var factory = mock(ConnectionFactory.class);
        var container = new JmsMessageListenerContainer(factory, config, (s, m) -> {}, (c, q) -> NoopJmsConsumerTelemetry.INSTANCE);
        container.init();
        container.init();
        container.release();
        container.release();
        assertThat(container.probe()).isNull();
        verifyNoInteractions(factory);
    }

    @Test
    void validatesConfigBeforeStartup() throws Exception {
        var broker = new Broker();
        assertThatThrownBy(
            () -> container(broker, new Config(-1, false, null, Duration.ofSeconds(1)), (s, m) -> {}, NoopJmsConsumerTelemetry.INSTANCE)
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("threads");
        assertThatThrownBy(
            () -> container(broker, new Config(1, false, null, Duration.ofMillis(-1)), (s, m) -> {}, NoopJmsConsumerTelemetry.INSTANCE)
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shutdownWait");
        for (var size : List.of(Size.of(-1, Size.Type.BYTES), Size.of(2, Size.Type.GiB))) {
            assertThatThrownBy(
                () -> container(
                    broker,
                    new Config(1, false, null, Duration.ofSeconds(1), size),
                    (s, m) -> {},
                    NoopJmsConsumerTelemetry.INSTANCE
                )
            ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxBodySize");
        }
        verifyNoInteractions(broker.factory);
    }

    @Test
    void startupAndReadinessWaitForFirstSuccessfulReceive() throws Exception {
        var broker = new Broker();
        var receiveEntered = new CountDownLatch(1);
        var allowReceive = new CountDownLatch(1);
        var peer = broker.new Peer();
        broker.peers.add(peer);
        doReturn(peer.connection).when(broker.factory).createConnection();
        when(peer.consumer.receive(anyLong())).thenAnswer(invocation -> {
            receiveEntered.countDown();
            allowReceive.await();
            return null;
        });
        var container = container(broker, (s, m) -> {});
        var startupFailure = new AtomicReference<Throwable>();
        var startup = Thread.ofVirtual().start(() -> {
            try {
                container.init();
            } catch (Throwable e) {
                startupFailure.set(e);
            }
        });
        try {
            assertThat(receiveEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(container.probe()).isNotNull();
            assertThat(startup.isAlive()).isTrue();
            allowReceive.countDown();
            startup.join(3000);
            assertThat(startup.isAlive()).isFalse();
            assertThat(startupFailure.get()).isNull();
            assertThat(container.probe()).isNull();
        } finally {
            allowReceive.countDown();
            startup.join(3000);
            container.release();
        }
    }

    @Test
    void emptyQueueStartupUsesShortPollEvenWithLongPollTimeout() throws Exception {
        var broker = new Broker();
        var config = mock(JmsListenerContainerConfig.class, CALLS_REAL_METHODS);
        when(config.queueName()).thenReturn("test-queue");
        when(config.threads()).thenReturn(1);
        when(config.pollTimeout()).thenReturn(Duration.ofSeconds(30));
        when(config.initializationFailTimeout()).thenReturn(Duration.ofSeconds(2));
        var container = container(broker, config, (s, m) -> {}, NoopJmsConsumerTelemetry.INSTANCE);
        try {
            container.init();
            verify(broker.peers.getFirst().consumer).receive(10L);
        } finally {
            container.release();
        }
    }

    @Test
    void fatalWorkerFailureFailsStartupImmediatelyAndPreservesCause() throws Exception {
        var broker = new Broker();
        var failure = new AssertionError("fatal connection failure");
        doThrow(failure).when(broker.factory).createConnection();
        var container = container(
            broker,
            new Config(1, true, Duration.ofMinutes(1), Duration.ofSeconds(2)),
            (s, m) -> {},
            NoopJmsConsumerTelemetry.INSTANCE
        );
        assertTimeoutPreemptively(
            Duration.ofSeconds(5),
            () -> assertThatThrownBy(container::init).isInstanceOf(java.lang.IllegalStateException.class).hasCause(failure)
        );
        assertThat(container.probe()).isNotNull();
        container.release();
    }

    @Test
    void receiveWithAsyncConnectionFailureDoesNotConfirmStartupOrProcessDelivery() throws Exception {
        var broker = new Broker();
        var peer = broker.new Peer();
        broker.peers.add(peer);
        doReturn(peer.connection).doAnswer(invocation -> {
            var recovered = broker.new Peer();
            broker.peers.add(recovered);
            return recovered.connection;
        }).when(broker.factory).createConnection();
        var message = mock(Message.class);
        when(peer.consumer.receive(anyLong())).thenAnswer(invocation -> {
            peer.exceptions.get().onException(new JMSException("async failure during receive"));
            return message;
        });
        var listener = mock(JmsMessageListener.class);
        var container = container(
            broker,
            new Config(1, true, Duration.ofSeconds(3), Duration.ofSeconds(2)),
            listener,
            NoopJmsConsumerTelemetry.INSTANCE
        );
        try {
            container.init();
            assertThat(broker.peers).hasSize(2);
            verifyNoInteractions(listener);
            verify(peer.session, never()).commit();
            verify(peer.consumer).close();
            assertThat(container.probe()).isNull();
        } finally {
            container.release();
        }
    }

    @Test
    void configuredBodyLimitAppliesToConversionsAndDoesNotLeakBetweenListeners() throws Exception {
        var restricted = new Broker();
        var unrestricted = new Broker();
        var message = mock(TextMessage.class);
        when(message.getText()).thenReturn("abc");
        var limited = container(
            restricted,
            new Config(1, true, Duration.ofSeconds(3), Duration.ofSeconds(2), Size.of(2, Size.Type.BYTES)),
            (s, m) -> {
                assertThatThrownBy(() -> JmsUtils.text(m)).isInstanceOf(JMSException.class).hasMessageContaining("2 bytes");
                assertThatThrownBy(() -> JmsUtils.bytes(m)).isInstanceOf(JMSException.class).hasMessageContaining("2 bytes");
                assertThat(JmsUtils.text(m, 3)).isEqualTo("abc");
            },
            NoopJmsConsumerTelemetry.INSTANCE
        );
        var larger = container(
            unrestricted,
            new Config(1, true, Duration.ofSeconds(3), Duration.ofSeconds(2), Size.of(3, Size.Type.BYTES)),
            (s, m) -> {
                assertThat(JmsUtils.text(m)).isEqualTo("abc");
                assertThat(JmsUtils.bytes(m)).containsExactly((byte) 'a', (byte) 'b', (byte) 'c');
            },
            NoopJmsConsumerTelemetry.INSTANCE
        );
        try {
            limited.init();
            larger.init();
            restricted.messages.offer(message);
            unrestricted.messages.offer(message);
            verify(restricted.peers.getFirst().session, timeout(3000)).commit();
            verify(unrestricted.peers.getFirst().session, timeout(3000)).commit();
            assertThat(JmsUtils.text(message)).isEqualTo("abc");
        } finally {
            limited.release();
            larger.release();
        }
    }

    @Test
    void handlerRunsInVirtualThreadAndObservationFinishesAfterCommit() throws Exception {
        var broker = new Broker();
        var events = new CopyOnWriteArrayList<String>();
        var observation = mock(JmsConsumerObservation.class);
        when(observation.span()).thenReturn(Span.getInvalid());
        doAnswer(invocation -> {
            assertThat(Observation.current(JmsConsumerObservation.class)).isSameAs(observation);
            events.add("end");
            return null;
        }).when(observation).end();
        var container = container(broker, new Config(1, true, Duration.ofSeconds(3), Duration.ofSeconds(2)), (s, m) -> {
            assertThat(Thread.currentThread().isVirtual()).isTrue();
            assertThat(Thread.currentThread().getName()).startsWith("jms-test-queue-");
            assertThat(Observation.current(JmsConsumerObservation.class)).isSameAs(observation);
            events.add("handler");
        }, message -> observation);
        try {
            assertThat(container.probe()).isNotNull();
            container.init();
            assertThat(container.probe()).isNull();
            var session = broker.peers.getFirst().session;
            doAnswer(invocation -> {
                events.add("commit");
                return null;
            }).when(session).commit();
            broker.messages.offer(mock(Message.class));
            await(() -> events.size() == 3);
            assertThat(events).containsExactly("handler", "commit", "end");
            verify(session, never()).rollback();
        } finally {
            container.release();
        }
        assertThat(container.probe()).isNotNull();
        verify(broker.peers.getFirst().connection).close();
    }

    @Test
    void idleReceiveDoesNotCommitAndInitIsIdempotent() throws Exception {
        var broker = new Broker();
        var container = container(broker, (s, m) -> {});
        try {
            container.init();
            container.init();
            var peer = broker.peers.getFirst();
            verify(peer.consumer, timeout(3000).atLeast(2)).receive(20L);
            verify(peer.session, never()).commit();
            assertThat(broker.peers).hasSize(1);
        } finally {
            container.release();
            container.release();
        }
        container.init();
        container.release();
        assertThat(broker.peers).hasSize(2);
    }

    @Test
    void everyTelemetryCallbackCanFailWithoutRollingBackSuccessfulHandler() throws Exception {
        var broker = new Broker();
        var observation = mock(JmsConsumerObservation.class);
        when(observation.span()).thenThrow(new IllegalStateException("scope"));
        doThrow(new JMSException("process")).when(observation).observeProcess();
        doThrow(new IllegalStateException("end")).when(observation).end();
        var calls = new AtomicInteger();
        var telemetryCalls = new AtomicInteger();
        var container = container(
            broker,
            new Config(1, true, Duration.ofSeconds(3), Duration.ofSeconds(2)),
            (s, m) -> calls.incrementAndGet(),
            message -> {
                if (telemetryCalls.getAndIncrement() == 0)
                    throw new JMSException("create observation");
                return observation;
            }
        );
        try {
            container.init();
            broker.messages.offer(mock(Message.class));
            broker.messages.offer(mock(Message.class));
            verify(broker.peers.getFirst().session, timeout(3000).times(2)).commit();
            assertThat(calls).hasValue(2);
            verify(broker.peers.getFirst().session, never()).rollback();
            verify(observation).end();
        } finally {
            container.release();
        }
    }

    @Test
    void handlerFailureRollsBackAndAllowsNextDeliveryEvenWhenErrorCallbackFails() throws Exception {
        var broker = new Broker();
        var error = new IllegalArgumentException("handler");
        var observation = mock(JmsConsumerObservation.class);
        when(observation.span()).thenReturn(Span.getInvalid());
        doThrow(new IllegalStateException("observe error")).when(observation).observeError(error);
        var calls = new AtomicInteger();
        var container = container(broker, new Config(1, true, Duration.ofSeconds(3), Duration.ofSeconds(2)), (s, m) -> {
            if (calls.getAndIncrement() == 0)
                throw error;
        }, message -> observation);
        try {
            container.init();
            broker.messages.offer(mock(Message.class));
            broker.messages.offer(mock(Message.class));
            verify(broker.peers.getFirst().session, timeout(3000)).commit();
            verify(broker.peers.getFirst().session).rollback();
            verify(observation).observeError(error);
            verify(observation, timeout(3000).times(2)).end();
        } finally {
            container.release();
        }
    }

    @Test
    void commitAndRollbackFailurePreserveOriginalErrorAndReconnect() throws Exception {
        var broker = new Broker();
        var commitFailure = new JMSException("commit");
        var rollbackFailure = new JMSException("rollback");
        var observation = mock(JmsConsumerObservation.class);
        when(observation.span()).thenReturn(Span.getInvalid());
        var container =
                container(broker, new Config(1, true, Duration.ofSeconds(3), Duration.ofSeconds(2)), (s, m) -> {}, message -> observation);
        try {
            container.init();
            var peer = broker.peers.getFirst();
            doThrow(commitFailure).when(peer.session).commit();
            doThrow(rollbackFailure).when(peer.session).rollback();
            broker.messages.offer(mock(Message.class));
            await(() -> broker.peers.size() > 1);
            verify(observation).observeError(commitFailure);
            verify(observation).end();
            assertThat(commitFailure).hasSuppressedException(rollbackFailure);
            verify(peer.connection).close();
        } finally {
            container.release();
        }
    }

    @Test
    void initializationTimeoutCleansUpAndCanRetry() throws Exception {
        var broker = new Broker();
        var failure = new JMSException("offline");
        broker.connectFailure.set(failure);
        var container = container(
            broker,
            new Config(1, false, Duration.ofMillis(100), Duration.ofSeconds(2)),
            (s, m) -> {},
            NoopJmsConsumerTelemetry.INSTANCE
        );
        assertThat(container.probe()).isNull();
        assertThatThrownBy(container::init).isInstanceOf(java.lang.IllegalStateException.class)
            .hasMessageContaining("failed to start")
            .hasCause(failure);
        container.release();
        broker.connectFailure.set(null);
        try {
            container.init();
        } finally {
            container.release();
        }
        assertThat(broker.peers).hasSize(1);
    }

    @Test
    void readinessTracksAllConnectionsAndAsyncConnectionFailure() throws Exception {
        var broker = new Broker();
        var container = container(
            broker,
            new Config(2, true, Duration.ofSeconds(3), Duration.ofSeconds(2)),
            (s, m) -> {},
            NoopJmsConsumerTelemetry.INSTANCE
        );
        try {
            container.init();
            assertThat(broker.peers).hasSize(2);
            assertThat(container.probe()).isNull();
            broker.connectFailure.set(new JMSException("offline"));
            broker.peers.getFirst().exceptions.get().onException(new JMSException("disconnected"));
            assertThat(container.probe()).isNotNull();
            broker.connectFailure.set(null);
            await(() -> container.probe() == null);
        } finally {
            container.release();
        }
    }

    @Test
    void shutdownAllowsInFlightHandlerToCommitWithoutInterrupt() throws Exception {
        var broker = new Broker();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var container = container(broker, (s, m) -> {
            entered.countDown();
            try {
                finish.await();
            } catch (InterruptedException e) {
                throw new AssertionError("handler interrupted", e);
            }
        });
        container.init();
        broker.messages.offer(mock(Message.class));
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        var shutdownFailure = new AtomicReference<Throwable>();
        var shutdown = Thread.ofVirtual().start(() -> {
            try {
                container.release();
            } catch (Throwable e) {
                shutdownFailure.set(e);
            }
        });
        try {
            verify(broker.peers.getFirst().consumer, timeout(3000).atLeastOnce()).close();
            finish.countDown();
            shutdown.join(3000);
            assertThat(shutdown.isAlive()).isFalse();
            assertThat(shutdownFailure.get()).isNull();
            verify(broker.peers.getFirst().session).commit();
        } finally {
            finish.countDown();
            container.release();
        }
    }

    @Test
    void shutdownTimeoutPreventsOverlappingRestart() throws Exception {
        var broker = new Broker();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var container = container(broker, new Config(1, true, Duration.ofSeconds(3), Duration.ofMillis(50)), (s, m) -> {
            entered.countDown();
            boolean done = false;
            while (!done) {
                try {
                    finish.await();
                    done = true;
                } catch (InterruptedException ignored) {}
            }
        }, NoopJmsConsumerTelemetry.INSTANCE);
        container.init();
        broker.messages.offer(mock(Message.class));
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(container::release).isInstanceOf(java.lang.IllegalStateException.class).hasMessageContaining("did not stop");
            assertThatThrownBy(container::init).isInstanceOf(java.lang.IllegalStateException.class).hasMessageContaining("still stopping");
            assertThat(container.probe()).isNotNull();
        } finally {
            finish.countDown();
            await(() -> {
                try {
                    container.release();
                    return true;
                } catch (java.lang.IllegalStateException e) {
                    return false;
                }
            });
        }
        assertThat(broker.peers).hasSize(1);
        container.init();
        container.release();
        assertThat(broker.peers).hasSize(2);
    }

    @Test
    void interruptedInitializationRestoresCallerInterrupt() throws Exception {
        var broker = new Broker();
        broker.connectFailure.set(new JMSException("offline"));
        var container = container(broker, (s, m) -> {});
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(container::init).isInstanceOf(java.lang.IllegalStateException.class).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            container.release();
        }
    }

    @Test
    void fatalHandlerErrorStopsWorkerAndFailsReadiness() throws Exception {
        var broker = new Broker();
        var container = container(broker, (s, m) -> { throw new AssertionError("fatal handler"); });
        try {
            container.init();
            var peer = broker.peers.getFirst();
            broker.messages.offer(mock(Message.class));
            verify(peer.connection, timeout(3000)).close();
            verify(peer.session).rollback();
            assertThat(container.probe()).isNotNull();
            assertThat(broker.peers).hasSize(1);
        } finally {
            container.release();
        }
    }

    @Test
    void interruptedShutdownPreservesCallerInterruptAndCanBeRetried() throws Exception {
        var broker = new Broker();
        var container = container(broker, (s, m) -> {});
        container.init();
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(container::release).isInstanceOf(java.lang.IllegalStateException.class).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            container.release();
        }
    }

    @Test
    void readinessFailsBeforeBrokenConsumerCloseCompletes() throws Exception {
        var broker = new Broker();
        var closeEntered = new CountDownLatch(1);
        var allowClose = new CountDownLatch(1);
        var container = container(broker, (s, m) -> {});
        try {
            container.init();
            var peer = broker.peers.getFirst();
            doAnswer(invocation -> {
                closeEntered.countDown();
                allowClose.await();
                return null;
            }).when(peer.consumer).close();
            when(peer.consumer.receive(anyLong())).thenThrow(new JMSException("receive failed"));
            assertThat(closeEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(container.probe()).isNotNull();
        } finally {
            allowClose.countDown();
            container.release();
        }
    }

    @Test
    void deliveryRestoresProviderMdcAndDoesNotLeakScopedMdc() throws Exception {
        var broker = new Broker();
        var container = container(broker, (s, m) -> {
            assertThat(org.slf4j.MDC.get("provider")).isEqualTo("original");
            assertThat(io.koraframework.logging.common.MDC.get().values()).isEmpty();
            org.slf4j.MDC.put("provider", "handler");
            org.slf4j.MDC.put("temporary", "handler");
            io.koraframework.logging.common.MDC.get().put0("temporary", "handler");
        });
        try {
            container.init();
            var peer = broker.peers.getFirst();
            var receives = new AtomicInteger();
            var receiveConfigured = new CountDownLatch(1);
            when(peer.consumer.receive(anyLong())).thenAnswer(invocation -> {
                receiveConfigured.countDown();
                var message = broker.messages.poll(20, TimeUnit.MILLISECONDS);
                if (message != null && message != broker.wakeup) {
                    if (receives.getAndIncrement() == 0) {
                        org.slf4j.MDC.put("provider", "original");
                    } else {
                        assertThat(org.slf4j.MDC.get("provider")).isEqualTo("original");
                        assertThat(org.slf4j.MDC.get("temporary")).isNull();
                    }
                }
                return message == broker.wakeup ? null : message;
            });
            assertThat(receiveConfigured.await(3, TimeUnit.SECONDS)).isTrue();
            broker.messages.offer(mock(Message.class));
            broker.messages.offer(mock(Message.class));
            verify(peer.session, timeout(3000).times(2)).commit();
        } finally {
            container.release();
        }
    }
}
