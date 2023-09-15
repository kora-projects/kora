package ru.tinkoff.kora.jms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerTelemetry;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerTelemetryFactory;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class JmsMessageListenerContainer implements Lifecycle {
    private static final ConcurrentHashMap<String, AtomicInteger> threadCounters = new ConcurrentHashMap<>();
    private final ConnectionFactory connectionFactory;
    private final JmsListenerContainerConfig config;
    private final JmsMessageListener messageListener;
    private final Logger log;
    private final JmsConsumerTelemetry telemetry;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private volatile ExecutorService executorService;

    public JmsMessageListenerContainer(ConnectionFactory connectionFactory, JmsListenerContainerConfig config, JmsMessageListener messageListener, JmsConsumerTelemetryFactory telemetryFactory) {
        this.connectionFactory = connectionFactory;
        this.config = config;
        this.messageListener = messageListener;
        this.log = LoggerFactory.getLogger(JmsMessageListenerContainer.class);
        this.telemetry = telemetryFactory.get(config.queueName());
    }

    @Override
    public void init() {
        if (this.isStarted.compareAndSet(false, true)) {
            if (this.config.threads() == 0) {
                return;
            }
            this.executorService = Executors.newFixedThreadPool(this.config.threads());
            for (int i = 0; i < this.config.threads(); i++) {
                this.executorService.submit(this::connectLoop);
            }
        }
    }

    @Override
    public void release() {
        if (this.isStarted.compareAndSet(true, false)) {
            if (this.config.threads() == 0) {
                return;
            }
            this.executorService.shutdownNow();
            try {
                this.executorService.awaitTermination(10, TimeUnit.SECONDS);
                this.executorService = null;
            } catch (InterruptedException ignore) {
            }
        }
    }

    private void connectLoop() {
        var counter = threadCounters.computeIfAbsent(this.config.queueName(), s -> new AtomicInteger());
        Thread.currentThread().setName("jms-" + this.config.queueName() + "-" + counter.getAndIncrement());
        log.info("listening...");
        while (this.isStarted.get()) {
            try {
                log.trace("Trying new connection");
                try (var connection = this.connectionFactory.createConnection();
                     var session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                    connection.start();
                    this.pollLoop(session);
                }
            } catch (JMSException e) {
                log.info("Jms exception caught while processing message: {}", e.toString(), e);
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException ex) {
                    log.trace("Jms thread interrupted");
                }
            } catch (Exception e) {
                log.trace("Unknown ex");
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException ex) {
                    log.trace("Jms thread interrupted");
                }
            }
        }
        log.info("Consumer stopped");
    }

    private void pollLoop(Session session) throws JMSException {
        var queue = session.createQueue(this.config.queueName());
        try (var consumer = session.createConsumer(queue, null)) {
            while (this.isStarted.get()) {
                try {
                    var message = consumer.receiveNoWait();
                    long lastSuccessfullReceive = System.nanoTime();
                    if (message == null) {
                        log.trace("No message was received");
                        Thread.sleep(1000);
                        session.commit();
                        continue;
                    }
                    var telemetryCtx = this.telemetry.get(message);
                    try {
                        if (log.isDebugEnabled()) {
                            var body = JmsUtils.text(message);
                            var headers = JmsUtils.dumpHeaders(message).toString();
                            log.debug(StructuredArgument.marker("jmsInputMessage", (gen) -> {
                                gen.writeStartObject();
                                gen.writeStringField("headers", headers);
                                gen.writeStringField("body", body);
                                gen.writeEndObject();
                            }), "JmsListener.message");
                        }
                        this.messageListener.onMessage(session, message);
                        session.commit();
                        telemetryCtx.close(null);
                    } catch (Exception e) {
                        telemetryCtx.close(e);
                        throw e;
                    } finally {
                        Context.clear();
                        MDC.clear();
                    }
                } catch (InterruptedException e) {
                    log.trace("Jms thread interrupted");
                } catch (JMSException e) {
                    session.rollback();
                    throw e;
                } catch (Exception e) {
                    log.debug("Exception caught while processing message", e);
                    session.rollback();
                }
            }
            log.trace("Poll loop end");
        }
    }

}
