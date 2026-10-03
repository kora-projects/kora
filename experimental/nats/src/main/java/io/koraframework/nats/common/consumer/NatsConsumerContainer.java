package io.koraframework.nats.common.consumer;

import io.koraframework.common.Configurer;
import io.koraframework.common.executor.LimitedVirtualThreadPerTaskExecutor;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.logging.common.MDC;
import io.koraframework.nats.common.NatsClient;
import io.koraframework.nats.common.consumer.deserializer.NatsDeserializer;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerPollObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerRecordObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetry;
import io.koraframework.nats.common.exceptions.NatsSkipRecordException;
import io.nats.client.*;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.impl.AckType;
import io.opentelemetry.context.Context;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core and JetStream worker lifecycle. Initial connection and subscriptions retry in the background,
 * with optional initializationFailTimeout waiting. Each worker runs in a virtual thread;
 * subscription pending limits bound incoming message queues.
 */
public final class NatsConsumerContainer<T> implements GeneratedNatsListener, ReadinessProbe {
    private final Logger logger = LoggerFactory.getLogger(NatsConsumerContainer.class);
    private final String name;
    private final NatsClient client;
    private final NatsListenerConfig config;
    private final List<String> subjects;
    private final NatsDeserializer<T> deserializer;
    private final @Nullable NatsMessageHandler<T> recordHandler;
    private final @Nullable NatsMessagesHandler<T> recordsHandler;
    private final @Nullable Configurer<ConsumerConfiguration.Builder> configurer;
    private final NatsConsumerTelemetry telemetry;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicInteger liveWorkers = new AtomicInteger();
    private final AtomicInteger readyWorkers = new AtomicInteger();
    private volatile boolean running;
    private volatile boolean stopping;
    private volatile AckPolicy effectiveAckPolicy = AckPolicy.Explicit;
    private @Nullable ExecutorService executor;

    public NatsConsumerContainer(String name, NatsClient client, NatsListenerConfig config, NatsDeserializer<T> deserializer,
                                 NatsMessageHandler<T> handler, @Nullable Configurer<ConsumerConfiguration.Builder> configurer, NatsConsumerTelemetry telemetry) {
        this(name, client, config, deserializer, handler, null, configurer, telemetry);
    }

    public NatsConsumerContainer(String name, NatsClient client, NatsListenerConfig config, NatsDeserializer<T> deserializer,
                                 NatsMessagesHandler<T> handler, @Nullable Configurer<ConsumerConfiguration.Builder> configurer, NatsConsumerTelemetry telemetry) {
        this(name, client, config, deserializer, null, handler, configurer, telemetry);
    }

    private NatsConsumerContainer(String name, NatsClient client, NatsListenerConfig config, NatsDeserializer<T> deserializer,
                                  @Nullable NatsMessageHandler<T> recordHandler, @Nullable NatsMessagesHandler<T> recordsHandler,
                                  @Nullable Configurer<ConsumerConfiguration.Builder> configurer, NatsConsumerTelemetry telemetry) {
        this.name = name;
        this.client = client;
        this.config = config;
        this.subjects = resolveSubjects(config);
        this.deserializer = deserializer;
        this.recordHandler = recordHandler;
        this.recordsHandler = recordsHandler;
        this.configurer = configurer;
        this.telemetry = telemetry;
        validate();
    }

    private void validate() {
        if (config.threads() < 0 || config.batchSize() <= 0) {
            throw new IllegalArgumentException("NATS threads must be >= 0 and batchSize > 0");
        }
        if (config.pendingMessages() < -1 || config.pendingBytes() < -1) {
            throw new IllegalArgumentException("NATS pending limits must be >= -1");
        }
        if (config.pollTimeout().isNegative() || config.pollTimeout().isZero()
            || config.shutdownWait().isNegative() || config.shutdownWait().isZero() || config.backoffTimeout().isNegative()) {
            throw new IllegalArgumentException("NATS pollTimeout and shutdownWait must be positive; backoffTimeout must be >= 0");
        }
        if (config.initializationFailTimeout() != null && config.initializationFailTimeout().isNegative()) {
            throw new IllegalArgumentException("NATS initializationFailTimeout must be >= 0");
        }
        var js = config.jetStream();
        if (config.threads() > 1 && config.queue() == null && (js == null || js.mode() == NatsListenerConfig.Mode.PUSH)) {
            throw new IllegalArgumentException("NATS Core/PUSH listeners with multiple threads require a queue");
        }
        if (js != null) {
            if (js.bind() && js.durable() == null) {
                throw new IllegalArgumentException("NATS bind requires durable consumer name");
            }
            if (config.threads() > 1 && js.durable() == null) {
                throw new IllegalArgumentException("NATS parallel JetStream listeners require a shared durable consumer");
            }
            if (js.nakDelay().isNegative() || js.ackTimeout().isNegative() || js.ackTimeout().isZero()) {
                throw new IllegalArgumentException("NATS nakDelay must be >= 0 and ackTimeout > 0");
            }
            if (js.mode() == NatsListenerConfig.Mode.PULL && config.queue() != null) {
                throw new IllegalArgumentException("NATS pull consumers share durable name instead of queue");
            }
        }
    }

    private static List<String> resolveSubjects(NatsListenerConfig config) {
        var values = config.subjects();
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("NATS listener subjects must contain at least one non-blank subject");
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private List<Subscription> subscribeAll() throws Exception {
        var result = new ArrayList<Subscription>();
        try {
            if (config.jetStream() == null) {
                for (var subject : subjects) {
                    result.add(subscribe(subject));
                }
            } else {
                result.add(subscribe(subjects.size() == 1 ? subjects.getFirst() : null));
            }
            return result;
        } catch (Exception | Error e) {
            for (var subscription : result) {
                subscription.unsubscribe();
            }
            throw e;
        }
    }

    private Subscription subscribe(@Nullable String subject) throws Exception {
        var observation = telemetry.observeOperation("subscribe");
        try {
            return createSubscription(subject);
        } catch (Exception | Error e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    private Subscription createSubscription(@Nullable String subject) throws Exception {
        var js = config.jetStream();
        Subscription subscription;
        if (js == null) {
            subscription = config.queue() == null ? client.connection().subscribe(subject)
                : client.connection().subscribe(subject, config.queue());
        } else {
            var builder = ConsumerConfiguration.builder().ackPolicy(js.ackPolicy()).deliverPolicy(js.deliverPolicy())
                .replayPolicy(js.replayPolicy()).ackWait(js.ackWait()).maxDeliver(js.maxDeliver()).maxAckPending(js.maxAckPending());
            if (js.consumerConfiguration() != null) {
                builder.json(js.consumerConfiguration());
            }
            if (configurer != null) {
                builder = java.util.Objects.requireNonNull(configurer.configure(builder), "NATS consumer configurer returned null");
            }
            if (subjects.size() > 1 && !js.bind()) {
                builder.filterSubjects(subjects);
            }
            var consumer = builder.build();
            if (js.mode() == NatsListenerConfig.Mode.PULL) {
                var options = PullSubscribeOptions.builder().stream(js.stream()).durable(js.durable()).bind(js.bind());
                if (!js.bind()) {
                    options.configuration(consumer);
                }
                subscription = client.jetStream().subscribe(subject, options.build());
            } else {
                var options = PushSubscribeOptions.builder().stream(js.stream()).durable(js.durable()).bind(js.bind());
                if (!js.bind()) {
                    options.configuration(consumer);
                }
                subscription = config.queue() == null ? client.jetStream().subscribe(subject, options.build())
                    : client.jetStream().subscribe(subject, config.queue(), options.build());
            }
            try {
                var actual = ((JetStreamSubscription) subscription).getConsumerInfo().getConsumerConfiguration();
                effectiveAckPolicy = actual.getAckPolicy();
                if (js.bind() && subjects.size() > 1 && (actual.getFilterSubjects() == null
                    || !new LinkedHashSet<>(actual.getFilterSubjects()).equals(new LinkedHashSet<>(subjects)))) {
                    throw new IllegalArgumentException("NATS bound consumer filter subjects do not match listener subjects");
                }
                if (effectiveAckPolicy == AckPolicy.All && config.threads() > 1) {
                    throw new IllegalArgumentException("NATS AckPolicy.All cannot safely acknowledge parallel handlers");
                }
            } catch (Exception e) {
                subscription.unsubscribe();
                throw e;
            }
        }
        try {
            subscription.setPendingLimits(config.pendingMessages(), config.pendingBytes());
        } catch (RuntimeException e) {
            subscription.unsubscribe();
            throw e;
        }
        return subscription;
    }

    @Override
    public synchronized void init() throws Exception {
        if (running || config.threads() == 0) {
            return;
        }
        if (liveWorkers.get() != 0) {
            throw new IllegalStateException("NATS listener still has workers from previous shutdown: " + name);
        }
        var initializingWorkers = new AtomicInteger(config.threads());
        var initialized = new CompletableFuture<Void>();
        var observation = telemetry.observeOperation("listener.start");
        try {
            stopping = false;
            running = true;
            executor = new LimitedVirtualThreadPerTaskExecutor(config.threads(), "nats-" + name + "-");
            for (int i = 0; i < config.threads(); i++) {
                liveWorkers.incrementAndGet();
                try {
                    executor.execute(() -> {
                        try {
                            runWorker(initializingWorkers, initialized);
                        } finally {
                            liveWorkers.decrementAndGet();
                        }
                    });
                } catch (RuntimeException e) {
                    liveWorkers.decrementAndGet();
                    throw e;
                }
            }
            var timeout = config.initializationFailTimeout();
            if (timeout != null) {
                try {
                    initialized.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
                } catch (TimeoutException e) {
                    throw new TimeoutException("NATS listener failed to start within initializationFailTimeout: " + name);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof Exception failure) {
                        throw failure;
                    }
                    if (e.getCause() instanceof Error failure) {
                        throw failure;
                    }
                    throw new IllegalStateException("NATS listener initialization failed: " + name, e.getCause());
                }
            }
        } catch (Exception | Error e) {
            observation.observeError(e);
            try {
                release();
            } catch (Exception cleanup) {
                e.addSuppressed(cleanup);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw e;
        } finally {
            observation.end();
        }
    }

    private void runWorker(AtomicInteger initializingWorkers, CompletableFuture<Void> initialized) {
        var current = new ArrayList<Subscription>();
        boolean ready = false;
        boolean initializedWorker = false;
        try {
            while (running && !stopping) {
                try {
                    client.ensureConnected();
                    current.addAll(subscribeAll());
                    client.connection().flush(config.pollTimeout());
                    if (!running || stopping) {
                        return;
                    }
                    subscriptions.addAll(current);
                    readyWorkers.incrementAndGet();
                    ready = true;
                    if (!initializedWorker) {
                        initializedWorker = true;
                        if (initializingWorkers.decrementAndGet() == 0) {
                            initialized.complete(null);
                        }
                    }
                    poll(current);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IllegalArgumentException e) {
                    initialized.completeExceptionally(e);
                    logger.atWarn().addKeyValue("listener", name).setCause(e).log("NATS listener configuration is invalid");
                    return;
                } catch (Exception e) {
                    if (!running || stopping) {
                        return;
                    }
                    if (ready) {
                        readyWorkers.decrementAndGet();
                        ready = false;
                    }
                    subscriptions.removeAll(current);
                    for (var subscription : current) {
                        subscription.unsubscribe();
                    }
                    current.clear();
                    logger.atWarn().addKeyValue("listener", name).setCause(e).log("NATS listener initialization failed");
                    if (!backoff()) {
                        return;
                    }
                }
            }
        } catch (Error e) {
            initialized.completeExceptionally(e);
            throw e;
        } finally {
            if (ready) {
                readyWorkers.decrementAndGet();
            }
            // During shutdown release() owns registered subscriptions until all drains finish.
            for (var subscription : current) {
                if (subscription.isActive() && (!stopping || !subscriptions.contains(subscription))) {
                    subscription.unsubscribe();
                }
            }
            if (!stopping) {
                subscriptions.removeAll(current);
            }
        }
    }

    @Override
    public @Nullable ReadinessProbeFailure probe() {
        if (!config.readinessProbe() || config.threads() == 0) {
            return null;
        }
        var expectedSubscriptions = config.threads() * (config.jetStream() == null ? subjects.size() : 1);
        return running && !stopping && liveWorkers.get() == config.threads() && readyWorkers.get() == config.threads()
            && subscriptions.size() == expectedSubscriptions && subscriptions.stream().allMatch(Subscription::isActive)
            ? null : new ReadinessProbeFailure("NATS listener workers are not ready: " + name);
    }

    private void poll(List<Subscription> current) {
        var js = config.jetStream();
        var pull = js != null && js.mode() == NatsListenerConfig.Mode.PULL;
        while (running && (!stopping || !pull)) {
            var pollObservation = telemetry.observePoll();
            var polled = new ArrayList<NatsMessage<T>>();
            Throwable pollFailure = null;
            try {
                if (stopping) {
                    current.removeIf(subscription -> !subscription.isActive());
                    if (current.isEmpty()) {
                        return;
                    }
                } else if (current.isEmpty() || current.stream().anyMatch(subscription -> !subscription.isActive())) {
                    subscriptions.removeAll(current);
                    for (var subscription : current) {
                        if (subscription.isActive()) {
                            subscription.unsubscribe();
                        }
                    }
                    current.clear();
                    current.addAll(subscribeAll());
                    client.connection().flush(config.pollTimeout());
                    subscriptions.addAll(current);
                }
                List<Message> messages;
                if (pull) {
                    messages = ((JetStreamSubscription) current.getFirst()).fetch(config.batchSize(), config.pollTimeout());
                } else {
                    messages = new ArrayList<>(config.batchSize());
                    var wait = Duration.ofNanos(Math.max(1, config.pollTimeout().toNanos() / current.size()));
                    for (var subscription : current) {
                        if (messages.size() == config.batchSize()) {
                            break;
                        }
                        if (!subscription.isActive()) {
                            continue;
                        }
                        try {
                            if (!messages.isEmpty() && subscription.getPendingMessageCount() == 0) {
                                continue;
                            }
                            var first = subscription.nextMessage(wait);
                            if (first != null) {
                                messages.add(first);
                            }
                            while (messages.size() < config.batchSize() && subscription.getPendingMessageCount() > 0) {
                                var next = subscription.nextMessage(1);
                                if (next == null) {
                                    break;
                                }
                                messages.add(next);
                            }
                        } catch (IllegalStateException e) {
                            if (!stopping || subscription.isActive()) {
                                throw e;
                            }
                        }
                    }
                    if (current.size() > 1) {
                        Collections.rotate(current, -1);
                    }
                }
                var records = polled;
                for (var message : messages) {
                    if (!message.isStatusMessage()) {
                        var recordObservation = pollObservation.observeRecord(message);
                        recordObservation.observeDeserializer(deserializer);
                        records.add(new NatsMessage<>(message, deserializer, recordObservation));
                        if (message.isJetStream() && message.metaData() != null) {
                            var metadata = message.metaData();
                            telemetry.reportLag(metadata.getStream(), metadata.getConsumer(), metadata.pendingCount());
                        }
                    }
                }
                var batch = new NatsMessages<>(records);
                pollObservation.observeRecords(batch);
                for (var subscription : current) {
                    var destination = js == null ? subscription.getSubject() : String.join(",", subjects);
                    telemetry.reportPending(destination, subscription.getPendingMessageCount(), subscription.getPendingByteCount());
                }
                if (recordsHandler != null) {
                    if (!records.isEmpty() || config.allowEmptyRecords()) {
                        processBatch(pollObservation, batch);
                    }
                } else {
                    for (var record : records) {
                        processRecord(pollObservation, record);
                    }
                }
            } catch (InterruptedException e) {
                pollFailure = e;
                pollObservation.observeError(e);
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                pollFailure = e;
                pollObservation.observeError(e);
                if (!running || stopping) {
                    return;
                }
                logger.atWarn().addKeyValue("listener", name).setCause(e).log("NATS listener polling failed");
                if (!backoff()) {
                    return;
                }
            } catch (Error e) {
                pollFailure = e;
                pollObservation.observeError(e);
                throw e;
            } finally {
                for (var record : polled) {
                    record.observation().end(pollFailure);
                }
                pollObservation.end();
            }
        }
    }

    private boolean backoff() {
        try {
            TimeUnit.NANOSECONDS.sleep(config.backoffTimeout().toNanos());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void processRecord(NatsConsumerPollObservation pollObservation, NatsMessage<T> record) throws Exception {
        var observation = record.observation();
        Throwable failure = null;
        try {
            ScopedValue.where(Observation.VALUE, observation)
                .where(OpentelemetryContext.VALUE, Context.root().with(observation.span()))
                .where(MDC.VALUE, new MDC())
                .call(() -> {
                    observation.observeHandle();
                    recordHandler.handle(pollObservation, record);
                    return null;
                });
            acknowledge(record, null);
        } catch (Throwable e) {
            failure = e;
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            acknowledge(record, e);
            if (!(e instanceof NatsSkipRecordException)) {
                logger.atWarn().addKeyValue("listener", name).setCause(e).log("NATS record handler failed");
            }
            if (e instanceof Error error) {
                throw error;
            }
        } finally {
            observation.end(failure);
        }
    }

    private void processBatch(NatsConsumerPollObservation pollObservation, NatsMessages<T> records) throws Exception {
        var observations = new ArrayList<NatsConsumerRecordObservation>(records.count());
        for (var record : records) {
            observations.add(record.observation());
        }
        Throwable failure = null;
        try {
            if (observations.isEmpty()) {
                recordsHandler.handle(pollObservation, records);
            } else {
                var observation = observations.getFirst();
                ScopedValue.where(Observation.VALUE, observation)
                    .where(OpentelemetryContext.VALUE, Context.root().with(observation.span()))
                    .where(MDC.VALUE, new MDC())
                    .call(() -> {
                        observations.forEach(NatsConsumerRecordObservation::observeHandle);
                        recordsHandler.handle(pollObservation, records);
                        return null;
                    });
            }
        } catch (Throwable e) {
            failure = e;
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.atWarn().addKeyValue("listener", name).setCause(e).log("NATS batch handler failed");
            if (e instanceof Error error) {
                throw error;
            }
        } finally {
            Exception ackFailure = null;
            for (int i = 0; i < records.count(); i++) {
                try {
                    acknowledge(records.messages().get(i), failure);
                } catch (Exception e) {
                    if (ackFailure == null) {
                        ackFailure = e;
                    } else {
                        ackFailure.addSuppressed(e);
                    }
                    observations.get(i).observeError(e);
                } finally {
                    if (failure != null) {
                        observations.get(i).observeError(failure);
                    }
                    observations.get(i).end();
                }
            }
            if (ackFailure != null) {
                throw ackFailure;
            }
        }
    }

    private void acknowledge(NatsMessage<T> record, @Nullable Throwable failure) throws Exception {
        var message = record.message();
        var js = config.jetStream();
        if (js == null || !message.isJetStream() || js.acknowledgement() == NatsListenerConfig.Acknowledgement.MANUAL) {
            return;
        }
        if (effectiveAckPolicy == AckPolicy.None) {
            return;
        }
        if (message.lastAck() != null && message.lastAck() != AckType.AckProgress) {
            return;
        }
        if (failure instanceof NatsSkipRecordException) {
            record.term();
        } else if (failure != null) {
            record.nakWithDelay(js.nakDelay());
        } else {
            if (js.acknowledgement() == NatsListenerConfig.Acknowledgement.SYNC) {
                record.ackSync(js.ackTimeout());
            } else {
                record.ack();
            }
        }
    }

    @Override
    public synchronized void release() throws Exception {
        var currentExecutor = executor;
        if (currentExecutor == null) {
            telemetry.close();
            return;
        }
        var observation = telemetry.observeOperation("listener.stop");
        stopping = true;
        long deadline = System.nanoTime() + config.shutdownWait().toNanos();
        try {
            var js = config.jetStream();
            if (js == null || js.mode() == NatsListenerConfig.Mode.PUSH) {
                var drains = new ArrayList<CompletableFuture<Boolean>>();
                for (var subscription : subscriptions) {
                    if (subscription.isActive()) {
                        drains.add(subscription.drain(config.shutdownWait()));
                    }
                }
                CompletableFuture.allOf(drains.toArray(CompletableFuture[]::new))
                    .get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                running = false;
            }
            currentExecutor.shutdown();
            if (!currentExecutor.awaitTermination(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw new TimeoutException("NATS listener shutdown timed out: " + name);
            }
        } catch (InterruptedException e) {
            observation.observeError(e);
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            running = false;
            currentExecutor.shutdownNow();
            for (var subscription : subscriptions) {
                if (subscription.isActive()) {
                    subscription.unsubscribe();
                }
            }
            subscriptions.clear();
            executor = null;
            try {
                observation.end();
            } finally {
                telemetry.close();
            }
        }
    }
}
