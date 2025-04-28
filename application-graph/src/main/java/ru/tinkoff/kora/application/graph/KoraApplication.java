package ru.tinkoff.kora.application.graph;

import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.function.Supplier;

public final class KoraApplication {
    private KoraApplication() {
        throw new IllegalStateException();
    }

    public static RefreshableGraph run(Supplier<ApplicationGraphDraw> supplier) {
        var start = System.currentTimeMillis();
        var graphDraw = supplier.get();
        var log = LoggerFactory.getLogger(graphDraw.getRoot());
        log.debug("Application initializing...");
        try {
            var graph = graphDraw.init();
            var end = System.currentTimeMillis();
            try {
                var uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
                log.info("Application initialized in {} ms (JVM running for {} s)", end - start, uptime);
            } catch (Throwable ex) {
                log.info("Application initialized in {}ms", end - start);
            }
            var thread = new Thread(() -> {
                try {
                    log.info("Application shutdown");
                    graph.release();
                    log.info("Application released");
                } catch (Exception e) {
                    log.error("Application release error", e);
                    try {
                        Thread.sleep(100);// so async logger is able to write exception to log
                    } catch (InterruptedException ignore) {}
                    System.exit(-1);
                }
            });
            thread.setName("kora-shutdown");
            Runtime.getRuntime().addShutdownHook(thread);
            return graph;
        } catch (Exception e) {
            log.error("Application initializing failed with error", e);
            e.printStackTrace();
            try {
                Thread.sleep(100);// so async logger is able to write exception to log
            } catch (InterruptedException ignore) {}
            System.exit(-1);
        }
        return null;
    }
}
