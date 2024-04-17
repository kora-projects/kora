package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class XnioLifecycle implements Lifecycle, Wrapped<XnioWorker> {

    private static final Logger logger = LoggerFactory.getLogger(XnioLifecycle.class);

    private final ValueOf<HttpServerConfig> configValue;

    private volatile XnioWorker worker;

    public XnioLifecycle(ValueOf<HttpServerConfig> configValue) {
        this.configValue = configValue;
    }

    @Override
    public void init() throws Exception {
        logger.debug("XnioWorker starting...");
        var started = System.nanoTime();

        var threads = configValue.get().blockingThreads();
        var ioThreads = configValue.get().ioThreads();
        var f = new CompletableFuture<XnioWorker>();
        var t = new Thread(() -> {
            try {
                var worker = Xnio.getInstance(Undertow.class.getClassLoader())
                    .createWorkerBuilder()
                    .setCoreWorkerPoolSize(1)
                    .setMaxWorkerPoolSize(threads)
                    .setWorkerIoThreads(ioThreads)
                    .setWorkerKeepAlive(60 * 1000)
                    .setDaemon(false)
                    .setWorkerName("kora-undertow")
                    .build();
                f.complete(worker);
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        });
        t.setDaemon(false);
        t.start();
        this.worker = f.get();

        logger.info("XnioWorker started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() throws Exception {
        logger.debug("XnioWorker stopping...");
        var started = System.nanoTime();

        worker.shutdown();
        worker.awaitTermination();

        logger.info("XnioWorker stopped in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public XnioWorker value() {
        return this.worker;
    }
}
