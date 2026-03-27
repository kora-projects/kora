package io.koraframework.http.server.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VirtualThreadHttpHandler implements HttpHandler {

    private final AttachmentKey<ExecutorService> executorServiceAttachmentKey = AttachmentKey.create(ExecutorService.class);

    private final String name;
    private final HttpHandler delegate;

    public VirtualThreadHttpHandler(String name, HttpHandler delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        var executor = getOrCreateExecutor(exchange, executorServiceAttachmentKey, this.name);
        exchange.dispatch(executor, this.delegate);
    }

    public static ExecutorService getOrCreateExecutor(HttpServerExchange exchange, AttachmentKey<ExecutorService> key, String serverName) {
        var connection = exchange.getConnection();
        var existingExecutor = connection.getAttachment(key);
        if (existingExecutor != null) {
            return existingExecutor;
        }
        var threadName = serverName + "-" + connection.getId();
        // todo think about caching executor
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
