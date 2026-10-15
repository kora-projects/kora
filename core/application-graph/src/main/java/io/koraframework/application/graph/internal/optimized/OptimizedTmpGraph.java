package io.koraframework.application.graph.internal.optimized;

import io.koraframework.application.graph.*;
import io.koraframework.application.graph.GraphCondition.*;
import io.koraframework.application.graph.internal.*;
import io.koraframework.application.graph.internal.optimized.OptimizedGraphImpl.*;
import org.jspecify.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/// An isolated, transactional scratchpad implementation of [RefreshableGraph]
/// that facilitates atomic dependency updates and guarantees container state consistency.
///
/// OptimizedTmpGraph acts as a sequential **Unit of Work** during container bootstrap and refresh phases.
/// It isolates in-flight component materialization from the active, live application state,
/// preventing incomplete or broken graphs from corrupting the running container.
///
/// ### Operational Design & State Isolation
///
/// * **Transactional Isolation:** Upon creation, it references a flat, atomic snapshot of current
///   active references (rootRawObjects). Any new bean instantiation writes strictly to a temporary
///   tmpArray mapped directly via the node's unique, constant node.index.
/// * **Lock-Free Execution Guard:** By leveraging the compiler-guaranteed topological sorting, the single-threaded
///   traversal loop sequentially materializes components without any synchronization overhead, lock contention,
///   or execution bottlenecks.
/// * **Allocation-Free Mutation Tracking:** Tracks mutated slots using a primitive byte[] changedNodes
///   array. This completely bypasses dynamic collection allocations and reduces change tracking state lookup
///   to a single-tact CPU array instruction (O(1) access).
///
/// ### Core Transactional Mechanics
///
/// 1. **Materialization ([#createNode(NodeImpl, int)]):**
///    Invoked sequentially by the parent graph during linear graph traversal. It evaluates
///    [GraphCondition] configurations, triggers the factory lambda, invokes init() lifecycle hooks,
///    and decorates instances via compiled [GraphInterceptor] chains before saving them into the scratchpad.
///
/// 2. **Transactional Commit ([#commit(int)]):**
///    Triggered by the parent graph only when the entire target sub-graph is materialized without errors.
///    It performs a bulk, atomic reference swap into the live container state, re-binds live [ValueOf]
///    and [PromiseOf] proxies, and safely activates a volatile delegate redirecting ongoing lookups
///    from long-lived proxies to the main container. Finally, it gracefully terminates stale replaced components
///    in reverse topological order.
///
/// 3. **Transactional Rollback ([#rollback()]):**
///    Invoked abruptly if any component factory or lifecycle hook throws an unhandled exception.
///    It instantly cleans up and closes all partially instantiated temporary resources within this
///    scratchpad scope, leaving the master application state completely untouched and perfectly stable.
///
/// @see OptimizedGraphImpl
public final class OptimizedTmpGraph implements RefreshableGraph {

    private final OptimizedGraphImpl rootGraph;
    private final AtomicReferenceArray<@Nullable Object> tmpArray;
    private final AtomicReferenceArray<@Nullable Object> rootRawObjects;
    private final AtomicReferenceArray<@Nullable ConditionResult> conditionCache;
    private final @Nullable TmpValueOf<?>[] valueOfs;
    private final @Nullable BasePromiseOf<?>[] promises;
    private final byte[] changedNodes;
    private volatile @Nullable OptimizedGraphImpl delegate;

    public OptimizedTmpGraph(OptimizedGraphImpl rootGraph) {
        this.rootGraph = rootGraph;
        this.rootRawObjects = rootGraph.getRawObjects();
        var length = this.rootRawObjects.length();
        this.tmpArray = new AtomicReferenceArray<>(length);
        for (var i = 0; i < length; i++) {
            this.tmpArray.set(i, this.rootRawObjects.get(i));
        }
        this.valueOfs = new TmpValueOf<?>[length];
        this.promises = new BasePromiseOf<?>[length];
        this.conditionCache = new AtomicReferenceArray<>(length);
        this.changedNodes = new byte[length];
    }

    @Override
    public ApplicationGraphDraw draw() {
        return rootGraph.draw();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Node<? extends T> node) {
        var del = this.delegate;
        if (del != null) {
            return del.get(node);
        }
        var index = ((NodeImpl<?>) node).index;
        var val = tmpArray.get(index);
        if (val == null) {
            throw new IllegalStateException("Node not initialized: " + index);
        }
        if (val instanceof ConditionFailedValue) {
            throw new IllegalStateException("Graph node value was not initialized because condition failed: " + ((ConditionFailedValue) val).reason);
        }
        return (T) val;
    }

    @Override
    public GraphCondition condition(Node<? extends GraphCondition> node) {
        var del = this.delegate;
        if (del != null) {
            return del.condition(node);
        }
        var index = ((NodeImpl<?>) node).index;
        return () -> {
            var cached = conditionCache.get(index);
            if (cached != null) {
                return cached;
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
        var fixed = (NodeImpl<T>) rootGraph.draw().getNodes().get(casted.index);
        var value = new TmpValueOf<>(fixed, this);
        valueOfs[casted.index] = value;
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> PromiseOf<T> promiseOf(Node<? extends T> node) {
        var casted = (NodeImpl<T>) node;
        var fixed = (NodeImpl<T>) rootGraph.draw().getNodes().get(casted.index);
        var promise = new PromiseOfImpl<>(fixed);
        promises[casted.index] = promise;
        return promise;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... oneOfNodes) {
        var fixedNodes = new NodeWithMapper[oneOfNodes.length];
        var nodesList = rootGraph.draw().getNodes();
        var primaryIndex = -1;

        for (var i = 0; i < oneOfNodes.length; i++) {
            var node = oneOfNodes[i];
            var casted = (NodeImpl<N>) node.node();
            if (i == 0) {
                primaryIndex = casted.index;
            }
            var fixed = (NodeImpl<N>) nodesList.get(casted.index);
            fixedNodes[i] = new NodeWithMapper<>(fixed, node.mapper());
        }

        var promise = new PromiseOfOneConditionalImpl<N, V>(fixedNodes);
        if (primaryIndex >= 0) {
            promises[primaryIndex] = promise;
        }
        return promise;
    }

    @SuppressWarnings("unchecked")
    public <T> void createNode(NodeImpl<T> node, int startFrom) throws Exception {
        final boolean isTrace = rootGraph.logger.isTraceEnabled();
        var oldObject = (T) rootRawObjects.get(node.index);

        if (oldObject != null && node.index != startFrom) {
            var dependencyChanged = false;
            var refreshDeps = node.refreshDependencies;
            for (int i = 0; i < refreshDeps.size(); i++) {
                if (changedNodes[((NodeImpl<?>) refreshDeps.get(i)).index] == 1) {
                    dependencyChanged = true;
                    break;
                }
            }
            if (!dependencyChanged) {
                var interceptors = node.interceptors;
                for (int i = 0; i < interceptors.size(); i++) {
                    if (changedNodes[((NodeImpl<?>) interceptors.get(i)).index] == 1) {
                        dependencyChanged = true;
                        break;
                    }
                }
            }
            if (!dependencyChanged) return;
        }

        if (node.condition() != null) {
            if (node.condition().apply(this) instanceof ConditionResult.Failed failed) {
                if (isTrace) {
                    rootGraph.logger.trace("Creating node {} canceled: {}", node.index, failed.reason());
                }
                tmpArray.set(node.index, new ConditionFailedValue(failed));
                changedNodes[node.index] = 1;
                return;
            }
        }

        if (isTrace) {
            var dependenciesStr = node.createDependencies.stream().map(Node::toString).collect(java.util.stream.Collectors.joining(",", "[", "]"));
            rootGraph.logger.trace("Creating node {}, dependencies {}", node.index, dependenciesStr);
        }

        var newObject = Objects.requireNonNull(node.factory.get(this));

        var isUnchanged = false;
        if (oldObject != null) {
            try {
                isUnchanged = (newObject == oldObject || newObject.equals(oldObject));
            } catch (Throwable _) {}
        }

        if (isUnchanged) {
            if (newObject instanceof Lifecycle lifecycle) {lifecycle.release();} else if (newObject instanceof Closeable closeable) closeable.close();
            return;
        }

        tmpArray.set(node.index, newObject);
        changedNodes[node.index] = 1;

        if (newObject instanceof RefreshListener) {
            rootGraph.getRefreshListenerIndices().set(node.index);
        }

        if (isTrace) {
            rootGraph.logger.trace("Created node {} {}", node.index, newObject.getClass());
        }

        if (newObject instanceof Lifecycle lifecycle) {
            if (isTrace) {
                rootGraph.logger.trace("Initializing node {} of class {}", node.index, lifecycle.getClass());
            }
            try {
                lifecycle.init();
                if (isTrace) {
                    rootGraph.logger.trace("Node Initializing {} of class {} complete", node.index, lifecycle.getClass());
                }
            } catch (CancellationException e) {
                if (isTrace) {
                    rootGraph.logger.trace("Node Initializing {} of class {} cancelled", node.index, lifecycle.getClass());
                }
                throw e;
            } catch (CompletionException ce) {
                if (isTrace) {
                    rootGraph.logger.trace("Node Initializing {} of class {} error", node.index, lifecycle.getClass(), ce.getCause());
                }
                throw ce;
            } catch (RuntimeException | Error e) {
                if (isTrace) {
                    rootGraph.logger.trace("Node Initializing {} of class {} error", node.index, lifecycle.getClass(), e);
                }
                throw e;
            } catch (Throwable e) {
                if (isTrace) {
                    rootGraph.logger.trace("Initializing node {} of class {} error", node.index, lifecycle.getClass(), e);
                }
                throw new IllegalStateException("Lifecycle init failed with checked exception for node %s at index %s".formatted(node.type, node.index), e);
            }
        }

        var interceptors = node.interceptors;
        if (!interceptors.isEmpty()) {
            for (int i = 0; i < interceptors.size(); i++) {
                var interceptorNode = (NodeImpl<? extends GraphInterceptor<T>>) interceptors.get(i);
                var interceptorObject = (GraphInterceptor<T>) get(interceptorNode);

                if (isTrace) {
                    rootGraph.logger.trace("Intercepting init node {} of class {} with node {} of class {}", node.index, newObject.getClass(), interceptorNode.index, interceptorObject.getClass());
                }
                try {
                    var intercepted = interceptorObject.afterInit(newObject);
                    if (isTrace) {
                        rootGraph.logger.trace("Intercepting init node {} of class {} with node {} of class {} complete", node.index, newObject.getClass(), interceptorNode.index, interceptorObject.getClass());
                    }
                    newObject = intercepted;
                } catch (RuntimeException | Error e) {
                    if (isTrace) {
                        rootGraph.logger.trace("Intercepting init node {} of class {} with node {} of class {} error", node.index, newObject.getClass(), interceptorNode.index, interceptorObject.getClass(), e);
                    }
                    throw e;
                } catch (Throwable e) {
                    if (isTrace) {
                        rootGraph.logger.trace("Intercepting init node {} of class {} with node {} of class {} error", node.index, newObject.getClass(), interceptorNode.index, interceptorObject.getClass(), e);
                    }
                    throw new IllegalStateException("Graph interceptor failed with checked exception for node %s and interceptor %s".formatted(node.type, interceptorNode.type), e);
                }
            }
            tmpArray.set(node.index, newObject);
        }
    }

    @SuppressWarnings("unchecked")
    public void commit(int startFrom) {
        var length = changedNodes.length;
        var oldValuesToRelease = new Object[length];

        for (var i = 0; i < length; i++) {
            if (changedNodes[i] == 1) {
                var newVal = tmpArray.get(i);
                if (newVal != null) {
                    oldValuesToRelease[i] = rootRawObjects.get(i);
                    rootRawObjects.set(i, newVal);
                }
            }
        }
        for (var i = 0; i < length; i++) {
            var v = valueOfs[i];
            if (v != null) {
                v.tmpGraph = rootGraph;
            }
        }

        for (var i = 0; i < length; i++) {
            var p = promises[i];
            if (p != null) {
                p.graph = rootGraph;
            }
        }

        var cache = rootGraph.getConditionResultsCache();
        var condCacheLength = conditionCache.length();
        for (var i = 0; i < condCacheLength; i++) {
            var cond = conditionCache.get(i);
            if (cond != null) {
                cache.put(i, cond);
            }
        }

        if (startFrom > 0) {
            var nodes = rootGraph.draw().getNodes();
            for (var i = length - 1; i >= 0; i--) {
                var oldVal = oldValuesToRelease[i];
                if (oldVal == null) continue;

                try {
                    var node = (NodeImpl<Object>) nodes.get(i);
                    var interceptors = node.interceptors;

                    for (var j = interceptors.size() - 1; j >= 0; j--) {
                        var interceptorNode = (NodeImpl<? extends GraphInterceptor<Object>>) interceptors.get(j);
                        var interceptorObject = (GraphInterceptor<Object>) oldValuesToRelease[interceptorNode.index];
                        if (interceptorObject == null) {
                            interceptorObject = (GraphInterceptor<Object>) rootRawObjects.get(interceptorNode.index);
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
                } catch (Exception _) {
                }
            }
        }
        this.delegate = rootGraph;
    }

    public void rollback() {
        var length = tmpArray.length();
        for (var i = length - 1; i >= 0; i--) {
            var currentTmp = tmpArray.get(i);
            var original = rootRawObjects.get(i);
            if (currentTmp != original && currentTmp != null) {
                try {
                    if (currentTmp instanceof Lifecycle lifecycle) {
                        lifecycle.release();
                    } else if (currentTmp instanceof Closeable closeable) {
                        closeable.close();
                    }
                } catch (Exception _) {
                }
            }
        }
    }

    @Override
    public void refresh(Node<?> fromNode) {
        rootGraph.refresh(fromNode);
    }

    private static class TmpValueOf<T> implements ValueOf<T> {
        public volatile Graph tmpGraph;
        private final NodeImpl<? extends T> node;

        private TmpValueOf(NodeImpl<? extends T> node, Graph tmpGraph) {
            this.node = node;
            this.tmpGraph = tmpGraph;
        }

        @Override
        public T get() {
            return this.tmpGraph.get(this.node);
        }
    }
}
