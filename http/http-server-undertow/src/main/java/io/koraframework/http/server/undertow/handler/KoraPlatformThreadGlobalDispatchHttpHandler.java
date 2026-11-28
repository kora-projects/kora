package io.koraframework.http.server.undertow.handler;

import io.undertow.server.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class KoraPlatformThreadGlobalDispatchHttpHandler implements HttpHandler {

    private final ExecutorService executor;
    private final HttpHandler delegate;

    public KoraPlatformThreadGlobalDispatchHttpHandler(String name, HttpHandler delegate) {
        this.delegate = delegate;

        int cores = Runtime.getRuntime().availableProcessors();
        int threadCount = cores * 2;
        if (threadCount < 2) {
            threadCount = 2;
        }

        this.executor = Executors.newFixedThreadPool(threadCount, new KoraPlatformThreadFactory(name));
    }

    public KoraPlatformThreadGlobalDispatchHttpHandler(String name, HttpHandler delegate, int threadCount) {
        this.delegate = delegate;
        this.executor = Executors.newFixedThreadPool(threadCount, new KoraPlatformThreadFactory(name));
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.dispatch(this.executor, this.delegate);
    }

    private static final class KoraPlatformThreadFactory implements ThreadFactory {
        private final String serverName;
        private final AtomicInteger counter = new AtomicInteger(1);

        public KoraPlatformThreadFactory(String serverName) {
            this.serverName = serverName;
        }

        @Override
        public Thread newThread(Runnable r) {
            var t = new Thread(r, this.serverName + "-platform-worker-" + this.counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
