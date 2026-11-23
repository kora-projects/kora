package io.koraframework.application.graph.internal.optimized;

import io.koraframework.application.graph.*;
import io.koraframework.application.graph.GraphCondition.*;
import io.koraframework.application.graph.internal.*;
import io.koraframework.application.graph.internal.optimized.OptimizedGraphImpl.*;
import org.jspecify.annotations.*;

import java.io.*;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static io.koraframework.application.graph.internal.optimized.OptimizedGraphImpl.OBJECTS_VH;

public final class OptimizedTmpGraph implements RefreshableGraph {

    private static final VarHandle TMP_ARRAY_VH = MethodHandles.arrayElementVarHandle(Object[].class);

    private final OptimizedGraphImpl rootGraph;
    private final Object[] tmpArray;
    private final Object[] rootRawObjects;
    private final Object[] oldRawObjects;
    private final AtomicIntegerArray nodeCreationStates;
    private final AtomicReferenceArray<@Nullable ConditionResult> conditionCache;
    private final AtomicReferenceArray<@Nullable TmpValueOf<?>> valueOfs;
    private final AtomicReferenceArray<@Nullable BasePromiseOf<?>> promises;
    private final byte[] changedNodes;
    private final byte[] localRefreshListeners;
    private volatile @Nullable OptimizedGraphImpl delegate;

    public OptimizedTmpGraph(OptimizedGraphImpl rootGraph) {
        this.rootGraph = rootGraph;
        rootRawObjects = rootGraph.graphObjects;
        var length = rootRawObjects.length;
        tmpArray = new Object[length];
        System.arraycopy(rootRawObjects, 0, tmpArray, 0, length);
        oldRawObjects = Arrays.copyOf(rootRawObjects, length);

        nodeCreationStates = rootGraph.getNodeCreationStates();
        valueOfs = new AtomicReferenceArray<>(length);
        promises = new AtomicReferenceArray<>(length);
        conditionCache = new AtomicReferenceArray<>(length);
        changedNodes = new byte[length];
        localRefreshListeners = new byte[length];
    }

    @Override
    public ApplicationGraphDraw draw() {
        return rootGraph.draw();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Node<? extends T> node) {
        var del = delegate;
        if (del != null) {
            return del.get(node);
        }
        var index = ((NodeImpl<?>) node).index;
        var val = TMP_ARRAY_VH.getAcquire(tmpArray, index);
        if (val == null) {
            throw new IllegalStateException("Node not initialized: " + index);
        }
        if (val instanceof OptimizedGraphImpl.ConditionFailedValue failedValue) {
            throw new IllegalStateException("Graph node value was not initialized because condition failed: " + failedValue.reason);
        }
        return (T) val;
    }

    @Override
    public GraphCondition condition(Node<? extends GraphCondition> node) {
        var del = delegate;
        if (del != null) {
            return del.condition(node);
        }
        var index = ((NodeImpl<?>) node).index;
        return () -> {
            var cached = conditionCache.get(index);
            if (cached != null) {
                return cached;
            }
            VarHandle.fullFence();
            var rootCache = rootGraph.getConditionResultsCache()[index];
            if (rootCache != 0 && changedNodes[index] == 0) {
                return rootCache == 1 ? new ConditionResult.Matched("Cached matched") : new ConditionResult.Failed("Cached failed");
            }
            var computed = get(node).eval();
            if (conditionCache.compareAndSet(index, null, computed)) {
                return computed;
            }
            return Objects.requireNonNull(conditionCache.get(index));
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ValueOf<T> valueOf(Node<? extends T> node) {
        var casted = (NodeImpl<T>) node;
        var cached = (TmpValueOf<?>) valueOfs.get(casted.index);
        if (cached != null) {
            return (ValueOf<T>) cached;
        }
        var fixed = (NodeImpl<T>) rootGraph.draw().getNodeByIndex(casted.index);
        var value = new TmpValueOf<>(fixed, this);
        if (valueOfs.compareAndSet(casted.index, null, value)) {
            return value;
        }
        return (ValueOf<T>) Objects.requireNonNull(valueOfs.get(casted.index));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> PromiseOf<T> promiseOf(Node<? extends T> node) {
        var casted = (NodeImpl<T>) node;
        var cached = promises.get(casted.index);
        if (cached instanceof PromiseOfImpl<?> p) {
            return (PromiseOf<T>) p;
        }
        var fixed = (NodeImpl<T>) rootGraph.draw().getNodeByIndex(casted.index);
        var promise = new PromiseOfImpl<>(fixed);
        if (promises.compareAndSet(casted.index, null, promise)) {
            return promise;
        }
        return (PromiseOf<T>) Objects.requireNonNull(promises.get(casted.index));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... oneOfNodes) {
        if (oneOfNodes.length == 0) {
            throw new IllegalArgumentException("Nodes array cannot be empty");
        }
        var primaryIndex = ((NodeImpl<N>) oneOfNodes[0].node()).index;
        var cached = promises.get(primaryIndex);
        if (cached instanceof PromiseOfOneConditionalImpl<?, ?> p) {
            return (PromiseOf<V>) p;
        }

        var fixedNodes = new NodeWithMapper[oneOfNodes.length];
        for (var i = 0; i < oneOfNodes.length; i++) {
            var node = oneOfNodes[i];
            var casted = (NodeImpl<N>) node.node();
            var fixed = (NodeImpl<N>) rootGraph.draw().getNodeByIndex(casted.index);
            fixedNodes[i] = new NodeWithMapper<>(fixed, node.mapper());
        }

        var promise = new PromiseOfOneConditionalImpl<N, V>(fixedNodes);
        if (promises.compareAndSet(primaryIndex, null, promise)) {
            return promise;
        }
        return (PromiseOf<V>) Objects.requireNonNull(promises.get(primaryIndex));
    }

    @SuppressWarnings("unchecked")
    public <T> void createNode(NodeImpl<T> node, int startFrom, boolean isFullInit, boolean isTrace) throws Exception {
        var idx = node.index;
        // var threadName = Thread.currentThread().toString();
        // var graphHash = Integer.toHexString(System.identityHashCode(rootGraph));
        // var tmpGraphHash = Integer.toHexString(System.identityHashCode(this));

        try {
            var oldObject = (T) rootRawObjects[idx];
            var draw = rootGraph.draw();

            if (!isFullInit && oldObject != null && idx != startFrom) {
                var dependencyChanged = false;
                var customRefresh = draw.hasCustomRefresh(idx);
                var depEdges = customRefresh ? draw.getRefreshEdges() : draw.getCreateEdges();
                var depOffsets = customRefresh ? draw.getRefreshOffsets() : draw.getCreateOffsets();
                var start = depOffsets[idx];
                var end = depOffsets[idx + 1];
                VarHandle.acquireFence();
                for (var i = start; i < end; i++) {
                    if (changedNodes[depEdges[i]] == 1) {
                        dependencyChanged = true;
                        break;
                    }
                }
                if (!dependencyChanged) {
                    var iEdges = draw.getInterceptorEdges();
                    var iOffsets = draw.getInterceptorOffsets();
                    start = iOffsets[idx];
                    end = iOffsets[idx + 1];
                    for (var i = start; i < end; i++) {
                        if (changedNodes[iEdges[i]] == 1) {
                            dependencyChanged = true;
                            break;
                        }
                    }
                }
                if (!dependencyChanged) {
                    // println("[" + threadName + "][GRAPH-" + graphHash + "] Node " + idx + " unchanged during refresh, keeping old instance");
                    nodeCreationStates.set(idx, 2);
                    return;
                }
            }

            var conditionFunction = draw.getNodeCondition(idx);
            if (conditionFunction != null) {
                if (conditionFunction.apply(this) instanceof ConditionResult.Failed failed) {
                    TMP_ARRAY_VH.setRelease(tmpArray, idx, new ConditionFailedValue(failed.reason()));
                    changedNodes[idx] = 1;
                    VarHandle.releaseFence();
                    nodeCreationStates.set(idx, 2);
                    return;
                }
            }

            var factory = (Graph.Factory<Object>) rootGraph.draw().getNodeFactory(idx);
            // println("[" + threadName + "][GRAPH-" + graphHash + "] Node " + idx + " calling factory.get()...");
            var newObject = Objects.requireNonNull(factory.get(this, idx));

            var isUnchanged = false;
            if (oldObject != null) {
                try {
                    isUnchanged = (newObject == oldObject || newObject.equals(oldObject));
                } catch (Throwable _) {}
            }

            if (isUnchanged) {
                if (newObject instanceof Lifecycle lifecycle) {
                    lifecycle.release();
                } else if (newObject instanceof Closeable closeable) {
                    closeable.close();
                }
                // println("[" + threadName + "][GRAPH-" + graphHash + "] Node " + idx + " factory returned identical object, rolled back to old instance");
                nodeCreationStates.set(idx, 2);
                return;
            }

            if (newObject instanceof RefreshListener) {
                localRefreshListeners[idx] = 1;
            }

            var iEdges = draw.getInterceptorEdges();
            var iOffsets = draw.getInterceptorOffsets();
            var interStart = iOffsets[idx];
            var interEnd = iOffsets[idx + 1];

            if (interStart != interEnd) {
                for (var i = interStart; i < interEnd; i++) {
                    var interceptorIndex = iEdges[i];
                    var interceptorObject = (GraphInterceptor<Object>) TMP_ARRAY_VH.getAcquire(tmpArray, interceptorIndex);
                    if (interceptorObject != null) {
                        newObject = interceptorObject.afterInit(newObject);
                    }
                }
            }
            TMP_ARRAY_VH.setRelease(tmpArray, idx, newObject);
            changedNodes[idx] = 1;
            VarHandle.releaseFence();

            if (newObject instanceof Lifecycle lifecycle) {
                // println("[" + threadName + "][GRAPH-" + graphHash + "] Node " + idx + " calling lifecycle.init() for " + lifecycle.getClass().getName());
                lifecycle.init();
                // println("[" + threadName + "][GRAPH-" + graphHash + "] Node " + idx + " lifecycle.init() success");
            }

            // println("[" + threadName + "][GRAPH-" + graphHash + "] Node " + idx + " fully complete inside tmpGraph");
        } catch (Throwable t) {
            // println("[" + threadName + "][GRAPH-" + graphHash + "] Node " + idx + " failed with exception inside tmpGraph, resetting state to 0");
            nodeCreationStates.set(idx, 0);
            throw t;
        }
    }

    @SuppressWarnings("unchecked")
    public void commit(boolean isFullInit) {
        var length = changedNodes.length;
        var oldValuesToRelease = !isFullInit ? new Object[length] : null;
        var rootListeners = rootGraph.getRefreshListenerIndices();
        var cache = rootGraph.getConditionResultsCache();

        for (var i = 0; i < length; i++) {
            if (changedNodes[i] == 1) {
                var newVal = tmpArray[i];
                if (newVal != null) {
                    if (oldValuesToRelease != null) {
                        oldValuesToRelease[i] = oldRawObjects[i];
                    }
                    OBJECTS_VH.setRelease(rootRawObjects, i, newVal);
                }

                if (localRefreshListeners[i] == 1) {
                    rootListeners.set(i);
                } else {
                    rootListeners.clear(i);
                }

                nodeCreationStates.set(i, 0);
            } else {
                if (nodeCreationStates.get(i) == 1) {
                    nodeCreationStates.set(i, 0);
                }
            }

            var cond = conditionCache.get(i);
            if (cond != null) {
                var targetVal = cond instanceof ConditionResult.Matched ? 1 : 2;
                cache[i] = targetVal;
            }
        }

        if (!isFullInit && oldValuesToRelease != null) {
            var draw = rootGraph.draw();
            var iEdges = draw.getInterceptorEdges();
            var iOffsets = draw.getInterceptorOffsets();

            for (var i = length - 1; i >= 0; i--) {
                var oldVal = oldValuesToRelease[i];
                if (oldVal == null) {
                    continue;
                }

                try {
                    var interStart = iOffsets[i];
                    var interEnd = iOffsets[i + 1];

                    for (var j = interEnd - 1; j >= interStart; j--) {
                        var interceptorIndex = iEdges[j];
                        var interceptorObject = (GraphInterceptor<Object>) oldValuesToRelease[interceptorIndex];
                        if (interceptorObject == null) {
                            interceptorObject = (GraphInterceptor<Object>) rootRawObjects[interceptorIndex];
                        }
                        if (interceptorObject != null) {
                            oldVal = interceptorObject.beforeRelease(oldVal);
                        }
                    }

                    if (oldVal instanceof Lifecycle lifecycle) {
                        lifecycle.release();
                    } else if (oldVal instanceof AutoCloseable closeable) {
                        closeable.close();
                    }
                } catch (Exception _) {}
            }
        }
        delegate = rootGraph;
        VarHandle.fullFence();
    }

    public void rollback() {
        VarHandle.fullFence();
        var length = tmpArray.length;
        for (var i = length - 1; i >= 0; i--) {
            var currentTmp = tmpArray[i];
            var original = oldRawObjects[i];
            if (currentTmp != original && currentTmp != null) {
                try {
                    if (currentTmp instanceof Lifecycle lifecycle) {
                        lifecycle.release();
                    } else if (currentTmp instanceof Closeable closeable) {
                        closeable.close();
                    }
                } catch (Exception _) {}
            }
            if (changedNodes[i] == 1) {
                OBJECTS_VH.setRelease(rootRawObjects, i, original);
            }
            nodeCreationStates.set(i, 0);
        }
    }

    @Override
    public void refresh(Node<?> fromNode) {
        rootGraph.refresh(fromNode);
    }

    Object[] getRawTmpArray() {
        return tmpArray;
    }

    private static class TmpValueOf<T> implements ValueOf<T> {
        private final OptimizedTmpGraph tmpGraph;
        private final NodeImpl<? extends T> node;

        private TmpValueOf(NodeImpl<? extends T> node, OptimizedTmpGraph tmpGraph) {
            this.node = node;
            this.tmpGraph = tmpGraph;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get() {
            var del = tmpGraph.delegate;
            if (del != null) {
                return del.get(node);
            }
            var val = tmpGraph.getRawTmpArray()[node.index];
            if (val == null || val instanceof OptimizedGraphImpl.ConditionFailedValue) {
                return tmpGraph.rootGraph.get(node);
            }
            return (T) val;
        }
    }
}
