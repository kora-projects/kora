package io.koraframework.http.server.undertow;

import io.koraframework.common.util.Configurer;
import io.undertow.Undertow;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.util.TimeUtils;

import java.util.concurrent.CompletableFuture;

public final class XnioLifecycle implements Lifecycle, Wrapped<XnioWorker> {

    private static final Logger logger = LoggerFactory.getLogger(XnioLifecycle.class);

    private final ValueOf<UndertowConfig> configValue;
    @Nullable
    private final Configurer<XnioWorker.Builder> configurer;

    private volatile XnioWorker worker;

    public XnioLifecycle(ValueOf<UndertowConfig> configValue,
                         @Nullable Configurer<XnioWorker.Builder> configurer) {
        this.configValue = configValue;
        this.configurer = configurer;
    }

    @Override
    public void init() throws Exception {
        logger.debug("XnioWorker starting...");
        var started = System.nanoTime();

        var httpServerConfig = configValue.get();

        var f = new CompletableFuture<XnioWorker>();
        var t = new Thread(() -> {
            try {
                // XnioWorker will be daemon despite flag .setDaemon(false) if the thread it is started from is daemon (virtual thread)
                var builder = Xnio.getInstance(Undertow.class.getClassLoader())
                    .createWorkerBuilder()
                    .setCoreWorkerPoolSize(1)
                    .setMaxWorkerPoolSize(1)
                    .setWorkerIoThreads(httpServerConfig.ioThreads())
                    .setWorkerKeepAlive(((int) httpServerConfig.threadKeepAliveTimeout().toMillis()))
                    .setDaemon(false)
                    .setWorkerName("kora-undertow");

                if(configurer != null) {
                    builder = configurer.configure(builder);
                }
                var worker = builder.build();

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
        if (worker != null) {
            logger.debug("XnioWorker stopping...");
            var started = System.nanoTime();

            worker.shutdown();
            worker.awaitTermination();

            logger.info("XnioWorker stopped in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public XnioWorker value() {
        return this.worker;
    }
}
