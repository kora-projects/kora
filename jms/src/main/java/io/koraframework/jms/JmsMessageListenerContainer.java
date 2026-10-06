package io.koraframework.jms;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.executor.LimitedVirtualThreadPerTaskExecutor;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.jms.telemetry.JmsConsumerObservation;
import io.koraframework.jms.telemetry.JmsConsumerTelemetry;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryFactory;
import io.koraframework.jms.telemetry.impl.NoopJmsConsumerObservation;
import io.koraframework.jms.util.JmsUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.jms.*;
import java.lang.IllegalStateException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class JmsMessageListenerContainer implements Lifecycle, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(JmsMessageListenerContainer.class);
    private final ConnectionFactory connectionFactory;
    private final JmsListenerContainerConfig config;
    private final JmsMessageListener messageListener;
    private final JmsConsumerTelemetry telemetry;
    private final int maxBodySize;
    @Nullable
    private volatile WorkerGroup workers;

    public JmsMessageListenerContainer(
        ConnectionFactory connectionFactory,
        JmsListenerContainerConfig config,
        JmsMessageListener messageListener,
        JmsConsumerTelemetryFactory telemetryFactory
    ) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
        this.config = Objects.requireNonNull(config);
        this.messageListener = Objects.requireNonNull(messageListener);
        validateConfig(config);
        this.maxBodySize = (int) config.maxBodySize().toBytes();
        this.telemetry = Objects.requireNonNull(telemetryFactory.get(config.telemetry(), config.queueName()));
    }

    private static void validateConfig(JmsListenerContainerConfig config) {
        if (config.queueName().isBlank()) {
            throw new IllegalArgumentException("JMS queueName must not be blank");
        }
        if (config.threads() < 0) {
            throw new IllegalArgumentException("JMS threads must not be negative");
        }
        long bodySize = config.maxBodySize().toBytes();
        if (bodySize < 0 || bodySize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JMS maxBodySize must be between 0 and " + Integer.MAX_VALUE + " bytes");
        }
        validateDuration("pollTimeout", config.pollTimeout(), false);
        validateDuration("backoffTimeout", config.backoffTimeout(), false);
        validateDuration("maxBackoffTimeout", config.maxBackoffTimeout(), false);
        validateDuration("shutdownWait", config.shutdownWait(), true);
        if (config.initializationFailTimeout() != null) {
            validateDuration("initializationFailTimeout", config.initializationFailTimeout(), true);
        }
        if (config.maxBackoffTimeout().compareTo(config.backoffTimeout()) < 0) {
            throw new IllegalArgumentException("JMS maxBackoffTimeout must be at least backoffTimeout");
        }
    }

    private static void validateDuration(String name, Duration duration, boolean allowZero) {
        try {
            if (duration.isNegative() || (!allowZero && duration.toMillis() == 0)) {
                throw new IllegalArgumentException("JMS " + name + " must be " + (allowZero ? "nonnegative" : "at least 1ms"));
            }
            duration.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("JMS " + name + " is too large", e);
        }
    }

    @Override
    public synchronized void init() {
        var current = this.workers;
        if (current != null) {
            if (current.running.get()) {
                return;
            }
            if (!current.isTerminated()) {
                throw new IllegalStateException("JMS listener for '" + config.queueName() + "' is still stopping");
            }
            this.workers = null;
        }
        if (config.threads() == 0) {
            return;
        }
        var started = System.nanoTime();
        var group = new WorkerGroup();
        this.workers = group;
        try {
            for (var worker : group.consumers) {
                // Fatal failures must not be hidden in an ignored Future.
                group.executor.execute(worker::run);
            }
            var timeout = config.initializationFailTimeout();
            if (timeout != null) {
                try {
                    group.initialized.get(remaining(started + timeout.toNanos()), TimeUnit.NANOSECONDS);
                } catch (TimeoutException e) {
                    throw new IllegalStateException(
                        "JMS listener for '" + config.queueName() + "' failed to start within " + timeout, group.lastFailure.get()
                    );
                } catch (ExecutionException e) {
                    throw new IllegalStateException("JMS listener for '" + config.queueName() + "' failed to start", e.getCause());
                }
            }
            logger.info("JMS listener for '{}' started in {}", config.queueName(), TimeUtils.tookForLogging(started));
        } catch (InterruptedException e) {
            var failure = new IllegalStateException("JMS listener initialization interrupted for '" + config.queueName() + "'", e);
            stopAfterInitFailure(group, failure);
            Thread.currentThread().interrupt();
            throw failure;
        } catch (RuntimeException | Error e) {
            stopAfterInitFailure(group, e);
            throw e;
        }
    }

    private void stopAfterInitFailure(WorkerGroup group, Throwable failure) {
        try {
            stop(group);
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    @Override
    public synchronized void release() {
        var group = this.workers;
        if (group != null) {
            stop(group);
        }
    }

    private void stop(WorkerGroup group) {
        var started = System.nanoTime();
        long deadline = started + config.shutdownWait().toNanos();
        if (group.running.compareAndSet(true, false)) {
            group.stopSignal.countDown();
            group.executor.shutdown();
            // Cross-thread consumer.close unblocks receive without invalidating the session
            // used by an in-flight synchronous handler or interrupting that handler.
            for (var worker : group.consumers) {
                var attempt = worker.attempt;
                var consumer = attempt == null ? null : attempt.consumer;
                if (consumer != null) {
                    group.closer.execute(() -> closeResource(consumer));
                }
            }
        }
        try {
            if (!group.executor.awaitTermination(remaining(deadline), TimeUnit.NANOSECONDS)) {
                forceStop(group);
            }
            group.closer.shutdown();
            group.closer.awaitTermination(remaining(deadline), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            forceStop(group);
            group.closer.shutdown();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JMS listener shutdown interrupted for '" + config.queueName() + "'", e);
        }
        if (!group.isTerminated()) {
            throw new IllegalStateException("JMS listener for '" + config.queueName() + "' did not stop within " + config.shutdownWait());
        }
        this.workers = null;
        logger.info("JMS listener for '{}' stopped in {}", config.queueName(), TimeUtils.tookForLogging(started));
    }

    private void forceStop(WorkerGroup group) {
        group.executor.shutdownNow();
        if (!group.closer.isShutdown() && group.forceClosing.compareAndSet(false, true)) {
            for (var worker : group.consumers) {
                var attempt = worker.attempt;
                if (attempt != null) {
                    group.closer.execute(() -> closeResource(attempt.connection));
                }
            }
        }
    }

    private static long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }

    private void closeResource(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception e) {
            logger.warn("Failed to close JMS resource for '{}'", config.queueName(), e);
        }
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() {
        if (!config.readinessProbe() || config.threads() == 0) {
            return null;
        }
        var group = this.workers;
        if (group == null || !group.running.get()) {
            return new ReadinessProbeFailure("JMS listener for '" + config.queueName() + "' is not running");
        }
        int connected = group.connected.get();
        return connected == config.threads()
            ? null
            : new ReadinessProbeFailure(
            "JMS listener for '" + config.queueName() + "' has " + connected + "/" + config.threads() + " connected consumers"
        );
    }

    private final class WorkerGroup {

        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicBoolean forceClosing = new AtomicBoolean();
        private final AtomicInteger connected = new AtomicInteger();
        private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
        private final CompletableFuture<Void> initialized = new CompletableFuture<>();
        private final AtomicInteger initializing = new AtomicInteger(config.threads());
        private final CountDownLatch stopSignal = new CountDownLatch(1);
        private final LimitedVirtualThreadPerTaskExecutor executor =
            new LimitedVirtualThreadPerTaskExecutor(config.threads(), "jms-" + config.queueName());
        // A blocked consumer close must not prevent a forced connection close from starting.
        private final LimitedVirtualThreadPerTaskExecutor closer = new LimitedVirtualThreadPerTaskExecutor(
            (int) Math.min(Integer.MAX_VALUE, 2L * config.threads()), "jms-close-" + config.queueName()
        );
        private final List<Worker> consumers = new ArrayList<>();

        private WorkerGroup() {
            for (int i = 0; i < config.threads(); i++) {
                consumers.add(new Worker(this));
            }
        }

        private boolean isTerminated() {return executor.isTerminated() && closer.isTerminated();}

        private boolean pause(long millis) throws InterruptedException {
            return running.get() && !stopSignal.await(millis, TimeUnit.MILLISECONDS);
        }
    }

    private final class ConnectionAttempt {

        private final WorkerGroup group;
        private final Connection connection;
        private final AtomicBoolean connected = new AtomicBoolean();
        private final AtomicReference<JMSException> failure = new AtomicReference<>();
        @Nullable
        private volatile MessageConsumer consumer;

        private ConnectionAttempt(WorkerGroup group, Connection connection) {
            this.group = group;
            this.connection = connection;
        }

        private void setConnected(boolean value) {
            if (connected.compareAndSet(!value, value)) {
                group.connected.addAndGet(value ? 1 : -1);
            }
        }
    }

    private final class Worker {

        private final WorkerGroup group;
        @Nullable
        private volatile ConnectionAttempt attempt;
        private boolean initialized;

        private Worker(WorkerGroup group) {
            this.group = group;
        }

        private void run() {
            long backoff = config.backoffTimeout().toMillis();
            try {
                while (group.running.get() && !Thread.currentThread().isInterrupted()) {
                    try (var connection = connectionFactory.createConnection()) {
                        var state = new ConnectionAttempt(group, connection);
                        this.attempt = state;
                        connection.setExceptionListener(e -> {
                            state.failure.compareAndSet(null, e);
                            state.setConnected(false);
                        });
                        if (!group.running.get()) {
                            break;
                        }
                        try (var session = connection.createSession(true, Session.SESSION_TRANSACTED);
                             var consumer = session.createConsumer(session.createQueue(config.queueName()))) {
                            state.consumer = consumer;
                            try {
                                connection.start();
                                var startupFailure = state.failure.get();
                                if (startupFailure != null) {
                                    throw startupFailure;
                                }
                                boolean firstPoll = true;
                                while (group.running.get()) {
                                    var failure = state.failure.get();
                                    if (failure != null) {
                                        throw failure;
                                    }
                                    // Like Kafka, confirm startup with a short first poll, even on an empty queue.
                                    var message = consumer.receive(
                                        firstPoll ? Math.min(10, config.pollTimeout().toMillis()) : config.pollTimeout().toMillis()
                                    );
                                    failure = state.failure.get();
                                    if (failure != null) {
                                        throw failure;
                                    }
                                    if (!group.running.get()) {
                                        break; // Session.close rolls back the unprocessed delivery.
                                    }
                                    if (firstPoll) {
                                        state.setConnected(true);
                                        failure = state.failure.get();
                                        if (failure != null) {
                                            throw failure;
                                        }
                                        firstPoll = false;
                                        if (!initialized) {
                                            initialized = true;
                                            if (group.initializing.decrementAndGet() == 0) {
                                                group.initialized.complete(null);
                                            }
                                        }
                                        logger.debug("JMS consumer connected to '{}'", config.queueName());
                                    }
                                    if (message == null) {
                                        backoff = config.backoffTimeout().toMillis();
                                        continue;
                                    }
                                    var processingFailure = processMessage(session, message);
                                    backoff = config.backoffTimeout().toMillis();
                                    if (processingFailure != null) {
                                        logger.warn(
                                            "JMS handler failed for '{}'; message rolled back",
                                            config.queueName(),
                                            processingFailure
                                        );
                                        if (!group.pause(backoff)) {
                                            break;
                                        }
                                    }
                                }
                            } finally {
                                // Publish loss of connectivity before a provider's close may block.
                                state.setConnected(false);
                            }
                        } finally {
                            state.consumer = null;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        group.lastFailure.set(e);
                        if (!group.running.get()) {
                            break;
                        }
                        safeTelemetry(() -> telemetry.observeConnectionError(e));
                        long delay = ThreadLocalRandom.current().nextLong(Math.max(1, backoff / 2), backoff + 1);
                        logger.warn("JMS consumer for '{}' failed; reconnecting in {}ms", config.queueName(), delay, e);
                        if (!group.pause(delay)) {
                            break;
                        }
                        long maxBackoff = config.maxBackoffTimeout().toMillis();
                        backoff = backoff >= maxBackoff / 2 ? maxBackoff : Math.min(maxBackoff, backoff * 2);
                    } finally {
                        var state = this.attempt;
                        if (state != null) {
                            state.setConnected(false);
                        }
                        this.attempt = null;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Error e) {
                group.lastFailure.set(e);
                group.initialized.completeExceptionally(e);
                logger.error("JMS worker for '{}' terminated", config.queueName(), e);
                throw e;
            }
        }
    }

    @Nullable
    private Exception processMessage(Session session, Message message) throws Exception {
        JmsConsumerObservation observation;
        try {
            observation = Objects.requireNonNull(telemetry.observe(message));
        } catch (Exception e) {
            logger.warn("Failed to create JMS observation for '{}'", config.queueName(), e);
            observation = NoopJmsConsumerObservation.INSTANCE;
        }
        var current = observation;
        var ended = new AtomicBoolean();
        var previousMdc = MDC.getCopyOfContextMap();
        try {
            ScopedValue.Carrier scope;
            try {
                scope = Observation.scoped(current);
            } catch (RuntimeException e) {
                logger.warn("Failed to scope JMS observation for '{}'", config.queueName(), e);
                scope = Observation.scoped(NoopJmsConsumerObservation.INSTANCE);
            }
            return JmsUtils.withBodySizeLimit(scope, maxBodySize)
                .where(io.koraframework.logging.common.MDC.VALUE, new io.koraframework.logging.common.MDC())
                .call(() -> {
                    try {
                        safeTelemetry(current::observeProcess);
                        boolean handlerCompleted = false;
                        try {
                            messageListener.onMessage(session, message);
                            handlerCompleted = true;
                            session.commit();
                            return null;
                        } catch (Exception | Error e) {
                            safeTelemetry(() -> current.observeError(e));
                            try {
                                session.rollback();
                            } catch (Exception rollbackFailure) {
                                if (rollbackFailure != e) {
                                    e.addSuppressed(rollbackFailure);
                                }
                                throw e; // Discard the session even for a non-JMS handler error.
                            }
                            if (handlerCompleted || e instanceof JMSException || e instanceof Error || e instanceof InterruptedException) {
                                throw e;
                            }
                            return (Exception) e;
                        }
                    } finally {
                        ended.set(true);
                        safeTelemetry(current::end);
                    }
                });
        } finally {
            if (!ended.get()) {
                safeTelemetry(current::end);
            }
            if (previousMdc == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousMdc);
            }
        }
    }

    @FunctionalInterface
    private interface TelemetryAction {

        void run() throws Exception;
    }

    private void safeTelemetry(TelemetryAction action) {
        try {
            action.run();
        } catch (Exception e) {
            logger.warn("JMS telemetry failed for '{}'", config.queueName(), e);
        }
    }
}
