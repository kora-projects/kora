package io.koraframework.jms;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerTelemetry;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerTelemetryFactory;
import io.koraframework.jms.telemetry.impl.NoopJmsConsumerTelemetry;
import io.koraframework.jms.util.JmsUtils;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Session;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JmsBrokerIntegrationTest {

    private static final AtomicInteger brokerIds = new AtomicInteger();
    @TempDir
    Path directory;
    private ActiveMQServer server;
    private ActiveMQConnectionFactory factory;
    private JmsMessageListenerContainer container;

    @BeforeEach
    void startBroker() throws Exception {
        int id = brokerIds.incrementAndGet();
        var config = new ConfigurationImpl().setPersistenceEnabled(false)
            .setSecurityEnabled(false)
            .setJournalDirectory(directory.resolve("journal").toString())
            .setBindingsDirectory(directory.resolve("bindings").toString())
            .setPagingDirectory(directory.resolve("paging").toString())
            .setLargeMessagesDirectory(directory.resolve("large-messages").toString())
            .addAcceptorConfiguration("in-vm", "vm://" + id);
        for (var queue : java.util.List.of("test-queue", "replies", "DLQ")) {
            config.addQueueConfiguration(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST).setDurable(false));
        }
        server = ActiveMQServers.newActiveMQServer(config);
        server.getAddressSettingsRepository()
            .addMatch(
                "#",
                new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST)
                    .setDefaultQueueRoutingType(RoutingType.ANYCAST)
                    .setRedeliveryDelay(0)
                    .setMaxDeliveryAttempts(3)
                    .setDeadLetterAddress(SimpleString.of("DLQ"))
            );
        server.start();
        factory = new ActiveMQConnectionFactory("vm://" + id);
        factory.setReconnectAttempts(0);
        factory.setInitialConnectAttempts(0);
        factory.setCallTimeout(1000);
    }

    @AfterEach
    void stopBroker() throws Exception {
        try {
            if (container != null)
                container.release();
        } finally {
            try {
                if (factory != null)
                    factory.close();
            } finally {
                if (server != null)
                    server.stop();
            }
        }
    }

    private JmsMessageListenerContainer startListener(JmsMessageListener listener) {
        container = new JmsMessageListenerContainer(
            factory, new JmsMessageListenerContainerTest.Config(1, true, Duration.ofSeconds(5), Duration.ofSeconds(3)), listener,
            (c, q) -> NoopJmsConsumerTelemetry.INSTANCE
        );
        container.init();
        return container;
    }

    private void send(String text) throws Exception {
        try (var connection = factory.createConnection();
                var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                var producer = session.createProducer(session.createQueue("test-queue"))) {
            producer.send(session.createTextMessage(text));
        }
    }

    @Test
    void receiveAndReplyCommitInSameTransaction() throws Exception {
        startListener((session, message) -> {
            try (var producer = session.createProducer(session.createQueue("replies"))) {
                producer.send(session.createTextMessage("reply:" + JmsUtils.text(message)));
            }
        });
        send("request");
        try (var connection = factory.createConnection();
                var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                var replies = session.createConsumer(session.createQueue("replies"))) {
            connection.start();
            var reply = replies.receive(5000);
            assertThat(reply).isNotNull();
            assertThat(JmsUtils.text(reply)).isEqualTo("reply:request");
            assertThat(replies.receive(100)).isNull();
        }
    }

    @Test
    void rollbackRedeliversInputAndDiscardsReply() throws Exception {
        var deliveries = new AtomicInteger();
        startListener((session, message) -> {
            int attempt = deliveries.incrementAndGet();
            try (var producer = session.createProducer(session.createQueue("replies"))) {
                if (attempt == 1) {
                    producer.send(session.createTextMessage("discarded"));
                    throw new IllegalArgumentException("retry");
                }
                assertThat(message.getJMSRedelivered()).isTrue();
                assertThat(message.getIntProperty("JMSXDeliveryCount")).isEqualTo(2);
                producer.send(session.createTextMessage("accepted"));
            }
        });
        send("request");
        try (var connection = factory.createConnection();
                var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                var replies = session.createConsumer(session.createQueue("replies"))) {
            connection.start();
            var reply = replies.receive(5000);
            assertThat(reply).isNotNull();
            assertThat(JmsUtils.text(reply)).isEqualTo("accepted");
            assertThat(replies.receive(100)).isNull();
            assertThat(deliveries).hasValue(2);
        }
    }

    @Test
    void poisonMessageReachesBrokerDeadLetterQueue() throws Exception {
        var deliveries = new AtomicInteger();
        startListener((session, message) -> {
            deliveries.incrementAndGet();
            throw new IllegalArgumentException("poison");
        });
        send("poison");
        try (var connection = factory.createConnection();
                var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                var deadLetters = session.createConsumer(session.createQueue("DLQ"))) {
            connection.start();
            var message = deadLetters.receive(5000);
            assertThat(message).isNotNull();
            assertThat(JmsUtils.text(message)).isEqualTo("poison");
            assertThat(deliveries).hasValue(3);
        }
    }

    @Test
    void readinessFailsOnBrokerStopAndRecoversAfterRestart() throws Exception {
        var received = new CountDownLatch(1);
        startListener((session, message) -> received.countDown());
        assertThat(container.probe()).isNull();
        server.stop();
        JmsMessageListenerContainerTest.await(() -> container.probe() != null);
        server.start();
        JmsMessageListenerContainerTest.await(() -> container.probe() == null);
        send("after-restart");
        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shutdownUnblocksLongReceive() {
        var config = new JmsListenerContainerConfig() {

            public String queueName() {
                return "test-queue";
            }

            public int threads() {
                return 1;
            }

            public Duration initializationFailTimeout() {
                return Duration.ofSeconds(5);
            }

            public Duration pollTimeout() {
                return Duration.ofSeconds(30);
            }

            public Duration shutdownWait() {
                return Duration.ofSeconds(2);
            }

            public JmsConsumerTelemetryConfig telemetry() {
                return DefaultJmsConsumerTelemetry.TelemetryContext.EMPTY.config();
            }
        };
        container = new JmsMessageListenerContainer(factory, config, (session, message) -> {}, (c, q) -> NoopJmsConsumerTelemetry.INSTANCE);
        container.init();
        assertTimeoutPreemptively(Duration.ofSeconds(3), container::release);
    }

    @Test
    void traceLoggingDoesNotRejectMapMessage() throws Exception {
        var received = new CountDownLatch(1);
        var defaults = DefaultJmsConsumerTelemetry.TelemetryContext.EMPTY.config();
        var telemetryConfig = new JmsConsumerTelemetryConfig() {

            public JmsConsumerLoggingConfig logging() {
                return new JmsConsumerLoggingConfig() {

                    public boolean enabled() {
                        return true;
                    }
                };
            }

            public JmsConsumerMetricsConfig metrics() {
                return defaults.metrics();
            }

            public JmsConsumerTracingConfig tracing() {
                return defaults.tracing();
            }
        };
        var config = new JmsListenerContainerConfig() {

            public String queueName() {
                return "test-queue";
            }

            public int threads() {
                return 1;
            }

            public Duration initializationFailTimeout() {
                return Duration.ofSeconds(5);
            }

            public JmsConsumerTelemetryConfig telemetry() {
                return telemetryConfig;
            }
        };
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("io.koraframework.jms.consumer.test-queue");
        var previous = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.TRACE);
        try {
            container = new JmsMessageListenerContainer(factory, config, (session, message) -> {
                assertThat(((javax.jms.MapMessage) message).getString("key")).isEqualTo("value");
                received.countDown();
            }, new DefaultJmsConsumerTelemetryFactory(null, null, null, null));
            container.init();
            try (var connection = factory.createConnection();
                    var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    var producer = session.createProducer(session.createQueue("test-queue"))) {
                var message = session.createMapMessage();
                message.setString("key", "value");
                JmsUtils.appendHeaders(message, java.util.Map.of("attempt", 1L, "TOKEN", "secret"));
                producer.send(message);
            }
            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            if (container != null)
                container.release();
            logger.setLevel(previous);
        }
    }

    @Test
    void gracefulShutdownLetsInFlightReplyCommit() throws Exception {
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var shutdownFailure = new AtomicReference<Throwable>();
        startListener((session, message) -> {
            entered.countDown();
            try {
                finish.await();
            } catch (InterruptedException e) {
                throw new AssertionError("handler interrupted", e);
            }
            try (var producer = session.createProducer(session.createQueue("replies"))) {
                producer.send(session.createTextMessage("committed-during-shutdown"));
            }
        });
        send("request");
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var shutdown = Thread.ofVirtual().start(() -> {
            try {
                container.release();
            } catch (Throwable e) {
                shutdownFailure.set(e);
            }
        });
        try {
            JmsMessageListenerContainerTest.await(() -> container.probe() != null);
            finish.countDown();
            shutdown.join(5000);
            assertThat(shutdown.isAlive()).isFalse();
            assertThat(shutdownFailure.get()).isNull();
            try (var connection = factory.createConnection();
                    var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    var replies = session.createConsumer(session.createQueue("replies"))) {
                connection.start();
                var message = replies.receive(5000);
                assertThat(message).isNotNull();
                assertThat(JmsUtils.text(message)).isEqualTo("committed-during-shutdown");
            }
        } finally {
            finish.countDown();
            shutdown.join(5000);
        }
    }
}
