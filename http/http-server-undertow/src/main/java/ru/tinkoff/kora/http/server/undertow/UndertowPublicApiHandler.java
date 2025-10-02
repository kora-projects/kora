package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.SameThreadExecutor;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;

import java.util.concurrent.atomic.AtomicBoolean;

public final class UndertowPublicApiHandler implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(UndertowPublicApiHandler.class);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final PublicApiHandler publicApiHandler;
    @Nullable
    private final HttpServerTracer tracer;

    public UndertowPublicApiHandler(PublicApiHandler publicApiHandler, @Nullable HttpServerTracer tracer) {
        this.publicApiHandler = publicApiHandler;
        this.tracer = tracer;
    }

    public void handleRequest(HttpServerExchange exchange) {
        var context = Context.clear();
        var exchangeProcessor = new UndertowExchangeProcessor(exchange, this.publicApiHandler, shutdown::get, context, this.tracer);
        exchange.dispatch(SameThreadExecutor.INSTANCE, exchangeProcessor);
    }

    @Override
    public void init() {
        // do nothing
    }

    @Override
    public void release() {
        this.shutdown.set(true);
    }
}
