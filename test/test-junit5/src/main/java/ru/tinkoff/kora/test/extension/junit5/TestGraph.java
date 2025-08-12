package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.RefreshableGraph;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.test.extension.junit5.KoraJUnit5Extension.TestMethodMetadata;

import java.io.IOException;

final class TestGraph implements AutoCloseable {

    public enum Status {
        CREATED,
        INITIALIZED,
        RELEASED
    }

    private static final Logger logger = LoggerFactory.getLogger(KoraJUnit5Extension.class);

    private static final Object LOCK = new Object();

    private final ApplicationGraphDraw graph;
    private final TestMethodMetadata metadata;

    private volatile TestGraphContext graphInitialized;
    private volatile Status status;

    TestGraph(ApplicationGraphDraw graph, TestMethodMetadata metadata) {
        this.graph = graph;
        this.metadata = metadata;
        this.status = Status.CREATED;
    }

    void initialize() {
        logger.trace("@KoraAppTest dependency container initializing...");
        final long started = TimeUtils.started();

        var config = metadata.classMetadata().config();
        synchronized (LOCK) { // system property set/unset sync
            try {
                config.setup(graph);
                final RefreshableGraph initGraph = graph.init();
                this.graphInitialized = new TestGraphContext(initGraph, graph, new DefaultKoraAppGraph(graph, initGraph));
                this.status = Status.INITIALIZED;
                logger.debug("@KoraAppTest dependency container initialized in {}", TimeUtils.tookForLogging(started));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } finally {
                config.cleanup();
            }
        }
    }

    @Nonnull
    TestGraphContext initialized() {
        if (graphInitialized == null) {
            throw new IllegalStateException("Dependency Container is not initialized!");
        }
        return graphInitialized;
    }

    Status status() {
        return status;
    }

    @Override
    public void close() {
        if (graphInitialized != null) {
            final long started = TimeUtils.started();
            logger.trace("@KoraAppTest dependency container releasing...");
            try {
                graphInitialized.refreshableGraph().release();
                this.status = Status.RELEASED;
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            graphInitialized = null;
            logger.debug("@KoraAppTest dependency container released in {}", TimeUtils.tookForLogging(started));
        }
    }
}
