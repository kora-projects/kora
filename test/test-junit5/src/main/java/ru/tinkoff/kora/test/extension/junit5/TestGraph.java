package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.RefreshableGraph;
import ru.tinkoff.kora.test.extension.junit5.KoraJUnit5Extension.TestClassMetadata;

import java.io.IOException;
import java.time.Duration;

final class TestGraph implements AutoCloseable {

    private static final Object LOCK = new Object();

    private static final Logger logger = LoggerFactory.getLogger(KoraJUnit5Extension.class);

    private final ApplicationGraphDraw graph;
    private final TestClassMetadata meta;

    private volatile TestGraphInitialized graphInitialized;

    TestGraph(ApplicationGraphDraw graph, TestClassMetadata meta) {
        this.graph = graph;
        this.meta = meta;
    }

    void initialize() {
        logger.debug("@KoraAppTest dependency container initializing...");
        final long started = System.nanoTime();

        synchronized (LOCK) {
            var config = meta.config();
            try {
                config.setup(graph);
                final RefreshableGraph initGraph = graph.init();
                this.graphInitialized = new TestGraphInitialized(initGraph, graph, new DefaultKoraAppGraph(graph, initGraph));
                logger.info("@KoraAppTest dependency container initialization took: {}", Duration.ofNanos(System.nanoTime() - started));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } finally {
                config.cleanup();
            }
        }
    }

    @Nonnull
    TestGraphInitialized initialized() {
        if (graphInitialized == null) {
            throw new IllegalStateException("Dependency Container is not initialized!");
        }
        return graphInitialized;
    }

    @Override
    public void close() {
        if (graphInitialized != null) {
            final long started = System.nanoTime();
            logger.debug("@KoraAppTest dependency container closing...");
            try {
                graphInitialized.refreshableGraph().release();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            graphInitialized = null;
            logger.info("@KoraAppTest dependency container close took: {}", Duration.ofNanos(System.nanoTime() - started));
        }
    }
}
