package ru.tinkoff.kora.jms;

import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;

import javax.jms.ConnectionFactory;

public class JmsConnectionFactory implements Lifecycle, Wrapped<ConnectionFactory> {
    private final ConnectionFactory connectionFactory;

    @SuppressWarnings("try")
    public JmsConnectionFactory(ConnectionFactory connectionFactory, String name) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void init() {
    }

    @Override
    public void release() throws Exception {
        if (this.connectionFactory instanceof AutoCloseable closeable) {
            closeable.close();
        }

    }

    @Override
    public ConnectionFactory value() {
        return this.connectionFactory;
    }
}
