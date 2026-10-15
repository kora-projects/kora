package io.koraframework.application.graph.internal.optimized;

import io.koraframework.application.graph.*;
import io.koraframework.application.graph.GraphCondition.*;
import io.koraframework.application.graph.internal.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/// Core dependency injection container implementation that manages the lifecycle
/// of application components based on the static topological blueprint provided by [ApplicationGraphDraw].
///
/// The container handles both initialization and dynamic updates through a unified, highly optimized
/// sequential runtime path, guaranteeing transactional state safety using an isolated rollbackable
/// [OptimizedTmpGraph] scratchpad.
///
/// ### Core Container Lifecycle Algorithm
///
/// 1. **Mutation Locking**
///    Any state modification (via [#init()] or [#refresh(Node)]) exclusively acquires the mutationLock
///    to eliminate concurrent race conditions during graph restructuring.
///
/// 2. **Transaction Initiation**
///    Before materialization begins, the condition evaluation cache (conditionResultsCache) is cleared,
///    and a new [OptimizedTmpGraph] instance is spawned. The scratchpad isolates the in-flight bootstrap
///    by operating on an atomic snapshot of current active component references.
///
/// 3. **Materialization Strategy Selection** (inside initializeSubgraph)
///    The graph framework leverages the compiler-guaranteed topological sorting of draw.getNodes().
///    - **Cold Start (startFrom == 0):** The container flags all compiled nodes as active inside
///      a traversal [BitSet]. It then iterates linearly over all slots from 0 to size - 1.
///      Dependencies for any given node are guaranteed to be fully materialized in the scratchpad ahead of time,
///      completely bypassing thread scheduling overhead and maximizing CPU cache locality.
///      and maximizing CPU cache locality.
///    - **Subtree Refresh (startFrom > 0):** The container executes a lightning-fast single-pass linear
///      bitwise scan over the topologically sorted node array to compute dependentNodes (including
///      refreshDependencies and interceptors). It then runs a sequential single-threaded initialization loop
///      from startFrom to size - 1, skipping unaffected nodes. This entirely eliminates data races,
///      concurrency bottlenecks, and memory footprint overhead from futures or barriers.
///
/// 4. **Node Materialization** (inside OptimizedTmpGraph#createNode)
///    - Dynamic [GraphCondition] configurations are evaluated. If a condition mismatch occurs,
///      the node slot is flagged as failed/inactive.
///    - The raw factory lambda node.factory.get(this) is invoked to instantiate the application bean.
///    - If the bean implements [Lifecycle], its init() hooks are triggered.
///    - The instance is sequentially decorated by all compiled [GraphInterceptor#afterInit(Object)] interceptors.
///    - The fully assembled component is written to its flat slot mapped to its unique node.index.
///
/// 5. **Transactional Commit Phase**
///    Upon successful materialization of all scheduled nodes, tmpGraph.commit() is executed.
///    The container performs an atomic slot reference swap inside the live objects array using
///    a primitive byte[] changedNodes array to minimize CPU tacts. Proxied [ValueOf] and [PromiseOf] references
///    are instantly re-bound to the active state. To ensure strict read thread-safety, the transient scratchpad
///    activates a volatile delegate redirecting ongoing lookups to the main container. Replaced stale components
///    are sequentially torn down in reverse topological order through beforeRelease interceptors
///    and standard lifecycle closure procedures.
///
/// 6. **Abrupt Rollback Phase**
///    If a component factory throws any unhandled exception, tmpGraph.rollback() is invoked.
///    All temporary components instantiated within the failed scope are immediately closed to release
///    sockets/file descriptors. The master objects array remains completely untainted, keeping
///    the active application running stable on its old state.
///
/// 7. **Listener Notification**
///    Following a successful commit, [#fireRefreshListeners()] is invoked to trigger event handlers
///    across all components implementing [RefreshListener].
///
/// @see OptimizedTmpGraph
public final class OptimizedGraphImpl implements InitializedGraph {

    private static final long SLOW_NODE_INIT_THRESHOLD_NANOS = Long.parseLong(System.getProperty("kora.graph.slowNodeInitThresholdMillis", "100")) * 1_000_000L;

    final Logger logger;
    private final ApplicationGraphDraw draw;
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final AtomicReferenceArray<@Nullable Object> objects;
    private final BitSet refreshListenerIndices = new BitSet();
    private final Map<Integer, ConditionResult> conditionResultsCache = new ConcurrentHashMap<>();

    public OptimizedGraphImpl(ApplicationGraphDraw draw) {
        this.draw = draw;
        this.logger = LoggerFactory.getLogger(draw.getRoot());
        this.objects = new AtomicReferenceArray<>(draw.size());
    }

    @Override
    public ApplicationGraphDraw draw() {
        return this.draw;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Node<? extends T> node) {
        var index = ((NodeImpl<?>) node).index;
        var val = this.objects.get(index);
        if (val == null) {
            throw new IllegalStateException("Graph node value was not initialized: " + node);
        }
        if (val instanceof ConditionFailedValue failedValue) {
            throw new IllegalStateException("Graph node value was not initialized because condition failed: " + failedValue.reason);
        }
        return (T) val;
    }

    @Override
    public GraphCondition condition(Node<? extends GraphCondition> node) {
        var index = ((NodeImpl<?>) node).index;
        var cached = this.conditionResultsCache.get(index);
        if (cached != null) {
            return () -> cached;
        }
        var computed = get(node).eval();
        var existing = this.conditionResultsCache.putIfAbsent(index, computed);
        var result = existing != null ? existing : computed;
        return () -> result;
    }

    @Override
    public <T> ValueOf<T> valueOf(Node<? extends T> node) {
        return () -> OptimizedGraphImpl.this.get(node);
    }

    @Override
    public <T> PromiseOf<T> promiseOf(Node<? extends T> node) {
        return () -> Optional.of(OptimizedGraphImpl.this.get(node));
    }

    @Override
    @SafeVarargs
    public final <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... nodes) {
        return () -> Optional.of(this.getOneOf(nodes));
    }

    @Override
    public void init() {
        this.mutationLock.lock();
        logger.debug("Dependency container initializing...");
        final long started = System.nanoTime();
        try {
            this.initializeSubgraph(0);
            if (logger.isDebugEnabled()) {
                logger.debug("Dependency container initialized in {}ms", tookForLogging(started));
            }
        } catch (Exception e) {
            logger.debug("Dependency container initialization failed", e);
            throw e;
        } finally {
            this.mutationLock.unlock();
        }
    }

    @Override
    public void refresh(Node<?> fromNodeRaw) {
        var fromNode = (NodeImpl<?>) fromNodeRaw;
        this.mutationLock.lock();
        var fromNodeObj = this.objects.get(fromNode.index);
        if (logger.isDebugEnabled()) {
            logger.debug("Dependency container refreshing from node {} of class {}...", fromNode.index, fromNodeObj != null ? fromNodeObj.getClass() : "null");
        }
        final long started = System.nanoTime();
        try {
            this.initializeSubgraph(fromNode.index);
            if (logger.isDebugEnabled()) {
                logger.debug("Dependency container refreshed in {}ms", tookForLogging(started));
            }
        } catch (Throwable e) {
            logger.debug("Dependency container refresh error", e);
            throw e;
        } finally {
            this.mutationLock.unlock();
        }
    }

    @Override
    public void release() {
        this.mutationLock.lock();
        logger.debug("Dependency container releasing...");
        final long started = System.nanoTime();
        try {
            this.releaseAllInternal();
            if (logger.isDebugEnabled()) {
                logger.debug("Dependency container released in {}ms", tookForLogging(started));
            }
        } catch (Exception e) {
            logger.debug("Dependency container releasing failed", e);
            throw e;
        } finally {
            this.mutationLock.unlock();
        }
    }

    private void releaseAllInternal() {
        var nodes = this.draw.getNodes();
        var size = nodes.size();
        Throwable error = null;

        for (int i = size - 1; i >= 0; i--) {
            var node = (NodeImpl<?>) nodes.get(i);
            try {
                this.releaseSingleNode(node);
            } catch (Throwable t) {
                if (error == null) {
                    error = t;
                } else {
                    error.addSuppressed(t);
                }
            }
        }

        if (error != null) {
            if (error instanceof RuntimeException re) throw re;
            if (error instanceof Error err) throw err;
            throw new RuntimeException(error);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void releaseSingleNode(NodeImpl<T> node) throws Exception {
        var object = (T) this.objects.get(node.index);
        if (object == null) return;

        var interceptors = node.interceptors;
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            var interceptorNode = (NodeImpl<? extends GraphInterceptor<T>>) interceptors.get(i);
            var interceptor = (GraphInterceptor<T>) objects.get(interceptorNode.index);
            if (interceptor != null) {
                object = interceptor.beforeRelease(object);
            }
        }

        if (object instanceof Lifecycle lifecycle) {
            lifecycle.release();
        } else if (object instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private void fireRefreshListeners() {
        for (int i = refreshListenerIndices.nextSetBit(0); i >= 0; i = refreshListenerIndices.nextSetBit(i + 1)) {
            var object = objects.get(i);
            if (object instanceof RefreshListener listener) {
                try {
                    listener.graphRefreshed();
                } catch (Throwable t) {
                    logger.warn("Error calling refresh listener for node at index {}", i, t);
                }
            }
        }
    }

    private static long tookForLogging(long started) {
        long tookNanos = System.nanoTime() - started;
        return tookNanos / 1_000_000;
    }

    private void initializeSubgraph(int startFrom) {
        logger.trace("Materializing graph objects {}", startFrom);
        conditionResultsCache.clear();

        var tmpGraph = new OptimizedTmpGraph(this);
        var isDebug = logger.isDebugEnabled();
        var nodes = draw.getNodes();
        int size = nodes.size();

        var dependentNodes = new BitSet(size);
        if (startFrom == 0) {
            dependentNodes.set(0, size);
        } else {
            dependentNodes.set(startFrom);
            for (int i = startFrom; i < size; i++) {
                var node = (NodeImpl<?>) nodes.get(i);
                boolean dependsOnChanged = false;

                for (int j = 0; j < node.createDependencies.size(); j++) {
                    if (dependentNodes.get(((NodeImpl<?>) node.createDependencies.get(j)).index)) {
                        dependsOnChanged = true;
                        break;
                    }
                }
                if (!dependsOnChanged) {
                    for (int j = 0; j < node.refreshDependencies.size(); j++) {
                        if (dependentNodes.get(((NodeImpl<?>) node.refreshDependencies.get(j)).index)) {
                            dependsOnChanged = true;
                            break;
                        }
                    }
                }
                if (!dependsOnChanged) {
                    for (int j = 0; j < node.interceptors.size(); j++) {
                        if (dependentNodes.get(((NodeImpl<?>) node.interceptors.get(j)).index)) {
                            dependsOnChanged = true;
                            break;
                        }
                    }
                }

                if (dependsOnChanged) {
                    dependentNodes.set(i);
                }
            }
        }

        for (int i = startFrom; i < size; i++) {
            if (!dependentNodes.get(i)) {
                continue;
            }

            var node = (NodeImpl<?>) nodes.get(i);
            long start = isDebug ? System.nanoTime() : 0L;
            try {
                tmpGraph.createNode(node, startFrom);
                if (isDebug) {
                    long tookNanos = System.nanoTime() - start;
                    if (tookNanos > SLOW_NODE_INIT_THRESHOLD_NANOS) {
                        logger.debug("Initialized node {} at index {} in {}ms", node.type(), node.index, tookNanos / 1_000_000);
                    }
                }
            } catch (Throwable throwable) {
                tmpGraph.rollback();
                switch (throwable) {
                    case RuntimeException re -> throw re;
                    case Error err -> throw err;
                    case null -> throw new IllegalStateException("Kora internal error: graph initialization failed with null error");
                    default -> throw new IllegalStateException("Application graph failed to initialize; see cause for failed component", throwable);
                }
            }
        }

        tmpGraph.commit(startFrom);
        this.fireRefreshListeners();
    }

    AtomicReferenceArray<@Nullable Object> getRawObjects() {
        return this.objects;
    }

    BitSet getRefreshListenerIndices() {
        return this.refreshListenerIndices;
    }

    Map<Integer, ConditionResult> getConditionResultsCache() {
        return this.conditionResultsCache;
    }

    static final class ConditionFailedValue {
        final String reason;

        ConditionFailedValue(ConditionResult.Failed failed) {this.reason = failed.reason();}
    }
}
