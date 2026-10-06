package io.koraframework.nats.common;

import io.koraframework.nats.common.consumer.*;
import io.koraframework.nats.common.exceptions.NatsAtomicBatchException;
import io.koraframework.nats.common.exceptions.NatsPublishException;
import io.koraframework.nats.common.exceptions.NatsSkipRecordException;
import io.koraframework.nats.common.producer.*;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.Options;
import io.nats.client.PublishOptions;
import io.nats.client.api.*;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@Timeout(30)
class NatsIntegrationTest {
    @TempDir
    static Path serverDirectory;
    private static GenericContainer<?> docker;
    private static Process server;
    private static int port;
    private static String executable;
    private static String url;
    private NatsClient client;
    private final NatsModule serialization = new NatsModule() {
    };

    @BeforeAll
    static void startServer() throws Exception {
        executable = System.getProperty("nats.server.executable");
        if (executable != null) {
            try (var socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            startNative();
            url = "nats://127.0.0.1:" + port;
        } else {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "NATS broker tests require Docker or -PnatsServerExecutable=/path/to/nats-server");
            docker = new GenericContainer<>(DockerImageName.parse("nats:2.12.8-alpine"))
                .withExposedPorts(4222).withCommand("-js");
            docker.start();
            url = "nats://" + docker.getHost() + ":" + docker.getMappedPort(4222);
        }
    }

    private static void startNative() throws Exception {
        server = new ProcessBuilder(executable, "-js", "-a", "127.0.0.1", "-p", Integer.toString(port),
            "-sd", serverDirectory.resolve("data").toString()).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(serverDirectory.resolve("server.log").toFile())).start();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            try (var socket = new Socket("127.0.0.1", port)) {
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static void stopNative() throws Exception {
        if (server == null) {
            return;
        }
        server.destroy();
        if (!server.waitFor(3, TimeUnit.SECONDS)) {
            server.destroyForcibly();
            server.waitFor(3, TimeUnit.SECONDS);
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        stopNative();
        if (docker != null) {
            docker.stop();
        }
    }

    @BeforeEach
    void connect() throws Exception {
        client = TestSupport.client(url);
        client.init();
    }

    @AfterEach
    void disconnect() throws Exception {
        if (client != null) {
            client.release();
        }
    }

    private String stream(String subject) throws Exception {
        var name = "S" + UUID.randomUUID().toString().replace("-", "");
        client.jetStreamManagement().addStream(StreamConfiguration.builder().name(name).subjects(subject).storageType(StorageType.Memory).build());
        return name;
    }

    private String atomicStream(String subject) throws Exception {
        var name = "A" + UUID.randomUUID().toString().replace("-", "");
        client.jetStreamManagement().addStream(StreamConfiguration.builder().name(name).subjects(subject)
            .storageType(StorageType.Memory).allowAtomicPublish().build());
        return name;
    }

    private AtomicBatchPublisherImpl<AtomicEvents> atomicPublisher(String stream) {
        var config = new NatsAtomicBatchConfig() {
            public String stream() {
                return stream;
            }
        };
        var publisherConfig = TestSupport.publisher(NatsPublisherConfig.Mode.JETSTREAM);
        return new AtomicBatchPublisherImpl<>(client, publisherConfig, config, TestSupport.PUBLISHER_TELEMETRY,
            sink -> new AtomicEvents(client, publisherConfig, sink));
    }

    private Message atomicMessage(String subject, String body) {
        return io.nats.client.impl.NatsMessage.builder().subject(subject).data(body.getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    void atomicBatchStoresAllSubjectsTogetherAndReturnsBatchAck() throws Exception {
        var prefix = "atomic.commit." + UUID.randomUUID();
        var stream = atomicStream(prefix + ".>");
        try (var batch = atomicPublisher(stream).begin()) {
            batch.publisher().send(atomicMessage(prefix + ".a", "one"));
            batch.publisher().send(atomicMessage(prefix + ".b", "two"));
            batch.publisher().send(atomicMessage(prefix + ".a", "three"));
            assertThat(client.jetStreamManagement().getStreamInfo(stream).getStreamState().getMsgCount()).isZero();
            var ack = batch.commit();
            assertThat(ack.getStream()).isEqualTo(stream);
            assertThat(ack.getBatchId()).isEqualTo(batch.id());
            assertThat(ack.getBatchSize()).isEqualTo(3);
            assertThat(ack.getSeqno()).isEqualTo(3);
            assertThat(batch.commit()).isSameAs(ack);
            assertThat(client.jetStreamManagement().getStreamInfo(stream).getStreamState().getMsgCount()).isEqualTo(3);
            var first = client.jetStreamManagement().getMessage(stream, 1);
            var last = client.jetStreamManagement().getMessage(stream, 3);
            assertThat(first.getData()).isEqualTo("one".getBytes(StandardCharsets.UTF_8));
            assertThat(first.getHeaders().getFirst("Nats-Batch-Sequence")).isEqualTo("1");
            assertThat(last.getHeaders().getFirst("Nats-Batch-Commit")).isEqualTo("1");
        }
    }

    @Test
    void atomicBatchCanCommitOneMessage() throws Exception {
        var subject = "atomic.single." + UUID.randomUUID();
        var stream = atomicStream(subject);
        var ack = atomicPublisher(stream).inBatch(events -> events.send(atomicMessage(subject, "one")));
        assertThat(ack.getBatchSize()).isEqualTo(1);
        assertThat(client.jetStreamManagement().getStreamInfo(stream).getStreamState().getMsgCount()).isEqualTo(1);
    }

    @Test
    void atomicBatchCloseAndApplicationFailureStoreNothing() throws Exception {
        var subject = "atomic.abort." + UUID.randomUUID();
        var stream = atomicStream(subject);
        var publisher = atomicPublisher(stream);
        try (var batch = publisher.begin()) {
            batch.publisher().send(atomicMessage(subject, "discard"));
        }
        assertThatThrownBy(() -> publisher.inBatch(events -> {
            events.send(atomicMessage(subject, "discard"));
            throw new IllegalArgumentException("application failure");
        })).hasMessage("application failure");
        assertThat(client.jetStreamManagement().getStreamInfo(stream).getStreamState().getMsgCount()).isZero();
    }

    @Test
    void atomicBatchConstraintFailureDoesNotStoreStagedMessages() throws Exception {
        var subject = "atomic.constraint." + UUID.randomUUID();
        var stream = atomicStream(subject);
        try (var batch = atomicPublisher(stream).begin()) {
            batch.publisher().send(io.nats.client.impl.NatsMessage.builder().subject(subject).headers(new Headers().put("Nats-Expected-Last-Sequence", "999"))
                .data("one".getBytes(StandardCharsets.UTF_8)).build());
            batch.publisher().send(atomicMessage(subject, "two"));
            assertThatThrownBy(batch::commit).isInstanceOfSatisfying(NatsAtomicBatchException.class, failure -> {
                assertThat(failure.outcome()).isEqualTo(NatsAtomicBatchException.Outcome.ABORTED);
                assertThat(failure.getCause()).isInstanceOf(JetStreamApiException.class);
            });
        }
        assertThat(client.jetStreamManagement().getStreamInfo(stream).getStreamState().getMsgCount()).isZero();
    }

    @Test
    void atomicBatchRejectsMessagesRoutedToAnotherStream() throws Exception {
        var prefix = "atomic.cross." + UUID.randomUUID();
        var target = atomicStream(prefix + ".a");
        var other = atomicStream(prefix + ".b");
        try (var batch = atomicPublisher(target).begin()) {
            batch.publisher().send(atomicMessage(prefix + ".a", "one"));
            batch.publisher().send(atomicMessage(prefix + ".b", "two"));
            assertThatThrownBy(batch::commit).isInstanceOfSatisfying(NatsAtomicBatchException.class,
                failure -> assertThat(failure.outcome()).isEqualTo(NatsAtomicBatchException.Outcome.ABORTED));
        }
        assertThat(client.jetStreamManagement().getStreamInfo(target).getStreamState().getMsgCount()).isZero();
        assertThat(client.jetStreamManagement().getStreamInfo(other).getStreamState().getMsgCount()).isZero();
    }

    @Test
    void atomicBatchRequiresEnabledStream() throws Exception {
        var subject = "atomic.disabled." + UUID.randomUUID();
        var stream = stream(subject);
        assertThatThrownBy(atomicPublisher(stream)::begin).hasMessageContaining("must enable allow_atomic");
        assertThat(client.jetStreamManagement().getStreamInfo(stream).getStreamState().getMsgCount()).isZero();
    }

    @Test
    void atomicBatchDuplicateMessageIdsRejectEntireBatch() throws Exception {
        var subject = "atomic.duplicate." + UUID.randomUUID();
        var stream = atomicStream(subject);
        try (var batch = atomicPublisher(stream).begin()) {
            for (int i = 0; i < 2; i++) {
                batch.publisher().send(io.nats.client.impl.NatsMessage.builder().subject(subject)
                    .headers(new Headers().put("Nats-Msg-Id", "same-id")).data(new byte[]{1}).build());
            }
            assertThatThrownBy(batch::commit).isInstanceOfSatisfying(NatsAtomicBatchException.class,
                failure -> assertThat(failure.outcome()).isEqualTo(NatsAtomicBatchException.Outcome.ABORTED));
        }
        assertThat(client.jetStreamManagement().getStreamInfo(stream).getStreamState().getMsgCount()).isZero();
    }

    private static final class AtomicEvents extends AbstractNatsPublisher {
        private AtomicEvents(NatsClient client, NatsPublisherConfig config, NatsAtomicBatchSink batch) {
            super("atomic", client, config, TestSupport.PUBLISHER_TELEMETRY, batch);
        }

        void send(Message message) {
            publish(message, null);
        }
    }

    private NatsConsumerContainer<String> container(String name, NatsListenerConfig config, NatsMessageHandler<String> handler) {
        return new NatsConsumerContainer<>(name, client, config, serialization.stringNatsDeserializer(), handler, null, TestSupport.CONSUMER_TELEMETRY);
    }

    @Test
    void coreListenerConsumesMultipleSubjectsOnSharedWorkers() throws Exception {
        var prefix = "multi.core." + UUID.randomUUID();
        var config = TestSupport.listener(null, "workers", null);
        when(config.subjects()).thenReturn(java.util.List.of(prefix + ".a", prefix + ".b"));
        when(config.threads()).thenReturn(2);
        var received = new LinkedBlockingQueue<String>();
        var consumer = container("multi-core", config, (_poll, record) -> received.add(record.value()));
        consumer.init();
        try {
            for (int i = 0; i < 20; i++) {
                client.connection().publish(prefix + (i % 2 == 0 ? ".a" : ".b"), ("value" + i).getBytes(StandardCharsets.UTF_8));
            }
            client.connection().publish(prefix + ".excluded", "excluded".getBytes(StandardCharsets.UTF_8));
            await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 20);
            assertThat(received).doesNotHaveDuplicates().doesNotContain("excluded");
            assertThat(consumer.probe()).isNull();
        } finally {
            consumer.release();
        }
    }

    @Test
    void jetStreamListenerUsesOneMultiFilterConsumerForPullAndPush() throws Exception {
        for (var mode : NatsListenerConfig.Mode.values()) {
            var prefix = "multi.js." + mode + "." + UUID.randomUUID();
            var stream = stream(prefix + ".>");
            var js = TestSupport.jetStream(stream, "workers");
            when(js.mode()).thenReturn(mode);
            var config = TestSupport.listener(null, mode == NatsListenerConfig.Mode.PUSH ? "workers" : null, js);
            var subjects = java.util.List.of(prefix + ".a", prefix + ".b");
            when(config.subjects()).thenReturn(subjects);
            when(config.threads()).thenReturn(2);
            var received = new LinkedBlockingQueue<String>();
            var consumer = container("multi-" + mode, config, (_poll, record) -> received.add(record.value()));
            consumer.init();
            try {
                client.jetStream().publish(prefix + ".a", "a".getBytes(StandardCharsets.UTF_8));
                client.jetStream().publish(prefix + ".b", "b".getBytes(StandardCharsets.UTF_8));
                client.jetStream().publish(prefix + ".excluded", "excluded".getBytes(StandardCharsets.UTF_8));
                await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 2);
                assertThat(received).containsExactlyInAnyOrder("a", "b");
                assertThat(client.jetStreamManagement().getConsumerInfo(stream, "workers").getConsumerConfiguration().getFilterSubjects())
                    .containsExactlyElementsOf(subjects);
                await().atMost(Duration.ofSeconds(5)).until(() -> client.jetStreamManagement().getConsumerInfo(stream, "workers").getNumAckPending() == 0);
            } finally {
                consumer.release();
            }
        }
    }

    @Test
    void backgroundListenerConnectsWhenServerBecomesAvailable() throws Exception {
        Assumptions.assumeTrue(server != null, "Native server restart test");
        var subject = "initial.retry." + UUID.randomUUID();
        var config = TestSupport.listener(subject, null, null);
        var properties = new java.util.Properties();
        properties.put(Options.PROP_URL, url);
        when(config.driverProperties()).thenReturn(properties);
        when(config.initializationFailTimeout()).thenReturn(null);
        when(config.backoffTimeout()).thenReturn(Duration.ofMillis(20));
        var listenerClient = new NatsClient(config, options -> options.connectionTimeout(Duration.ofMillis(100)),
            true, true);
        var received = new LinkedBlockingQueue<String>();
        var consumer = new NatsConsumerContainer<>("initial-retry", listenerClient, config, serialization.stringNatsDeserializer(),
            (NatsMessageHandler<String>) (_poll, record) -> received.add(record.value()), null, TestSupport.CONSUMER_TELEMETRY);
        stopNative();
        try {
            listenerClient.init();
            consumer.init();
            assertThat(listenerClient.probe()).isNotNull();
            assertThat(consumer.probe()).isNotNull();
            startNative();
            await().atMost(Duration.ofSeconds(5)).until(() -> consumer.probe() == null);
            listenerClient.connection().publish(subject, "ready".getBytes(StandardCharsets.UTF_8));
            assertThat(received.poll(3, TimeUnit.SECONDS)).isEqualTo("ready");
        } finally {
            if (server == null || !server.isAlive()) {
                startNative();
            }
            try {
                consumer.release();
            } finally {
                listenerClient.release();
            }
        }
    }

    @Test
    void coreWildcardQueueConsumersShareMessages() throws Exception {
        var subject = "core." + UUID.randomUUID();
        var received = new LinkedBlockingQueue<String>();
        var workerThreads = ConcurrentHashMap.<Thread>newKeySet();
        var config = TestSupport.listener(subject + ".*", "workers", null);
        when(config.threads()).thenReturn(2);
        var consumer = container("core", config, (_poll, record) -> {
            workerThreads.add(Thread.currentThread());
            received.add(record.value());
        });
        consumer.init();
        try {
            for (int i = 0; i < 20; i++) {
                client.connection().publish(subject + ".created", Integer.toString(i).getBytes(StandardCharsets.UTF_8));
            }
            client.connection().flush(Duration.ofSeconds(2));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received).hasSize(20));
            assertThat(received).doesNotHaveDuplicates();
            assertThat(workerThreads).isNotEmpty().hasSizeLessThanOrEqualTo(2).allSatisfy(thread -> {
                assertThat(thread.isVirtual()).isTrue();
                assertThat(thread.getName()).startsWith("nats-core-");
            });
        } finally {
            consumer.release();
        }
    }

    @Test
    void requestReplyAndNoResponders() throws Exception {
        var subject = "request." + UUID.randomUUID();
        var consumer = container("reply", TestSupport.listener(subject, null, null), (_poll, record) ->
            client.connection().publish(record.replyTo(), record.value().toUpperCase().getBytes(StandardCharsets.UTF_8)));
        consumer.init();
        try {
            var publisher = new TestPublisher(client, NatsPublisherConfig.Mode.CORE);
            assertThat(publisher.requestValue(subject, "hello")).isEqualTo("HELLO");
            assertThat(publisher.requestValueAsync(subject, "async").get(3, TimeUnit.SECONDS)).isEqualTo("ASYNC");
            assertThatThrownBy(() -> publisher.requestValue("missing." + UUID.randomUUID(), "hello")).isInstanceOf(NatsPublishException.class);
        } finally {
            consumer.release();
        }
    }

    @Test
    void jetStreamPublishDedupAndExpectedSequence() throws Exception {
        var subject = "dedup." + UUID.randomUUID();
        var stream = stream(subject);
        var publisher = new TestPublisher(client, NatsPublisherConfig.Mode.JETSTREAM);
        var options = PublishOptions.builder().messageId("id").expectedStream(stream).build();
        var first = publisher.send(subject, "hello", options);
        var duplicate = publisher.sendAsync(subject, "hello", options).get(3, TimeUnit.SECONDS);
        assertThat(first.getSeqno()).isEqualTo(1);
        assertThat(duplicate.isDuplicate()).isTrue();
        assertThatThrownBy(() -> publisher.send(subject, "bad", PublishOptions.builder().expectedLastSequence(99).build()))
            .isInstanceOf(NatsPublishException.class);
    }

    @Test
    void pullFailureRedeliversAndSuccessAcknowledges() throws Exception {
        var subject = "pull." + UUID.randomUUID();
        var stream = stream(subject);
        var attempts = new AtomicInteger();
        var received = new LinkedBlockingQueue<String>();
        var consumer = container("pull", TestSupport.listener(subject, null, TestSupport.jetStream(stream, "worker")), (_poll, record) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("retry");
            }
            received.add(record.value());
        });
        consumer.init();
        try {
            client.jetStream().publish(subject, "payload".getBytes(StandardCharsets.UTF_8));
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("payload");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(client.jetStreamManagement().getConsumerInfo(stream, "worker").getNumAckPending()).isZero());
            assertThat(attempts.get()).isEqualTo(2);
        } finally {
            consumer.release();
        }
    }

    @Test
    void explicitNakIsNotOverriddenByAutoAck() throws Exception {
        var subject = "nak." + UUID.randomUUID();
        var stream = stream(subject);
        var received = new AtomicInteger();
        var consumer = container("nak", TestSupport.listener(subject, null, TestSupport.jetStream(stream, "worker")), (_poll, record) -> {
            if (received.incrementAndGet() == 1) {
                record.nakWithDelay(Duration.ofMillis(50));
            }
        });
        consumer.init();
        try {
            client.jetStream().publish(subject, new byte[0]);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received.get()).isEqualTo(2));
        } finally {
            consumer.release();
        }
    }

    @Test
    void poisonMessageTerminates() throws Exception {
        var subject = "poison." + UUID.randomUUID();
        var stream = stream(subject);
        var received = new AtomicInteger();
        var consumer = container("poison", TestSupport.listener(subject, null, TestSupport.jetStream(stream, "worker")), (_poll, record) -> {
            received.incrementAndGet();
            throw new NatsSkipRecordException("invalid payload");
        });
        consumer.init();
        try {
            client.jetStream().publish(subject, new byte[0]);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(received.get()).isEqualTo(1);
                assertThat(client.jetStreamManagement().getConsumerInfo(stream, "worker").getNumAckPending()).isZero();
            });
        } finally {
            consumer.release();
        }
    }

    @Test
    void manualAckRemainsPendingUntilApplicationAcknowledges() throws Exception {
        var subject = "manual." + UUID.randomUUID();
        var stream = stream(subject);
        var js = TestSupport.jetStream(stream, "worker");
        when(js.acknowledgement()).thenReturn(NatsListenerConfig.Acknowledgement.MANUAL);
        var received = new LinkedBlockingQueue<NatsMessage<String>>();
        var consumer = container("manual", TestSupport.listener(subject, null, js), (_poll, record) -> received.add(record));
        consumer.init();
        try {
            client.jetStream().publish(subject, "value".getBytes(StandardCharsets.UTF_8));
            var record = received.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(client.jetStreamManagement().getConsumerInfo(stream, "worker").getNumAckPending()).isEqualTo(1);
            record.ackSync(Duration.ofSeconds(2));
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(client.jetStreamManagement().getConsumerInfo(stream, "worker").getNumAckPending()).isZero());
        } finally {
            consumer.release();
        }
    }

    @Test
    void pushQueueAndSynchronousAck() throws Exception {
        var subject = "push." + UUID.randomUUID();
        var stream = stream(subject);
        var js = TestSupport.jetStream(stream, "worker");
        when(js.mode()).thenReturn(NatsListenerConfig.Mode.PUSH);
        when(js.acknowledgement()).thenReturn(NatsListenerConfig.Acknowledgement.SYNC);
        var config = TestSupport.listener(subject, "queue", js);
        when(config.threads()).thenReturn(2);
        var received = new LinkedBlockingQueue<String>();
        var consumer = container("push", config, (_poll, record) -> received.add(record.value()));
        consumer.init();
        try {
            for (int i = 0; i < 10; i++) {
                client.jetStream().publish(subject, Integer.toString(i).getBytes(StandardCharsets.UTF_8));
            }
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(received).hasSize(10).doesNotHaveDuplicates();
                assertThat(client.jetStreamManagement().getConsumerInfo(stream, "worker").getNumAckPending()).isZero();
            });
        } finally {
            consumer.release();
        }
    }

    @Test
    void batchAcknowledgesEveryMessage() throws Exception {
        var subject = "batch." + UUID.randomUUID();
        var stream = stream(subject);
        for (int i = 0; i < 10; i++) {
            client.jetStream().publish(subject, Integer.toString(i).getBytes(StandardCharsets.UTF_8));
        }
        var received = new LinkedBlockingQueue<NatsMessages<String>>();
        var consumer = new NatsConsumerContainer<>("batch", client,
            TestSupport.listener(subject, null, TestSupport.jetStream(stream, "worker")), serialization.stringNatsDeserializer(),
            (NatsMessagesHandler<String>) (_poll, records) -> received.add(records), null, TestSupport.CONSUMER_TELEMETRY);
        consumer.init();
        try {
            var batch = received.poll(5, TimeUnit.SECONDS);
            assertThat(batch).isNotNull();
            assertThat(batch.count()).isEqualTo(10);
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(client.jetStreamManagement().getConsumerInfo(stream, "worker").getNumAckPending()).isZero());
        } finally {
            consumer.release();
        }
    }

    @Test
    void boundParallelPushRejectsEffectiveAckAll() throws Exception {
        var subject = "bind." + UUID.randomUUID();
        var stream = stream(subject);
        client.jetStreamManagement().addOrUpdateConsumer(stream, ConsumerConfiguration.builder()
            .durable("worker").filterSubject(subject).deliverSubject("delivery." + UUID.randomUUID())
            .deliverGroup("queue").ackPolicy(AckPolicy.All).build());
        var js = TestSupport.jetStream(stream, "worker");
        when(js.mode()).thenReturn(NatsListenerConfig.Mode.PUSH);
        when(js.bind()).thenReturn(true);
        var config = TestSupport.listener(subject, "queue", js);
        when(config.threads()).thenReturn(2);
        var consumer = container("bound", config, (_poll, record) -> {
        });
        try {
            assertThatThrownBy(consumer::init).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AckPolicy.All");
        } finally {
            consumer.release();
        }
    }

    @Test
    void shutdownDrainsAllSubjectsAndWaitsForInFlightHandler() throws Exception {
        var subject = "shutdown." + UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var handled = new java.util.concurrent.atomic.AtomicInteger();
        var config = TestSupport.listener(null, null, null);
        when(config.subjects()).thenReturn(java.util.List.of(subject + ".a", subject + ".b"));
        when(config.batchSize()).thenReturn(1);
        var consumer = container("shutdown", config, (_poll, record) -> {
            entered.countDown();
            finish.await(5, TimeUnit.SECONDS);
            handled.incrementAndGet();
        });
        consumer.init();
        client.connection().publish(subject + ".a", new byte[0]);
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 20; i++) {
            client.connection().publish(subject + ".b", new byte[0]);
        }
        client.connection().flush(Duration.ofSeconds(2));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var stopped = executor.submit(() -> {
                consumer.release();
                return null;
            });
            try {
                assertThatThrownBy(() -> stopped.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                finish.countDown();
            }
            stopped.get(5, TimeUnit.SECONDS);
        }
        assertThat(handled.get()).isEqualTo(21);
    }

    @Test
    void keyValueHistoryAndCas() throws Exception {
        var bucket = "KV" + UUID.randomUUID().toString().replace("-", "");
        client.keyValueManagement().create(KeyValueConfiguration.builder().name(bucket).maxHistoryPerKey(5).build());
        var kv = client.keyValue(bucket);
        var revision = kv.put("key", "one");
        var next = kv.update("key", "two", revision);
        assertThat(kv.get("key").getValueAsString()).isEqualTo("two");
        assertThat(kv.history("key")).hasSize(2);
        assertThatThrownBy(() -> kv.update("key", "stale", revision)).isInstanceOf(JetStreamApiException.class);
        kv.delete("key", next);
        assertThat(kv.get("key")).isNull();
        assertThat(kv.history("key").getLast().getOperation()).isEqualTo(KeyValueOperation.DELETE);
    }

    @Test
    void objectStoreRoundTrip() throws Exception {
        var bucket = "OS" + UUID.randomUUID().toString().replace("-", "");
        client.objectStoreManagement().create(ObjectStoreConfiguration.builder().name(bucket).build());
        var store = client.objectStore(bucket);
        var bytes = "object payload".getBytes(StandardCharsets.UTF_8);
        store.put("file", bytes);
        var output = new ByteArrayOutputStream();
        store.get("file", output);
        assertThat(output.toByteArray()).isEqualTo(bytes);
        store.delete("file");
        assertThat(store.getInfo("file", true).isDeleted()).isTrue();
    }

    @Test
    void annotationConfigsConnectToIndependentServers() throws Exception {
        Process secondServer = null;
        GenericContainer<?> secondDocker = null;
        String secondUrl;
        if (executable != null) {
            int secondPort;
            try (var socket = new ServerSocket(0)) {
                secondPort = socket.getLocalPort();
            }
            secondServer = new ProcessBuilder(executable, "-js", "-a", "127.0.0.1", "-p", Integer.toString(secondPort),
                "-sd", serverDirectory.resolve("second").toString()).redirectErrorStream(true)
                .redirectOutput(serverDirectory.resolve("second.log").toFile()).start();
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                try (var socket = new Socket("127.0.0.1", secondPort)) {
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
            secondUrl = "nats://127.0.0.1:" + secondPort;
        } else {
            secondDocker = new GenericContainer<>(DockerImageName.parse("nats:2.12.8-alpine")).withExposedPorts(4222).withCommand("-js");
            secondDocker.start();
            secondUrl = "nats://" + secondDocker.getHost() + ":" + secondDocker.getMappedPort(4222);
        }
        var firstConfig = mock(NatsPublisherConfig.class, CALLS_REAL_METHODS);
        var firstProperties = new java.util.Properties();
        firstProperties.put(Options.PROP_URL, url);
        when(firstConfig.driverProperties()).thenReturn(firstProperties);
        var subject = "isolated." + UUID.randomUUID();
        var secondConfig = TestSupport.listener(subject, null, null);
        var secondProperties = new java.util.Properties();
        secondProperties.put(Options.PROP_URL, secondUrl);
        when(secondConfig.driverProperties()).thenReturn(secondProperties);
        var first = new NatsClient(firstConfig, options -> options.connectionName("first"));
        var second = new NatsClient(secondConfig, options -> options.connectionName("second"));
        try {
            first.init();
            second.init();
            var firstMessages = first.connection().subscribe(subject);
            var secondMessages = second.connection().subscribe(subject);
            first.connection().flush(Duration.ofSeconds(2));
            second.connection().flush(Duration.ofSeconds(2));
            first.connection().publish(subject, "first".getBytes(StandardCharsets.UTF_8));
            assertThat(new String(firstMessages.nextMessage(Duration.ofSeconds(2)).getData(), StandardCharsets.UTF_8)).isEqualTo("first");
            assertThat(secondMessages.nextMessage(Duration.ofMillis(100))).isNull();
            first.release();
            second.connection().publish(subject, "second".getBytes(StandardCharsets.UTF_8));
            assertThat(new String(secondMessages.nextMessage(Duration.ofSeconds(2)).getData(), StandardCharsets.UTF_8)).isEqualTo("second");
            assertThat(second.probe()).isNull();
        } finally {
            try {
                first.release();
            } finally {
                try {
                    second.release();
                } finally {
                    if (secondServer != null) {
                        secondServer.destroy();
                        if (!secondServer.waitFor(3, TimeUnit.SECONDS)) {
                            secondServer.destroyForcibly();
                            secondServer.waitFor(3, TimeUnit.SECONDS);
                        }
                    }
                    if (secondDocker != null) {
                        secondDocker.stop();
                    }
                }
            }
        }
    }

    @Test
    void nativeKvObjectStoreApisAreAccessibleDirectly() throws Exception {
        var nativeClient = new NatsClient(new NatsConnectionConfig() {
            @Override
            public boolean driverMetricsEnabled() {
                return true;
            }
        }, options -> options.server(url));
        try {
            nativeClient.init();
            assertThat(nativeClient.options().isTrackAdvancedStats()).isTrue();
            var bucket = "KV" + UUID.randomUUID().toString().replace("-", "");
            nativeClient.keyValueManagement().create(KeyValueConfiguration.builder().name(bucket).maxHistoryPerKey(2).build());
            var kv = nativeClient.keyValue(bucket);
            var revision = kv.put("key", "one");
            kv.update("key", "two", revision);
            assertThatThrownBy(() -> kv.update("key", "stale", revision)).isInstanceOf(JetStreamApiException.class);
            assertThat(kv.get("key").getValueAsString()).isEqualTo("two");
            var objectBucket = "OS" + UUID.randomUUID().toString().replace("-", "");
            nativeClient.objectStoreManagement().create(ObjectStoreConfiguration.builder().name(objectBucket).build());
            var store = nativeClient.objectStore(objectBucket);
            store.put("file", "payload".getBytes(StandardCharsets.UTF_8));
            var output = new ByteArrayOutputStream();
            store.get("file", output);
            assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(nativeClient.connection().getStatistics().getOutMsgs()).isGreaterThan(0);
        } finally {
            nativeClient.release();
        }
    }

    @Test
    void reconnectRestoresCoreSubscription() throws Exception {
        Assumptions.assumeTrue(server != null, "Native server restart test");
        var subject = "reconnect." + UUID.randomUUID();
        var received = new LinkedBlockingQueue<String>();
        var consumer = container("reconnect", TestSupport.listener(subject, null, null), (_poll, record) -> received.add(record.value()));
        consumer.init();
        try {
            stopNative();
            await().atMost(Duration.ofSeconds(5)).until(() -> client.probe() != null);
            startNative();
            await().atMost(Duration.ofSeconds(10)).until(() -> client.probe() == null);
            client.connection().publish(subject, "after restart".getBytes(StandardCharsets.UTF_8));
            client.connection().flush(Duration.ofSeconds(2));
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("after restart");
        } finally {
            consumer.release();
        }
    }

    static final class TestPublisher extends AbstractNatsPublisher {
        private final NatsModule serializers = new NatsModule() {
        };

        TestPublisher(NatsClient client, NatsPublisherConfig.Mode mode) {
            super("test", client, TestSupport.publisher(mode), TestSupport.PUBLISHER_TELEMETRY);
        }

        Message record(String subject, String value) {
            return io.nats.client.impl.NatsMessage.builder().subject(subject).data(value).headers(new Headers()).build();
        }

        PublishAck send(String subject, String value, PublishOptions options) {
            return publishAcknowledged(record(subject, value), options);
        }

        CompletableFuture<PublishAck> sendAsync(String subject, String value, PublishOptions options) {
            return publishAsync(record(subject, value), options);
        }

        String requestValue(String subject, String value) {
            return request(record(subject, value), serializers.stringNatsDeserializer(), Duration.ofSeconds(2));
        }

        CompletableFuture<String> requestValueAsync(String subject, String value) {
            return requestAsync(record(subject, value), serializers.stringNatsDeserializer(), Duration.ofSeconds(2));
        }
    }
}
