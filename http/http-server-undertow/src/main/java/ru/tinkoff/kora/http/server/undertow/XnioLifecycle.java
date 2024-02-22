package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class XnioLifecycle implements Lifecycle, Wrapped<XnioWorker> {

    private static final Logger logger = LoggerFactory.getLogger(XnioLifecycle.class);

    private final ValueOf<HttpServerConfig> configValue;

    private XnioWorker worker;

    public XnioLifecycle(ValueOf<HttpServerConfig> configValue) {
        this.configValue = configValue;
    }

    @Override
    public void init() throws Exception {
        logger.debug("XNIO starting...");
        final long started = System.nanoTime();

        var httpServerConfig = configValue.get();

        // XnioWorker will be daemon despite flag .setDaemon(false) if the thread it is started from is daemon (virtual thread)
        var f = new CompletableFuture<XnioWorker>();
        var t = new Thread(() -> {
            try {
                var worker = Xnio.getInstance(Undertow.class.getClassLoader())
                    .createWorkerBuilder()
                    .setCoreWorkerPoolSize(1)
                    .setMaxWorkerPoolSize(httpServerConfig.blockingThreads())
                    .setWorkerIoThreads(httpServerConfig.ioThreads())
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

        logger.info("XNIO started in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public void release() throws Exception {
        if(worker != null) {
            logger.debug("XNIO stopping...");
            final long started = System.nanoTime();

            worker.shutdown();
            worker.awaitTermination();
            worker = null;

            logger.info("XNIO stopped in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        }
    }

    @Override
    public XnioWorker value() {
        return this.worker;
    }
}
