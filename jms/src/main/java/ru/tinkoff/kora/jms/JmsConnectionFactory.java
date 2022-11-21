package ru.tinkoff.kora.jms;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.ReactorUtils;

import javax.jms.ConnectionFactory;
import java.io.Closeable;
import java.io.IOException;

public class JmsConnectionFactory implements Lifecycle, Wrapped<ConnectionFactory> {
    private final ConnectionFactory connectionFactory;

    @SuppressWarnings("try")
    public JmsConnectionFactory(ConnectionFactory connectionFactory, String name) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Mono<Void> init() {
        return ReactorUtils.ioMono(() -> {
        });
    }

    @Override
    public Mono<Void> release() {
        return ReactorUtils.ioMono(() -> {
            if (this.connectionFactory instanceof Closeable) {
                try {
                    ((Closeable) this.connectionFactory).close();
                } catch (IOException e) {
                    throw Exceptions.bubble(e);
                }
            }
        });

    }

    @Override
    public ConnectionFactory value() {
        return this.connectionFactory;
    }
}
