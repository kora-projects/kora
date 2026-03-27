package io.koraframework.application.graph.internal;

import io.koraframework.application.graph.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public final class GraphImpl implements InitializedGraph {
    private static final long SLOW_NODE_INIT_THRESHOLD = Long.parseLong(System.getProperty("kora.graph.slowNodeInitThresholdMillis", "100"));
    private static final CompletableFuture<Void> EMPTY_FUTURE = CompletableFuture.completedFuture(null);

    private final ApplicationGraphDraw draw;
    private final Logger log;
    private final Set<Integer> refreshListenerNodes = new HashSet<>();

    private volatile AtomicReferenceArray<@Nullable Object> objects;
    private final ReentrantLock initLock = new ReentrantLock();

    public GraphImpl(ApplicationGraphDraw draw) {
        this.draw = draw;
        this.log = LoggerFactory.getLogger(this.draw.getRoot());
        this.objects = new AtomicReferenceArray<>(this.draw.size());
    }

    @Override
    public ApplicationGraphDraw draw() {
        return this.draw;
    }

    private record GraphConditionKey(ApplicationGraphDraw draw, Node<? extends GraphCondition> node) {
        @Override
        public int hashCode() {return toImpl(draw, node).index;}

        @Override
        public boolean equals(Object obj) {return obj instanceof GraphConditionKey(var draw, var node) && node == this.node && draw == this.draw;}
    }

    private record ConditionFailedGraphValue(GraphCondition.ConditionResult.Failed reason) {}

    private final ConcurrentMap<GraphConditionKey, GraphCondition.ConditionResult> conditionResultsCache = new ConcurrentHashMap<>();

    @Override
    public GraphCondition condition(Node<? extends GraphCondition> node) {
        return () -> conditionResultsCache.computeIfAbsent(
            new GraphConditionKey(draw, node),
            key -> get(key.node).eval()
        );
    }

    @Override
    public <T> T get(Node<? extends T> node) {
        return getImpl(this.draw, this.objects, node);
    }

    private static <T> NodeImpl<T> toImpl(ApplicationGraphDraw draw, Node<T> node) {
        if (node instanceof NodeImpl<T> impl) {
            if (impl.graphDraw != draw) {
                throw new IllegalArgumentException("Node is from another graph");
            }
            return impl;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getImpl(ApplicationGraphDraw draw, AtomicReferenceArray<@Nullable Object> objects, Node<? extends T> node) {
        var value = objects.get(toImpl(draw, node).index);
        if (value == null) {
            throw new IllegalStateException("Value was not initialized");
        }
        if (value instanceof ConditionFailedGraphValue(GraphCondition.ConditionResult.Failed(var reason))) {
            throw new RuntimeException("Node value was not initialized: " + reason);
        }
        return (T) value;
    }

    @Override
    public <T> ValueOf<T> valueOf(final Node<? extends T> node) {
        var _ = toImpl(draw, node);
        return () -> GraphImpl.this.get(node);
    }

    @Override
    public <T> PromiseOf<T> promiseOf(final Node<? extends T> node) {
        var _ = toImpl(draw, node);
        return () -> Optional.of(this.get(node));
    }

    @Override
    public <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... nodes) {
        for (var node : nodes) {
            var _ = toImpl(draw, node.node());
        }
        return () -> Optional.of(this.getOneOf(nodes));
    }

    @Override
    public void refresh(Node<?> fromNodeRaw) {
        var fromNode = toImpl(draw, fromNodeRaw);
        var root = new BitSet(this.objects.length());
        root.set(fromNode.index);
        this.initLock.lock();

        log.debug("Dependency container refreshing from node {} of class {}...", fromNode.index, this.objects.get(fromNode.index).getClass());
        final long started = log.isDebugEnabled() ? started() : 0;
        try {
            this.initializeSubgraph(fromNode.index);
            if (log.isDebugEnabled()) {
                log.debug("Dependency container refreshed in {}", tookForLogging(started));
            }
        } catch (Throwable e) {
            log.debug("Dependency container refresh error", e);
            throw e;
        } finally {
            this.initLock.unlock();
        }
    }

    @Override
    public void init() {
        var root = new BitSet(this.objects.length());
        root.set(0, this.objects.length());
        this.initLock.lock();

        log.debug("Dependency container initializing...");
        final long started = started();
        try {
            this.initializeSubgraph(0);
            log.debug("Dependency container initialized in {}", tookForLogging(started));
        } catch (Exception e) {
            log.debug("Dependency container initialization failed", e);
            throw e;
        } finally {
            this.initLock.unlock();
        }
    }

    @Override
    public void release() {
        var root = new BitSet(this.objects.length());
        root.set(0, this.objects.length());
        this.initLock.lock();
        log.debug("Dependency container releasing...");
        final long started = started();
        try {
            this.releaseNodes(this.objects, root);
            log.debug("Dependency container released in {}", tookForLogging(started));
        } catch (Exception e) {
            log.debug("Dependency container releasing failed", e);
            throw e;
        } finally {
            this.initLock.unlock();
        }
    }

    private void initializeSubgraph(int startFrom) {
        log.trace("Materializing graph objects {}", startFrom);
        this.conditionResultsCache.clear();
        var tmpGraph = new TmpGraph(this);
        var errors = tmpGraph.init(startFrom);
        if (!errors.isEmpty()) {
            try {
                this.releaseNodes(tmpGraph.tmpArray, tmpGraph.initialized);
            } catch (Throwable e) {
                this.log.warn("Error on releasing temporary objects after init error", e);
            }
            if (errors.size() == 1) {
                switch (errors.getFirst()) {
                    case RuntimeException re -> throw re;
                    case Error e -> throw e;
                    case null -> throw new IllegalStateException();
                    default -> throw new RuntimeException("Failed to initialize graph", errors.getFirst());
                }
            }
            var re = new RuntimeException("Failed to initialize graph");
            for (var error : errors) {
                if (error != re) {
                    re.addSuppressed(error);
                }
            }
            throw re;
        }
        var oldObjects = this.objects;
        this.objects = tmpGraph.tmpArray;
        for (var newValue : tmpGraph.newValueOf) {
            newValue.tmpGraph = GraphImpl.this;
        }
        for (var newPromise : tmpGraph.newPromises) {
            newPromise.graph = GraphImpl.this;
        }
        try {
            this.releaseNodes(oldObjects, tmpGraph.initialized);
        } catch (Throwable e) {
            this.log.warn("Error on releasing temporary objects after init error", e);
        }
        log.trace("Dependency container refreshed, calling interceptors...");
        for (var refreshListenerNode : this.refreshListenerNodes) {
            if (this.objects.get(refreshListenerNode) instanceof RefreshListener refreshListener) {
                try {
                    refreshListener.graphRefreshed();
                } catch (Exception e) {
                    log.warn("Exception caught when calling listener.graphRefreshed(), object={}", refreshListener);
                }
            }
        }
        this.conditionResultsCache.putAll(tmpGraph.conditionResultsCache);
        tmpGraph.delegate = this;
        log.trace("Dependency container refreshed, ");
    }

    private void releaseNodes(AtomicReferenceArray<Object> objects, BitSet root) {
        var release = new CompletableFuture<?>[objects.length()];
        var locks = new ArrayList<ReadWriteLock>(objects.length());
        for (int i = 0; i < this.draw.getNodes().size(); i++) {
            var lock = new ReentrantReadWriteLock();
            locks.add(lock);
        }
        var barrier = new CyclicBarrier(root.cardinality());
        for (int i = objects.length() - 1; i >= 0; i--) {
            if (!root.get(i)) {
                release[i] = EMPTY_FUTURE;
                continue;
            }
            var node = toImpl(draw, this.draw.getNodes().get(i));
            var future = new CompletableFuture<@Nullable Void>();
            var lock = locks.get(i);
            Thread.ofVirtual().name("release-" + i).start(() -> {
                for (var dependencyNode : node.createDependencies) {
                    locks.get(toImpl(draw, dependencyNode).index).readLock().lock();
                }
                for (var interceptorNode : node.interceptors) {
                    locks.get(toImpl(draw, interceptorNode).index).readLock().lock();
                }
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    future.completeExceptionally(e); // todo do we need to ignore it maybe?
                    return;
                }

                lock.writeLock().lock();
                try {
                    this.release(objects, node);
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                } finally {
                    lock.writeLock().unlock();
                    for (var dependencyNode : node.createDependencies) {
                        locks.get(toImpl(draw, dependencyNode).index).readLock().unlock();
                    }
                    for (var interceptorNode : node.interceptors) {
                        locks.get(toImpl(draw, interceptorNode).index).readLock().unlock();
                    }
                }
            });
            release[i] = future;
        }
        // todo await
        CompletableFuture.allOf(release).join();
    }

    private <T> void release(AtomicReferenceArray<@Nullable Object> objects, NodeImpl<T> node) throws Throwable {
        @SuppressWarnings("unchecked")
        T object = (T) objects.get(node.index);
        if (object == null) {
            return;
        }
        var i = node.interceptors.listIterator(node.interceptors.size());
        var error = (Throwable) null;
        while (i.hasPrevious()) {
            var interceptorNode = (NodeImpl<? extends GraphInterceptor<T>>) i.previous();
            @SuppressWarnings("unchecked")
            var interceptor = (GraphInterceptor<T>) objects.get(interceptorNode.index);
            this.log.trace("Intercepting release node {} of class {} with node {} of class {}", node.index, object.getClass(), interceptorNode.index, interceptor.getClass());
            try {
                var intercepted = interceptor.release(object);
                log.trace("Intercepting release node {} of class {} with node {} of class {} complete", node.index, object.getClass(), interceptorNode.index, interceptor.getClass());
                object = intercepted;
            } catch (Throwable e) {
                this.log.trace("Intercepting release node {} of class {} with node {} of class {} error", node.index, object.getClass(), interceptorNode.index, interceptor.getClass(), e);
                if (error == null) {
                    error = e;
                } else {
                    error.addSuppressed(e);
                }
            }
        }
        if (object instanceof Lifecycle lifecycle) {
            try {
                lifecycle.release();
            } catch (Throwable e) {
                if (error == null) {
                    error = e;
                } else {
                    error.addSuppressed(e);
                }
            }
            log.trace("Node {} of class {} released", node.index, object.getClass());
        }
        if (object instanceof AutoCloseable closeable) {
            log.trace("Releasing node {} of class {}", node.index, object.getClass());
            try {
                closeable.close();
            } catch (Throwable e) {
                if (error == null) {
                    error = e;
                } else {
                    error.addSuppressed(e);
                }
            }
            log.trace("Node {} of class {} released", node.index, object.getClass());
        }
        if (error != null) {
            throw error;
        }
    }

    private static class TmpGraph implements RefreshableGraph {
        private final GraphImpl rootGraph;
        private final AtomicReferenceArray<@Nullable Object> tmpArray;
        private final Collection<TmpValueOf<?>> newValueOf = new ConcurrentLinkedDeque<>();
        private final Collection<BasePromiseOf<?>> newPromises = new ConcurrentLinkedDeque<>();
        private final AtomicReferenceArray<@Nullable CompletableFuture<@Nullable Void>> inits;
        private final BitSet initialized;
        private final boolean debugEnabled;
        @Nullable
        public volatile GraphImpl delegate;

        private TmpGraph(GraphImpl rootGraph) {
            this.rootGraph = rootGraph;
            this.tmpArray = new AtomicReferenceArray<>(this.rootGraph.objects.length());
            for (int i = 0; i < this.rootGraph.objects.length(); i++) {
                this.tmpArray.set(i, this.rootGraph.objects.get(i));
            }
            this.inits = new AtomicReferenceArray<>(this.tmpArray.length());
            this.initialized = new BitSet(this.tmpArray.length());
            this.debugEnabled = this.rootGraph.log.isDebugEnabled();
        }

        private final ConcurrentMap<GraphConditionKey, GraphCondition.ConditionResult> conditionResultsCache = new ConcurrentHashMap<>();

        @Override
        public GraphCondition condition(Node<? extends GraphCondition> node) {
            var delegate = this.delegate;
            if (delegate != null) {
                return delegate.condition(node);
            }
            return () -> conditionResultsCache.computeIfAbsent(
                new GraphConditionKey(this.rootGraph.draw, node),
                key -> get(key.node).eval()
            );
        }

        @Override
        public ApplicationGraphDraw draw() {
            return this.rootGraph.draw();
        }

        @Override
        public <T> T get(Node<? extends T> node) {
            var delegate = this.delegate;
            if (delegate != null) {
                return delegate.get(node);
            }
            return getImpl(this.rootGraph.draw, this.tmpArray, node);
        }

        @Override
        public final <T> ValueOf<T> valueOf(Node<? extends T> node) {
            var delegate = this.delegate;
            if (delegate != null) {
                return delegate.valueOf(node);
            }

            var casted = (NodeImpl<? extends T>) node;
            // dirty hack to make copied graph work with valueOf
            @SuppressWarnings("unchecked")
            var fixed = (NodeImpl<? extends T>) this.rootGraph.draw.getNodes().get(casted.index);
            var value = new TmpValueOf<T>(fixed, this);
            this.newValueOf.add(value);
            return value;
        }

        @Override
        public final <T> PromiseOf<T> promiseOf(Node<? extends T> node) {
            var delegate = this.delegate;
            if (delegate != null) {
                return delegate.promiseOf(node);
            }

            var casted = (NodeImpl<? extends T>) node;
            // dirty hack to make copied graph work with valueOf
            @SuppressWarnings("unchecked")
            var fixed = (NodeImpl<? extends T>) this.rootGraph.draw.getNodes().get(casted.index);
            var promise = new PromiseOfImpl<T>(fixed);
            this.newPromises.add(promise);
            return promise;
        }

        @Override
        @SuppressWarnings("unchecked")
        public final <N, V> PromiseOf<V> getOnePromiseOf(NodeWithMapper<N, V>... nodes) {
            var delegate = this.delegate;
            if (delegate != null) {
                return delegate.getOnePromiseOf(nodes);
            }

            NodeWithMapper<N, V>[] fixedNodes = new NodeWithMapper[nodes.length];

            for (int i = 0; i < nodes.length; i++) {
                var node = nodes[i];
                toImpl(this.rootGraph.draw, node.node());
                var casted = (NodeImpl<? extends N>) toImpl(this.rootGraph.draw, node.node());
                var fixed = (NodeImpl<? extends N>) this.rootGraph.draw.getNodes().get(casted.index);
                fixedNodes[i] = new NodeWithMapper<>(fixed, node.mapper());
            }

            var promise = new PromiseOfOneConditionalImpl<>(fixedNodes);
            this.newPromises.add(promise);
            return promise;
        }

        @Override
        @SafeVarargs
        public final <N, V> V getOneOf(NodeWithMapper<N, V>... nodes) {
            var delegate = this.delegate;
            if (delegate != null) {
                return delegate.getOneOf(nodes);
            }
            return RefreshableGraph.super.getOneOf(nodes);
        }

        @Override
        @SafeVarargs
        public final <N, V> ValueOf<V> getOneValueOf(NodeWithMapper<N, V>... nodes) {
            var delegate = this.delegate;
            if (delegate != null) {
                return delegate.getOneValueOf(nodes);
            }
            return RefreshableGraph.super.getOneValueOf(nodes);
        }

        private <T> void createNode(int startFrom, NodeImpl<T> node) throws Exception {
            @SuppressWarnings("unchecked")
            T oldObject = (T) this.rootGraph.objects.get(node.index);
            for (var dependencyNode : node.createDependencies) {
                try {
                    var init = this.inits.get(toImpl(rootGraph.draw, dependencyNode).index);
                    if (init != null) {
                        init.get();
                    }
                } catch (ExecutionException _) {
                    throw new DependencyInitializationFailedException();
                }
            }
            for (var interceptorNode : node.interceptors) {
                var init = this.inits.get(toImpl(rootGraph.draw, interceptorNode).index);
                if (init != null) {
                    init.get();
                }
            }
            if (oldObject != null && !node.createDependencies.isEmpty() && node.index != startFrom) {
                var dependencyChanged = false;
                for (var dependency : node.refreshDependencies) {
                    if (rootGraph.get(dependency) != get(dependency)) { // ref equals is intended
                        dependencyChanged = true;
                        break;
                    }
                }
                for (var dependency : node.interceptors) {
                    if (rootGraph.get(dependency) != get(dependency)) { // ref equals is intended
                        dependencyChanged = true;
                        break;
                    }
                }
                if (!dependencyChanged) {
                    return;
                }
            }
            if (node.condition() != null) {
                switch (node.condition().apply(this)) {
                    case GraphCondition.ConditionResult.Matched _ -> {}
                    case GraphCondition.ConditionResult.Failed failed -> {
                        this.rootGraph.log.trace("Creating node {} canceled: {}", node.index, failed.reason());
                        this.tmpArray.set(node.index, new ConditionFailedGraphValue(failed));
                        return;
                    }
                }
            }


            if (this.rootGraph.log.isTraceEnabled()) {
                var dependenciesStr = node.createDependencies.stream().map(Node::toString).collect(Collectors.joining(",", "[", "]"));
                this.rootGraph.log.trace("Creating node {}, dependencies {}", node.index, dependenciesStr);
            }

            var newObject = Objects.requireNonNull(node.factory.get(this));
            if (Objects.equals(newObject, oldObject)) {
                if (newObject instanceof Lifecycle lifecycle) {
                    lifecycle.release();
                } else if (newObject instanceof Closeable closeable) {
                    closeable.close();
                }
                return;
            }
            synchronized (TmpGraph.this) {
                this.initialized.set(node.index);
            }
            this.tmpArray.set(node.index, newObject);
            if (newObject instanceof RefreshListener) {
                synchronized (this.rootGraph.refreshListenerNodes) {
                    this.rootGraph.refreshListenerNodes.add(node.index);
                }
            }
            this.rootGraph.log.trace("Created node {} {}", node.index, newObject.getClass());
            if (newObject instanceof Lifecycle lifecycle) {
                this.initializeNode(node, lifecycle);
            }
            for (var interceptorNode : node.interceptors) {
                var interceptor = (NodeImpl<? extends GraphInterceptor<T>>) interceptorNode;
                var interceptorObject = (GraphInterceptor<T>) this.get(interceptor);
                // todo handle somehow errors on that stage?
                this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {}", node.index, newObject.getClass(), interceptor.index, interceptorObject.getClass());
                try {
                    var intercepted = interceptorObject.init(newObject);
                    this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} complete", node.index, newObject.getClass(), interceptor.index, interceptorObject.getClass());
                    newObject = intercepted;
                } catch (RuntimeException | Error e) {
                    this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} error", node.index, newObject.getClass(), interceptor.index, interceptorObject.getClass(), e);
                    throw e;
                } catch (Throwable e) {
                    this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} error", node.index, newObject.getClass(), interceptor.index, interceptorObject.getClass(), e);
                    throw new IllegalStateException(e);
                }
            }
            this.tmpArray.set(node.index, newObject);
        }

        @Override
        public void refresh(Node<?> fromNode) {
            this.rootGraph.refresh(fromNode);
        }

        private static class DependencyInitializationFailedException extends RuntimeException {
            @Override
            public Throwable fillInStackTrace() {
                return this;
            }
        }

        private void initializeNode(NodeImpl<?> node, Lifecycle lifecycle) {
            var index = node.index;
            this.rootGraph.log.trace("Initializing node {} of class {} cancelled", index, lifecycle.getClass());
            try {
                lifecycle.init();
                this.rootGraph.log.trace("Node Initializing {} of class {} complete", index, lifecycle.getClass());
            } catch (CancellationException e) {
                this.rootGraph.log.trace("Node Initializing {} of class {} cancelled", index, lifecycle.getClass());
                throw e;
            } catch (CompletionException ce) {
                this.rootGraph.log.trace("Node Initializing {} of class {} error", index, lifecycle.getClass(), ce.getCause());
                throw ce;
            } catch (RuntimeException | Error e) {
                this.rootGraph.log.trace("Node Initializing {} of class {} error", index, lifecycle.getClass(), e);
                throw e;
            } catch (Throwable e) {
                this.rootGraph.log.trace("Initializing node {} of class {} error", index, lifecycle.getClass(), e);
                throw new IllegalStateException(e);
            }
        }

        private List<Throwable> init(int startFrom) {
            var nodes = this.rootGraph.draw.getNodes();
            for (int i = startFrom; i < nodes.size(); i++) {
                var node = (NodeImpl<?>) nodes.get(i);
                var future = new CompletableFuture<@Nullable Void>();
                Thread.ofVirtual().name("init-node-" + node.index).start(() -> {
                    var startTime = this.debugEnabled ? System.nanoTime() : 0L;
                    try {
                        this.createNode(startFrom, node);
                        if (this.debugEnabled) {
                            var took = System.nanoTime() - startTime;
                            if (took > SLOW_NODE_INIT_THRESHOLD * 1_000_000) {
                                this.rootGraph.log.debug("Initialized node {} at index {} in {}ms", node.type(), node.index, took / 1_000_000);
                            }
                        }
                        future.complete(null);
                    } catch (Throwable t) {
                        if (this.debugEnabled) {
                            var took = System.nanoTime() - startTime;
                            if (took > SLOW_NODE_INIT_THRESHOLD * 1_000_000) {
                                this.rootGraph.log.debug("Initialized node {} at index {} in {}ms", node.type(), node.index, took / 1_000_000);
                            }
                        }
                        future.completeExceptionally(t);
                    }
                });
                this.inits.set(node.index, future);
            }
            var errors = new ArrayList<Throwable>();
            for (var i = startFrom; i < TmpGraph.this.inits.length(); i++) {
                var init = TmpGraph.this.inits.get(i);
                try {
                    init.get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof DependencyInitializationFailedException || e.getCause().getCause() instanceof DependencyInitializationFailedException) {
                        continue;
                    }
                    errors.add(Objects.requireNonNull(e.getCause()));
                }
            }
            return errors;
        }
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

    private static long started() {
        return System.nanoTime();
    }

    private static String tookForLogging(long started) {
        return Duration.ofNanos(System.nanoTime() - started).truncatedTo(ChronoUnit.MILLIS).toString().substring(2).toLowerCase();
    }
}
