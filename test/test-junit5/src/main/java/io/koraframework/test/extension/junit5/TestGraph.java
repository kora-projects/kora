package io.koraframework.test.extension.junit5;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.Node;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.test.extension.junit5.KoraJUnit5Extension.TestMethodMetadata;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

final class TestGraph implements AutoCloseable {

    public enum Status {
        CREATED,
        INITIALIZED,
        RELEASED
    }

    private static final Logger logger = LoggerFactory.getLogger(KoraJUnit5Extension.class);

    private static final int PERMIT_WITH_PROPS = 64;
    private static final int PERMIT_NO_PROPS = 1;
    private static final Semaphore LOCK = new Semaphore(PERMIT_WITH_PROPS);
    private static final AtomicBoolean FIRST_INIT = new AtomicBoolean(true);

    private final ApplicationGraphDraw graph;
    private final TestMethodMetadata metadata;
    private final List<Node<?>> nodes;
    private final List<Node<?>> mocks;

    @Nullable
    private volatile TestGraphContext graphInitialized;
    private volatile Status status;

    TestGraph(ApplicationGraphDraw graph,
              TestMethodMetadata metadata,
              List<Node<?>> nodes,
              List<Node<?>> mocks) {
        this.graph = graph;
        this.metadata = metadata;
        this.nodes = nodes;
        this.mocks = mocks;
        this.status = Status.CREATED;
    }

    List<Node<?>> getNodes() {
        return nodes;
    }

    List<Node<?>> getMocks() {
        return mocks;
    }

    void initialize() {
        logger.trace("@KoraAppTest graph initializing...");
        final long started = TimeUtils.started();

        var config = metadata.classMetadata().config();

        // a permit not returned on a failed initialization blocks every later test in the JVM forever
        if (!config.systemProperties().isEmpty()) {
            // system property set/unset sync required or props reshare between different init graphs
            LOCK.acquireUninterruptibly(PERMIT_WITH_PROPS);
            try {
                initGraph(config, started);
            } finally {
                LOCK.release(PERMIT_WITH_PROPS);
            }
        } else {
            LOCK.acquireUninterruptibly(PERMIT_NO_PROPS);
            try {
                initGraph(config, started);
            } finally {
                LOCK.release(PERMIT_NO_PROPS);
            }
        }
    }

    private void initGraph(KoraJUnit5Extension.TestClassMetadata.Config config, long started) {
        try {
            config.setup(graph);
            var initGraph = graph.init();
            this.graphInitialized = new TestGraphContext(initGraph, graph, new TestKoraAppGraph(graph, initGraph));
            this.status = Status.INITIALIZED;
            if (FIRST_INIT.compareAndSet(true, false)) {
                try {
                    var uptimeTook = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
                    logger.info("@KoraAppTest graph initialized in {} (JVM running for {}s)", TimeUtils.tookForLogging(started), uptimeTook);
                } catch (Throwable ex) {
                    logger.debug("@KoraAppTest graph initialized in {}", TimeUtils.tookForLogging(started));
                }
            } else {
                logger.debug("@KoraAppTest graph initialized in {}", TimeUtils.tookForLogging(started));
            }
        } catch (Exception e) {
            throw new ExtensionConfigurationException("@KoraAppTest graph initialization failed after: " + TimeUtils.tookForLogging(started), e);
        } finally {
            config.cleanup();
        }
    }

    TestGraphContext initialized() {
        if (graphInitialized == null) {
            throw new ExtensionConfigurationException("@KoraAppTest graph is not initialized, initialization probably failed on previous steps!");
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
            logger.trace("@KoraAppTest graph releasing...");
            try {
                graphInitialized.initializedGraph().release();
                this.status = Status.RELEASED;
            } catch (Error | Exception e) {
                throw new ExtensionConfigurationException("TestGraph release failed after: " + TimeUtils.tookForLogging(started), e);
            }
            graphInitialized = null;
            logger.debug("@KoraAppTest graph released in {}", TimeUtils.tookForLogging(started));
        }
    }
}
