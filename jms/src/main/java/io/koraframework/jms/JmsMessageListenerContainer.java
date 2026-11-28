package io.koraframework.jms;

import io.koraframework.jms.telemetry.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.common.context.KoraContext;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.Message;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class JmsMessageListenerContainer implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(JmsMessageListenerContainer.class);

    private static final ConcurrentHashMap<String, AtomicInteger> threadCounters = new ConcurrentHashMap<>();

    private final ConnectionFactory connectionFactory;
    private final JmsListenerContainerConfig config;
    private final JmsMessageListener messageListener;
    private final Logger log;
    private final JmsConsumerTelemetry telemetry;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    @Nullable
    private volatile ExecutorService executorService;

    public JmsMessageListenerContainer(ConnectionFactory connectionFactory, JmsListenerContainerConfig config, JmsMessageListener messageListener, JmsConsumerTelemetryFactory telemetryFactory) {
        this.connectionFactory = connectionFactory;
        this.config = config;
        this.messageListener = messageListener;
        this.log = LoggerFactory.getLogger(JmsMessageListenerContainer.class);
        this.telemetry = telemetryFactory.get(config.telemetry(), config.queueName());
    }

    @Override
    public void init() {
        if (this.isStarted.compareAndSet(false, true)) {
            if (this.config.threads() == 0) {
                return;
            }

            logger.debug("JmsMessageListener starting...");
            var started = System.nanoTime();

            this.executorService = Executors.newFixedThreadPool(this.config.threads());
            for (int i = 0; i < this.config.threads(); i++) {
                this.executorService.submit(this::connectLoop);
            }

            logger.info("JmsMessageListener started in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public void release() {
        if (this.isStarted.compareAndSet(true, false)) {
            if (this.config.threads() == 0) {
                return;
            }

            logger.debug("JmsMessageListener stopping...");
            var started = System.nanoTime();

            this.executorService.shutdownNow();
            try {
                this.executorService.awaitTermination(10, TimeUnit.SECONDS);
                this.executorService = null;
            } catch (InterruptedException ignore) {
            }

            logger.info("JmsMessageListener stopped in {}", TimeUtils.tookForLogging(started));
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
                    if (message == null) {
                        log.trace("No message was received");
                        Thread.sleep(1000);
                        session.commit();
                        continue;
                    }
                    var observation = this.telemetry.observe(message);
                    try {
                        var baseContext = KoraContext.empty()
                            .with(io.koraframework.logging.common.MDC.MDC_KEY, new io.koraframework.logging.common.MDC());

                        var finalContext = Observation.attach(baseContext, observation);

                        KoraContext.runWith(finalContext, new JmsMessageProcessor(this, session, message, observation));
                        session.commit();
                    } finally {
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

    private static final class JmsMessageProcessor implements Supplier<Void> {
        private final JmsMessageListenerContainer container;
        private final Session session;
        private final Message message;
        private final JmsConsumerObservation observation;

        public JmsMessageProcessor(JmsMessageListenerContainer container,
                                   Session session,
                                   Message message,
                                   JmsConsumerObservation observation) {
            this.container = container;
            this.session = session;
            this.message = message;
            this.observation = observation;
        }

        @Override
        public Void get() {
            try {
                observation.observeProcess();
                container.messageListener.onMessage(session, message);
            } catch (Throwable t) {
                observation.observeError(t);
                throw new RuntimeException(t);
            } finally {
                observation.end();
            }
            return null;
        }
    }
}
