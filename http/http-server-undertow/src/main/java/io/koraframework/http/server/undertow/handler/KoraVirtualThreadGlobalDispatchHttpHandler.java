package io.koraframework.http.server.undertow.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class KoraVirtualThreadGlobalDispatchHttpHandler implements HttpHandler {

    private static final ThreadLocal<String> THREAD_NAME_CONTEXT = new ThreadLocal<>();
    private static final ExecutorService GLOBAL_VIRTUAL_EXECUTOR = Executors.newThreadPerTaskExecutor(new KoraVirtualThreadFactory());

    private final String name;
    private final HttpHandler delegate;

    public KoraVirtualThreadGlobalDispatchHttpHandler(String name, HttpHandler delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        String threadName = name + "-" + exchange.getConnection().getId();

        THREAD_NAME_CONTEXT.set(threadName);
        try {
            exchange.dispatch(GLOBAL_VIRTUAL_EXECUTOR, this.delegate);
        } finally {
            THREAD_NAME_CONTEXT.remove();
        }
    }

    private static final class KoraVirtualThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            String name = THREAD_NAME_CONTEXT.get();
            if (name == null) {
                name = "kora-global-virtual-worker";
            }
            return Thread.ofVirtual().name(name).unstarted(r);
        }
    }
}
