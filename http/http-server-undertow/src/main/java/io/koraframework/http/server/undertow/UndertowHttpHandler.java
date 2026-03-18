package io.koraframework.http.server.undertow;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.http.server.common.router.HttpServerHandler;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UndertowHttpHandler implements HttpHandler {

    private final AttachmentKey<ExecutorService> executorServiceAttachmentKey = AttachmentKey.create(ExecutorService.class);

    private final String name;
    private final ValueOf<HttpServerHandler> publicApiHandler;
    private final HttpServerTelemetry telemetry;

    public UndertowHttpHandler(String name, ValueOf<HttpServerHandler> publicApiHandler, HttpServerTelemetry telemetry) {
        this.name = name;
        this.publicApiHandler = publicApiHandler;
        this.telemetry = telemetry;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        var context = new UndertowContext(exchange);
        var exchangeProcessor = new UndertowExchangeProcessor(this.telemetry, context, this.publicApiHandler.get());
        var executor = getOrCreateExecutor(exchange, executorServiceAttachmentKey, this.name);
        exchange.dispatch(executor, exchangeProcessor);
    }

    public static ExecutorService getOrCreateExecutor(HttpServerExchange exchange, AttachmentKey<ExecutorService> key, String serverName) {
        var connection = exchange.getConnection();
        var existingExecutor = connection.getAttachment(key);
        if (existingExecutor != null) {
            return existingExecutor;
        }
        var threadName = serverName + "-" + connection.getId();
        var executor = Executors.newSingleThreadExecutor(r -> Thread.ofVirtual().name(threadName).unstarted(r));
        connection.addCloseListener(c -> {
            var e = c.removeAttachment(key);
            if (e != null) {
                e.shutdownNow();
            }
        });
        connection.putAttachment(key, executor);
        return executor;
    }
}
