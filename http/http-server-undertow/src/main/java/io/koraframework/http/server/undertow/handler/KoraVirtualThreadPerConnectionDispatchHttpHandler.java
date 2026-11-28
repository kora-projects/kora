package io.koraframework.http.server.undertow.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class KoraVirtualThreadPerConnectionDispatchHttpHandler implements HttpHandler {

    private final AttachmentKey<ExecutorService> executorServiceAttachmentKey = AttachmentKey.create(ExecutorService.class);

    private final String name;
    private final HttpHandler delegate;

    public KoraVirtualThreadPerConnectionDispatchHttpHandler(String name, HttpHandler delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        var executor = getOrCreateExecutor(exchange, executorServiceAttachmentKey, name);
        exchange.dispatch(executor, delegate);
    }

    public static ExecutorService getOrCreateExecutor(HttpServerExchange exchange, AttachmentKey<ExecutorService> key, String serverName) {
        var connection = exchange.getConnection();
        var existingExecutor = connection.getAttachment(key);
        if (existingExecutor != null) {
            return existingExecutor;
        }
        var threadName = serverName + "-" + connection.getId();
        // TODO think about caching executor
        var executor = Executors.newSingleThreadExecutor(new VirtualThreadFactory(threadName));
        connection.addCloseListener(new ConnectionCloseListener(key));
        connection.putAttachment(key, executor);
        return executor;
    }

    private static final class VirtualThreadFactory implements ThreadFactory {
        private final String threadName;

        public VirtualThreadFactory(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(Runnable r) {
            return Thread.ofVirtual().name(threadName).unstarted(r);
        }
    }

    private static final class ConnectionCloseListener implements io.undertow.server.ServerConnection.CloseListener {
        private final AttachmentKey<ExecutorService> key;

        public ConnectionCloseListener(AttachmentKey<ExecutorService> key) {
            this.key = key;
        }

        @Override
        public void closed(io.undertow.server.ServerConnection c) {
            var e = c.removeAttachment(key);
            if (e != null) {
                e.shutdownNow();
            }
        }
    }
}
