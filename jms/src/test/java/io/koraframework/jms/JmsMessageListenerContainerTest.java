package io.koraframework.jms;

import io.koraframework.jms.telemetry.JmsConsumerObservation;
import io.koraframework.jms.telemetry.JmsConsumerTelemetry;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryFactory;
import io.opentelemetry.api.trace.Span;
import javax.jms.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JmsMessageListenerContainerTest {

    @Test
    void listenerErrorDoesNotKillConsumerThread() throws Exception {
        var m1 = Mockito.mock(TextMessage.class);
        var m2 = Mockito.mock(TextMessage.class);
        var queue = new ConcurrentLinkedQueue<Message>(List.of(m1, m2));

        var consumer = Mockito.mock(MessageConsumer.class);
        Mockito.when(consumer.receiveNoWait()).thenAnswer(i -> queue.poll());
        var session = Mockito.mock(Session.class);
        Mockito.when(session.createConsumer(Mockito.any(), Mockito.any())).thenReturn(consumer);
        var connection = Mockito.mock(Connection.class);
        Mockito.when(connection.createSession(true, Session.SESSION_TRANSACTED)).thenReturn(session);
        var cf = Mockito.mock(ConnectionFactory.class);
        Mockito.when(cf.createConnection()).thenReturn(connection);

        var handled = new CopyOnWriteArrayList<Message>();
        JmsMessageListener listener = (s, m) -> {
            handled.add(m);
            if (m == m1) {
                throw new StackOverflowError("boom");
            }
        };

        JmsConsumerTelemetryFactory tf = (cfg, name) -> msg -> new JmsConsumerObservation() {
            @Override public void observeProcess() {}
            @Override public Span span() { return Span.getInvalid(); }
            @Override public void end() {}
            @Override public void observeError(Throwable e) {}
        };
        var config = new JmsListenerContainerConfig() {
            @Override public String queueName() { return "q"; }
            @Override public int threads() { return 1; }
            @Override public JmsConsumerTelemetryConfig telemetry() { return null; }
        };
        var container = new JmsMessageListenerContainer(cf, config, listener, tf);
        container.init();
        try {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!handled.contains(m2) && System.nanoTime() < deadline) Thread.sleep(50);
            assertThat(handled).contains(m2);
        } finally {
            container.release();
        }
    }
}
