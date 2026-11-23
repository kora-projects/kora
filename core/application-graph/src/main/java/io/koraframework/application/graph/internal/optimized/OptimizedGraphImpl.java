package io.koraframework.application.graph.internal.optimized;

import io.koraframework.application.graph.*;
import io.koraframework.application.graph.internal.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static io.koraframework.application.graph.GraphCondition.*;

public final class OptimizedGraphImpl implements InitializedGraph {

    static final VarHandle OBJECTS_VH = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle CONDITION_VH = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle GLOBAL_REMAINING_VH;
    private static final VarHandle ERROR_CHANNEL_VH;

    static {
        try {
            var l = MethodHandles.lookup();
            GLOBAL_REMAINING_VH = l.findVarHandle(OptimizedGraphImpl.class, "globalRemainingTasks", int.class);
            ERROR_CHANNEL_VH = l.findVarHandle(OptimizedGraphImpl.class, "errorChannel", Throwable.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private @Nullable Logger logger;
    private final ApplicationGraphDraw draw;
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final AtomicIntegerArray nodeCreationStates; // 0 - не создано, 1 - создается, 2 - готово
    Object[] graphObjects;
    private final BitSet refreshListenerIndices = new BitSet();
    private final int[] conditionResultsCache;

    private volatile int globalRemainingTasks;
    private volatile @Nullable Throwable errorChannel;
    private volatile @Nullable Thread mainThread;

    public OptimizedGraphImpl(ApplicationGraphDraw draw) {
        this.draw = draw;
        graphObjects = new Object[draw.size()];
        conditionResultsCache = new int[draw.size()];
        nodeCreationStates = new AtomicIntegerArray(draw.size());
        if (Boolean.getBoolean("kora.graph.debug")) {
            logger = LoggerFactory.getLogger(draw.getRoot());
        }
    }

    @Override
    public ApplicationGraphDraw draw() {
        return draw;
    }

    public AtomicIntegerArray getNodeCreationStates() {
        return nodeCreationStates;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Node<? extends T> node) {
        var index = ((NodeImpl<?>) node).index;
        var val = OBJECTS_VH.getVolatile(graphObjects, index);
        if (val == null) {
            throw new IllegalStateException("Graph node value was not initialized: " + node);
        }
        if (val instanceof ConditionFailedValue failedValue) {
            throw new IllegalStateException("Graph node value was not initialized because condition failed: " + failedValue.reason);
        }
        return (T) val;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getNullable(Node<? extends T> node) {
        var index = ((NodeImpl<?>) node).index;
        var val = OBJECTS_VH.getVolatile(graphObjects, index);
        if (val == null || val instanceof ConditionFailedValue) {
            return null;
        }
        return (T) val;
    }

    @Override
    public GraphCondition condition(Node<? extends GraphCondition> node) {
        var index = ((NodeImpl<?>) node).index;
        var cached = (int) CONDITION_VH.getVolatile(conditionResultsCache, index);
        if (cached != 0) {
            return new CachedCondition(cached, null);
        }
        var computed = get(node).eval();
        var targetVal = computed instanceof ConditionResult.Matched ? 1 : 2;
        if (CONDITION_VH.compareAndSet(conditionResultsCache, index, 0, targetVal)) {
            return new CachedCondition(0, computed);
        }
        var finalCached = (int) CONDITION_VH.getVolatile(conditionResultsCache, index);
        return new CachedCondition(finalCached, null);
    }

    @Override
    public <T> ValueOf<T> valueOf(Node<? extends T> node) {
        return new NodeValueOf<>(this, node);
    }

    @Override
    public <T> PromiseOf<T> promiseOf(Node<? extends T> node) {
        return new NodePromiseOf<>(this, node);
    }

    @Override
    @SafeVarargs
    public final <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... nodes) {
        return new OnePromiseOf<>(this, nodes);
    }

    @Override
    public void init() {
        mutationLock.lock();
        var l = logger;
        if (l != null) {
            l.debug("Dependency container initializing...");
        }
        var started = l != null ? System.nanoTime() : 0L;
        try {
            initializeSubgraph((int) 0, true);
            if (l != null && l.isDebugEnabled()) {
                l.debug("Dependency container initialized in {}ms", tookForLogging(started));
            }
        } catch (Exception e) {
            ensureLogger().debug("Dependency container initialization failed", e);
            throw e;
        } finally {
            mutationLock.unlock();
        }
    }

    private Logger ensureLogger() {
        var l = logger;
        if (l == null) {
            logger = l = LoggerFactory.getLogger(draw.getRoot());
        }
        return l;
    }

    @Override
    public void refresh(Node<?> fromNodeRaw) {
        var fromNode = (NodeImpl<?>) fromNodeRaw;
        mutationLock.lock();
        var fromNodeObj = OBJECTS_VH.getVolatile(graphObjects, fromNode.index);
        var l = logger;
        if (l != null && l.isDebugEnabled()) {
            l.debug("Dependency container refreshing from node {} of class {}...", fromNode.index, fromNodeObj != null ? fromNodeObj.getClass() : "null");
        }
        var started = l != null ? System.nanoTime() : 0L;
        try {
            initializeSubgraph(fromNode.index, false);
            if (l != null && l.isDebugEnabled()) {
                l.debug("Dependency container refreshed in {}ms", tookForLogging(started));
            }
        } catch (Throwable e) {
            ensureLogger().debug("Dependency container refresh error", e);
            throw e;
        } finally {
            mutationLock.unlock();
        }
    }

    @Override
    public void release() {
        mutationLock.lock();
        var l = logger;
        if (l != null) {
            l.debug("Dependency container releasing...");
        }
        var started = l != null ? System.nanoTime() : 0L;
        try {
            releaseAllInternal();
            if (l != null && l.isDebugEnabled()) {
                l.debug("Dependency container released in {}ms", tookForLogging(started));
            }
        } catch (Exception e) {
            ensureLogger().debug("Dependency container releasing failed", e);
            throw e;
        } finally {
            mutationLock.unlock();
        }
    }

    private static long tookForLogging(long started) {
        var tookNanos = System.nanoTime() - started;
        return tookNanos / 1_000_000;
    }

    BitSet getRefreshListenerIndices() {
        return refreshListenerIndices;
    }

    int[] getConditionResultsCache() {
        return conditionResultsCache;
    }

    private void fireRefreshListeners() {
        for (var i = refreshListenerIndices.nextSetBit(0); i >= 0; i = refreshListenerIndices.nextSetBit(i + 1)) {
            var object = OBJECTS_VH.getVolatile(graphObjects, i);
            if (object instanceof RefreshListener listener) {
                try {
                    listener.graphRefreshed();
                } catch (Throwable t) {
                    ensureLogger().warn("Error calling refresh listener for node at index {}", i, t);
                }
            }
        }
    }

    private void initializeSubgraph(int startFrom, boolean isFullInit) {
        var l = logger;
        if (l != null) {
            l.trace("Materializing graph objects {}", startFrom);
        }

        var size = draw.size();
        var dependentNodes = new boolean[size];

        var cEdges = draw.getCreateEdges();
        var cOffsets = draw.getCreateOffsets();
        var rEdges = draw.getRefreshEdges();
        var rOffsets = draw.getRefreshOffsets();
        var iEdges = draw.getInterceptorEdges();
        var iOffsets = draw.getInterceptorOffsets();

        if (isFullInit) {
            Arrays.fill(dependentNodes, true);
        } else {
            dependentNodes[startFrom] = true;

            for (var i = startFrom; i < size; i++) {
                var dependsOnChanged = false;
                var customRefresh = draw.hasCustomRefresh(i);
                var depEdges = customRefresh ? rEdges : cEdges;
                var depOffsets = customRefresh ? rOffsets : cOffsets;
                var start = depOffsets[i];
                var end = depOffsets[i + 1];
                for (var j = start; j < end; j++) {
                    if (dependentNodes[depEdges[j]]) {
                        dependsOnChanged = true;
                        break;
                    }
                }
                if (!dependsOnChanged) {
                    start = iOffsets[i];
                    end = iOffsets[i + 1];
                    for (var j = start; j < end; j++) {
                        if (dependentNodes[iEdges[j]]) {
                            dependsOnChanged = true;
                            break;
                        }
                    }
                }

                if (dependsOnChanged) {
                    dependentNodes[i] = true;
                }
            }
        }

        var isDebug = l != null && l.isDebugEnabled();
        var isTrace = l != null && l.isTraceEnabled();
        var tmpGraph = new OptimizedTmpGraph(this);

        var tasks = new NodeTask[size];
        var inDegrees = new int[size];
        var dsEdges = draw.getDownstreamEdges();
        var dsOffsets = draw.getDownstreamOffsets();
        var totalDependentTasks = 0;

        for (int i = startFrom; i < size; i++) {
            if (!dependentNodes[i]) {
                continue;
            }
            totalDependentTasks++;
            int dsStart = dsOffsets[i];
            int dsEnd = dsOffsets[i + 1];
            for (int k = dsStart; k < dsEnd; k++) {
                int consumerIdx = dsEdges[k];
                if (consumerIdx >= startFrom && dependentNodes[consumerIdx]) {
                    inDegrees[consumerIdx]++;
                }
            }
        }

        for (int i = startFrom; i < size; i++) {
            if (dependentNodes[i]) {
                tasks[i] = new NodeTask(i, inDegrees[i]);
                // println("Node " + i + " total calculated remainingDeps: " + inDegrees[i]);
            }
        }

        if (totalDependentTasks == 0) {
            // println("totalDependentTasks == 0. Fast-committing.");
            tmpGraph.commit(isFullInit);
            fireRefreshListeners();
            return;
        }

        globalRemainingTasks = totalDependentTasks;
        errorChannel = null;
        mainThread = Thread.currentThread();

        for (int i = startFrom; i < size; i++) {
            if (dependentNodes[i]) {
                nodeCreationStates.set(i, 0);
            }
        }

        for (var i = startFrom; i < size; i++) {
            if (dependentNodes[i] && tasks[i] != null && tasks[i].remainingDeps.get() == 0) {
                final var task = tasks[i];
                // println(" Spawning starter Thread for Node " + task.index);
                Thread.ofVirtual().start(new VirtualThreadTask(this, task, startFrom, isFullInit, isDebug, isTrace, tmpGraph, tasks));
            }
        }

        while (globalRemainingTasks > 0 && errorChannel == null) {
            LockSupport.park();
        }

        var error = errorChannel;
        if (error != null) {
            tmpGraph.rollback();
            if (error instanceof CompletionException ce) {
                error = ce.getCause();
            }
            if (error == null) {
                throw new IllegalStateException("Kora internal error: graph initialization failed with null error");
            }
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            if (error instanceof Error) {
                throw (Error) error;
            }
            throw new IllegalStateException("Application graph failed to initialize; see cause for failed component", error);
        }

        tmpGraph.commit(isFullInit);
        fireRefreshListeners();
    }

    private void executeNodeTask(
        NodeTask initialTask,
        int startFrom,
        boolean isFullInit,
        boolean isDebug,
        boolean isTrace,
        OptimizedTmpGraph tmpGraph,
        NodeTask[] tasks
    ) {
        var task = initialTask;
        // var threadName = Thread.currentThread().toString();

        while (task != null) {
            var targetIndex = task.index;
            // println("[" + threadName + "] executeNodeTask enter for Node " + targetIndex);

            if (!task.started.compareAndSet(0, 1)) {
                // println("[" + threadName + "] Node " + targetIndex + " failed started CAS, exiting");
                return;
            }
            // println("[" + threadName + "] Node " + targetIndex + " won started CAS (0 -> 1)");

            if (errorChannel != null || task.isCancelled) {
                // println("[" + threadName + "] Node " + targetIndex + " aborted before execution, errorChannel=" + errorChannel + ", cancelled=" + task.isCancelled);
                decrementGlobalTasks(1, targetIndex, " (aborted)");
                return;
            }

            if (!nodeCreationStates.compareAndSet(targetIndex, 0, 1)) {
                // var currentState = nodeCreationStates.get(targetIndex);
                // println("[" + threadName + "] Node " + targetIndex + " failed nodeCreationStates CAS (0 -> 1), current state=" + currentState);
                decrementGlobalTasks(1, targetIndex, " (skipped duplicate state)");
                task = notifyDownstreamConsumers(targetIndex, startFrom, isFullInit, isDebug, isTrace, tmpGraph, tasks, " (skipped duplicate state)");
                continue;
            }
            // println("[" + threadName + "] Node " + targetIndex + " won nodeCreationStates CAS (0 -> 1)");

            var currentNode = (NodeImpl<?>) draw.getNodeByIndex(targetIndex);
            var start = isDebug ? System.nanoTime() : 0L;
            try {
                // println("[" + threadName + "] Node " + targetIndex + " calling tmpGraph.createNode");
                tmpGraph.createNode(currentNode, startFrom, isFullInit, isTrace);
                // println("[" + threadName + "] Node " + targetIndex + " finished tmpGraph.createNode");

                var currentVal = tmpGraph.getRawTmpArray()[targetIndex];

                if (currentVal instanceof ConditionFailedValue) {
                    // println("[" + threadName + "] Node " + targetIndex + " execution stopped: ConditionFailedValue");
                    OBJECTS_VH.setRelease(graphObjects, targetIndex, currentVal);
                    nodeCreationStates.set(targetIndex, 2);
                    decrementGlobalTasks(1, targetIndex, " (failed condition)");
                    task = notifyDownstreamConsumers(targetIndex, startFrom, isFullInit, isDebug, isTrace, tmpGraph, tasks, " (failed condition)");
                    continue;
                }

                if (currentVal == null) {
                    currentVal = graphObjects[targetIndex];
                    // println("[" + threadName + "] Node " + targetIndex + " tmpArray value is null, rolled back to graphObjects value: " + currentVal);
                }

                if (currentVal != null) {
                    OBJECTS_VH.setRelease(graphObjects, targetIndex, currentVal);
                }

                nodeCreationStates.set(targetIndex, 2);
                // println("[" + threadName + "] Node " + targetIndex + " nodeCreationStates set to 2 (READY)");

                if (isDebug) {
                    var tookNanos = System.nanoTime() - start;
                    if (tookNanos > Long.parseLong(System.getProperty("kora.graph.slowNodeInitThresholdMillis", "100")) * 1_000_000L) {
                        ensureLogger().debug("Initialized node {} at index {} in {}ms", currentNode.type(), currentNode.index, tookNanos / 1_000_000);
                    }
                }

                decrementGlobalTasks(1, targetIndex, " successfully finished");
                task = notifyDownstreamConsumers(targetIndex, startFrom, isFullInit, isDebug, isTrace, tmpGraph, tasks, "");
            } catch (Throwable t) {
                // println("[" + threadName + "] CRITICAL error at Node " + targetIndex + ": " + t.getClass().getName() + " - " + t.getMessage());
                t.printStackTrace();

                nodeCreationStates.set(targetIndex, 0);
                // println("[" + threadName + "] Node " + targetIndex + " nodeCreationStates reset to 0 due to error");

                if (ERROR_CHANNEL_VH.compareAndSet(this, null, t)) {
                    // println("[" + threadName + "] Node " + targetIndex + " successfully set errorChannel, unparking main thread");
                    LockSupport.unpark(mainThread);
                }
                decrementGlobalTasks(1, targetIndex, " failed with exception");
                return;
            }
        }
    }

    private @Nullable NodeTask notifyDownstreamConsumers(
        int targetIndex,
        int startFrom,
        boolean isFullInit,
        boolean isDebug,
        boolean isTrace,
        OptimizedTmpGraph tmpGraph,
        NodeTask[] tasks,
        String logSuffix
    ) {
        var dsOffsets = draw.getDownstreamOffsets();
        var dsEdges = draw.getDownstreamEdges();
        var dsStart = dsOffsets[targetIndex];
        var dsEnd = dsOffsets[targetIndex + 1];

        // println("[" + threadName + "] Node " + targetIndex + " notifyDownstreamConsumers enter, dsStart=" + dsStart + ", dsEnd=" + dsEnd);

        if (dsStart == dsEnd) {
            // println("[" + threadName + "] Node " + targetIndex + " has no downstream consumers");
            return null;
        }

        NodeTask inlineNextTask = null;

        for (var k = dsStart; k < dsEnd; k++) {
            var nextIdx = dsEdges[k];
            var nextTask = tasks[nextIdx];
            if (nextTask == null) {
                // println("[" + threadName + "] Node " + targetIndex + " found null task for downstream index " + nextIdx + ", skipping");
                continue;
            }
            var remaining = nextTask.remainingDeps.decrementAndGet();
            // println("[" + threadName + "] Node " + targetIndex + logSuffix + " decremented Node " + nextIdx + " remainingDeps -> " + remaining);

            if (remaining == 0 && !nextTask.isCancelled) {
                if (inlineNextTask == null) {
                    // println("[" + threadName + "] Node " + nextIdx + " remainingDeps hit 0, running inline in current thread");
                    inlineNextTask = nextTask;
                } else {
                    // println("[" + threadName + "] Node " + nextIdx + " remainingDeps hit 0, spawning virtual thread");
                    Thread.ofVirtual().start(new VirtualThreadTask(this, nextTask, startFrom, isFullInit, isDebug, isTrace, tmpGraph, tasks));
                }
            }
        }
        // println("[" + threadName + "] Node " + targetIndex + " notifyDownstreamConsumers exit");
        return inlineNextTask;
    }

    private void decrementGlobalTasks(int count, int nodeIdx, String statusSuffix) {
        var globalLeft = (int) GLOBAL_REMAINING_VH.getAndAdd(this, -count) - count;
        // println("[" + threadName + "] Node " + nodeIdx + statusSuffix + ". globalRemainingTasks -> " + globalLeft);
        if (globalLeft == 0) {
            // println("[" + threadName + "] Node " + nodeIdx + statusSuffix + " unparking mainThread");
            LockSupport.unpark(mainThread);
        }
    }

    @SuppressWarnings("unchecked")
    private void releaseAllInternal() {
        var size = draw.size();
        Throwable error = null;

        for (var i = (int) (size - 1); i >= 0; i--) {
            try {
                var object = OBJECTS_VH.getVolatile(graphObjects, i);
                if (object == null || object instanceof ConditionFailedValue) continue;

                var interOffsets = draw.getInterceptorOffsets();
                var interEdges = draw.getInterceptorEdges();
                var start = interOffsets[i];
                var end = interOffsets[i + 1];

                for (var j = (int) (end - 1); j >= start; j--) {
                    var interceptorIndex = interEdges[j];
                    var interceptor = (GraphInterceptor<Object>) OBJECTS_VH.getVolatile(graphObjects, interceptorIndex);
                    if (interceptor != null) {
                        object = interceptor.beforeRelease(object);
                    }
                }

                if (object instanceof Lifecycle lifecycle) {
                    lifecycle.release();
                } else if (object instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Throwable t) {
                if (error == null) {
                    error = t;
                } else {
                    error.addSuppressed(t);
                }
            }
        }

        if (error != null) {
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            if (error instanceof Error) {
                throw (Error) error;
            }
            throw new RuntimeException(error);
        }
    }

    private static final class NodeTask {

        final AtomicInteger started = new AtomicInteger(0);
        final AtomicInteger remainingDeps;
        final int index;
        volatile boolean isCancelled = false;

        NodeTask(int index, int initialDeps) {
            this.index = index;
            this.remainingDeps = new AtomicInteger(initialDeps);
        }
    }

    static final class ConditionFailedValue {
        final String reason;

        ConditionFailedValue(String reason) {
            this.reason = reason;
        }
    }

    private static final class CachedCondition implements GraphCondition {
        private final int cachedStatus;
        private final GraphCondition.ConditionResult computedResult;

        CachedCondition(int cachedStatus, GraphCondition.ConditionResult computedResult) {
            this.cachedStatus = cachedStatus;
            this.computedResult = computedResult;
        }

        @Override
        public GraphCondition.ConditionResult eval() {
            if (computedResult != null) {
                return computedResult;
            }
            return cachedStatus == 1
                ? new GraphCondition.ConditionResult.Matched("Cached matched")
                : new GraphCondition.ConditionResult.Failed("Cached failed");
        }
    }

    private static final class NodeValueOf<T> implements ValueOf<T> {
        private final OptimizedGraphImpl graph;
        private final Node<? extends T> node;

        NodeValueOf(OptimizedGraphImpl graph, Node<? extends T> node) {
            this.graph = graph;
            this.node = node;
        }

        @Override
        public T get() {
            return graph.get(node);
        }
    }

    private static final class NodePromiseOf<T> implements PromiseOf<T> {
        private final OptimizedGraphImpl graph;
        private final Node<? extends T> node;

        NodePromiseOf(OptimizedGraphImpl graph, Node<? extends T> node) {
            this.graph = graph;
            this.node = node;
        }

        @Override
        public Optional<T> get() {
            return Optional.of(graph.get(node));
        }
    }

    private static final class OnePromiseOf<N, V> implements PromiseOf<V> {
        private final OptimizedGraphImpl graph;
        private final NodeWithMapper<N, V>[] nodes;

        OnePromiseOf(OptimizedGraphImpl graph, NodeWithMapper<N, V>[] nodes) {
            this.graph = graph;
            this.nodes = nodes;
        }

        @Override
        public Optional<V> get() {
            return Optional.of(graph.getOneOf(nodes));
        }
    }

    private static final class VirtualThreadTask implements Runnable {
        private final OptimizedGraphImpl graph;
        private final NodeTask task;
        private final int startFrom;
        private final boolean isFullInit;
        private final boolean isDebug;
        private final boolean isTrace;
        private final OptimizedTmpGraph tmpGraph;
        private final NodeTask[] tasks;

        VirtualThreadTask(
            OptimizedGraphImpl graph,
            NodeTask task,
            int startFrom,
            boolean isFullInit,
            boolean isDebug,
            boolean isTrace,
            OptimizedTmpGraph tmpGraph,
            NodeTask[] tasks
        ) {
            this.graph = graph;
            this.task = task;
            this.startFrom = startFrom;
            this.isFullInit = isFullInit;
            this.isDebug = isDebug;
            this.isTrace = isTrace;
            this.tmpGraph = tmpGraph;
            this.tasks = tasks;
        }

        @Override
        public void run() {
            graph.executeNodeTask(task, startFrom, isFullInit, isDebug, isTrace, tmpGraph, tasks);
        }
    }
}
