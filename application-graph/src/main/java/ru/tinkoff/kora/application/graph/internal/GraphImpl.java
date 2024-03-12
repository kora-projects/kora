package ru.tinkoff.kora.application.graph.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.*;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

public final class GraphImpl implements RefreshableGraph, Lifecycle {
    private static final CompletableFuture<Void> empty = CompletableFuture.completedFuture(null);
    private final Executor executor;
    private volatile AtomicReferenceArray<Object> objects;
    private final ApplicationGraphDraw draw;
    private final Logger log;
    private final Semaphore semaphore = new Semaphore(1);
    private final Set<Integer> refreshListenerNodes = new HashSet<>();

    public GraphImpl(ApplicationGraphDraw draw) {
        this.draw = draw;
        this.log = LoggerFactory.getLogger(this.draw.getRoot());
        this.objects = new AtomicReferenceArray<>(this.draw.size());
        this.executor = Objects.requireNonNullElse(VirtualThreadExecutorHolder.executor, ForkJoinPool.commonPool());
    }

    @Override
    public ApplicationGraphDraw draw() {
        return this.draw;
    }

    @Override
    public <T> T get(Node<T> node) {
        var casted = (NodeImpl<T>) node;
        if (casted.graphDraw != this.draw) {
            throw new IllegalArgumentException("Node is from another graph");
        }
        @SuppressWarnings("unchecked")
        var value = (T) this.objects.get(casted.index);
        if (value == null) {
            throw new IllegalStateException("Value was note initialized");
        }
        return value;
    }

    @Override
    public <T> ValueOf<T> valueOf(final Node<? extends T> node) {
        var casted = (NodeImpl<? extends T>) node;
        if (casted.graphDraw != this.draw) {
            throw new IllegalArgumentException("Node is from another graph");
        }
        return new ValueOf<>() {
            @Override
            public T get() {
                return GraphImpl.this.get(node);
            }

            @Override
            public void refresh() {
                GraphImpl.this.refresh(casted);
            }
        };
    }

    @Override
    public <T> PromiseOf<T> promiseOf(final Node<T> node) {
        var casted = (NodeImpl<T>) node;
        if (casted.index >= 0 && casted.graphDraw != this.draw) {
            throw new IllegalArgumentException("Node is from another graph");
        }
        return new PromiseOfImpl<>(this, casted);
    }

    @Override
    public void refresh(Node<?> fromNodeRaw) {
        var fromNode = (NodeImpl<?>) fromNodeRaw;
        var root = new BitSet(this.objects.length());
        root.set(fromNode.index);
        this.semaphore.acquireUninterruptibly();

        log.debug("Refreshing Graph from node {} of class {}", fromNode.index, this.objects.get(fromNode.index).getClass());
        final long started = System.nanoTime();
        try {
            this.initializeSubgraph(root).toCompletableFuture().join();
            log.debug("Refreshing Graph completed in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        } catch (Throwable e) {
            if (e instanceof CancellationException) {
                log.debug("Refreshing Graph cancelled");
            } else {
                log.debug("Refreshing Graph error", e);
            }
            if (e instanceof CompletionException ce) {
                if (ce.getCause() instanceof RuntimeException re) {
                    throw re;
                }
                if (ce.getCause() instanceof Error re) {
                    throw re;
                }
                throw ce;
            } else {
                throw e;
            }
        } finally {
            this.semaphore.release();
        }
    }

    @Override
    public void init() {
        var root = new BitSet(this.objects.length());
        root.set(0, this.objects.length());
        this.semaphore.acquireUninterruptibly();

        log.debug("Graph Initializing...");
        final long started = System.nanoTime();
        var f = this.initializeSubgraph(root).whenComplete((unused, throwable) -> {
            this.semaphore.release();
            if (throwable == null) {
                log.debug("Graph Initializing completed in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                return;
            }
            if (throwable instanceof CancellationException) {
                log.debug("Graph Initializing cancelled");
            } else if (throwable instanceof CompletionException ce) {
                log.debug("Graph Initializing error", ce.getCause());
            } else {
                log.debug("Graph Initializing error", throwable);
            }
        });
        try {
            f.toCompletableFuture().join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            if (e.getCause() instanceof Error re) {
                throw re;
            }
            throw e;
        }
    }

    @Override
    public void release() {
        var root = new BitSet(this.objects.length());
        root.set(0, this.objects.length());
        this.semaphore.acquireUninterruptibly();
        log.debug("Graph Releasing...");
        final long started = System.nanoTime();
        var f = this.releaseNodes(this.objects, root).whenComplete((unused, throwable) -> {
            this.semaphore.release();
            if (throwable == null) {
                log.debug("Graph Releasing completed in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                return;
            }
            if (throwable instanceof CancellationException) {
                log.debug("Graph Releasing cancelled");
            } else {
                log.debug("Graph Releasing error", throwable);
            }
        });
        try {
            f.toCompletableFuture().join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            if (e.getCause() instanceof Error re) {
                throw re;
            }
            throw e;
        }
    }

    private CompletionStage<Void> initializeSubgraph(BitSet root) {
        log.trace("Materializing graph objects {}", root);
        var tmpGraph = new TmpGraph(this);
        return tmpGraph.init(root).thenCompose((unused) -> {
                var oldObjects = this.objects;
                this.objects = tmpGraph.tmpArray;
                for (var newValue : tmpGraph.newValueOf) {
                    newValue.tmpGraph = GraphImpl.this;
                }
                for (var newPromise : tmpGraph.newPromises) {
                    newPromise.graph = GraphImpl.this;
                }
                log.trace("Graph refreshed, calling interceptors...");
                for (var refreshListenerNode : this.refreshListenerNodes) {
                    if (this.objects.get(refreshListenerNode) instanceof RefreshListener refreshListener) {
                        try {
                            refreshListener.graphRefreshed();
                        } catch (Exception e) {
                            log.warn("Exception caught when calling listener.graphRefreshed(), object={}", refreshListener);
                        }
                    }
                }
                log.trace("Graph refreshed, ");
                return this.releaseNodes(oldObjects, tmpGraph.initialized)
                    .exceptionally(e -> {
                        this.log.warn("Error on releasing original objects after refresh", e);
                        return null;
                    });
            })
            .exceptionallyCompose(e -> this.releaseNodes(tmpGraph.tmpArray, tmpGraph.initialized)
                .exceptionallyCompose(e1 -> {
                    this.log.warn("Error on releasing temporary objects after init error", e1);
                    e.addSuppressed(e1);
                    return CompletableFuture.failedFuture(e);
                })
                .thenCompose(v -> CompletableFuture.failedFuture(e)));
    }

    private CompletionStage<Void> releaseNodes(AtomicReferenceArray<Object> objects, BitSet root) {
        var release = new CompletableFuture<?>[objects.length()];
        for (int i = objects.length() - 1; i >= 0; i--) {
            if (!root.get(i)) {
                release[i] = empty;
                continue;
            }
            var node = (NodeImpl<?>) this.draw.getNodes().get(i);
            release[i] = this.release(objects, release, node);
        }
        return CompletableFuture.allOf(release);
    }

    private <T> CompletableFuture<Void> release(AtomicReferenceArray<Object> objects, CompletableFuture<?>[] releases, NodeImpl<T> node) {
        @SuppressWarnings("unchecked")
        var object = (T) objects.get(node.index);
        if (object == null) {
            return empty;
        }
        var dependentNodes = new CompletableFuture<?>[node.getDependentNodes().size() + node.getIntercepts().size()];
        for (int i = 0; i < node.getDependentNodes().size(); i++) {
            var n = node.getDependentNodes().get(i);
            if (n.index >= 0) {
                dependentNodes[i] = Objects.requireNonNullElse(releases[n.index], empty).exceptionally(e -> null);
            } else {
                dependentNodes[i] = empty;
            }
        }
        for (int i = 0; i < node.getIntercepts().size(); i++) {
            var interceptor = node.getIntercepts().get(i);
            if (interceptor.index >= 0) {
                dependentNodes[node.getDependentNodes().size() + i] = Objects.requireNonNullElse(releases[interceptor.index], empty).exceptionally(e -> null);
            } else {
                dependentNodes[node.getDependentNodes().size() + i] = empty;
            }
        }
        var dependentReleases = CompletableFuture.allOf(dependentNodes);

        var intercept = CompletableFuture.completedFuture(object);
        var i = node.getInterceptors().listIterator(node.getInterceptors().size());
        while (i.hasPrevious()) {
            var interceptorNode = i.previous();
            @SuppressWarnings("unchecked")
            var interceptor = (GraphInterceptor<T>) objects.get(interceptorNode.index);
            intercept = intercept.thenApplyAsync(o -> {
                this.log.trace("Intercepting release node {} of class {} with node {} of class {}", node.index, o.getClass(), interceptorNode.index, interceptor.getClass());
                try {
                    var intercepted = interceptor.release(o);
                    log.trace("Intercepting release node {} of class {} with node {} of class {} complete", node.index, o.getClass(), interceptorNode.index, interceptor.getClass());
                    return intercepted;
                } catch (RuntimeException | Error e) {
                    this.log.trace("Intercepting release node {} of class {} with node {} of class {} error", node.index, o.getClass(), interceptorNode.index, interceptor.getClass(), e);
                    throw e;
                } catch (Throwable e) {
                    this.log.trace("Intercepting release node {} of class {} with node {} of class {} error", node.index, o.getClass(), interceptorNode.index, interceptor.getClass(), e);
                    throw new IllegalStateException(e);
                }
            }, this.executor);
        }

        var finalIntercept = intercept;
        return dependentReleases
            .thenCompose(v -> finalIntercept)
            .thenComposeAsync(v -> {
                if (v instanceof Lifecycle lifecycle) {
                    log.trace("Releasing node {} of class {}", node.index, object.getClass());
                    try {
                        lifecycle.release();
                    } catch (Error | Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    log.trace("Node {} of class {} released", node.index, object.getClass());
                } else if(v instanceof AutoCloseable closeable) {
                    log.trace("Releasing node {} of class {}", node.index, object.getClass());
                    try {
                        closeable.close();
                    } catch (Error | Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    log.trace("Node {} of class {} released", node.index, object.getClass());
                }

                return CompletableFuture.completedFuture(null);
            }, this.executor);
    }

    private static class TmpGraph implements Graph {
        private final GraphImpl rootGraph;
        private final AtomicReferenceArray<Object> tmpArray;
        private final Collection<TmpValueOf<?>> newValueOf = new ConcurrentLinkedDeque<>();
        private final Collection<PromiseOfImpl<?>> newPromises = new ConcurrentLinkedDeque<>();
        private final AtomicReferenceArray<CompletableFuture<Void>> inits;
        private final BitSet initialized;
        private final Executor executor;

        private TmpGraph(GraphImpl rootGraph) {
            this.rootGraph = rootGraph;
            this.tmpArray = new AtomicReferenceArray<>(this.rootGraph.objects.length());
            for (int i = 0; i < this.rootGraph.objects.length(); i++) {
                this.tmpArray.set(i, this.rootGraph.objects.get(i));
            }
            this.inits = new AtomicReferenceArray<>(this.tmpArray.length());
            this.initialized = new BitSet(this.tmpArray.length());
            this.executor = rootGraph.executor;
        }

        @Override
        public ApplicationGraphDraw draw() {
            return this.rootGraph.draw();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Node<T> node) {
            var casted = (NodeImpl<T>) node;
            return (T) this.tmpArray.get(casted.index);
        }

        @Override
        public <T> ValueOf<T> valueOf(Node<? extends T> node) {
            var casted = (NodeImpl<? extends T>) node;
            // dirty hack to make copied graph work with valueOf
            @SuppressWarnings("unchecked")
            var fixed = (NodeImpl<? extends T>) this.rootGraph.draw.getNodes().get(casted.index);
            var value = new TmpValueOf<T>(fixed, this, this.rootGraph);
            this.newValueOf.add(value);
            return value;
        }

        @Override
        public <T> PromiseOf<T> promiseOf(Node<T> node) {
            var casted = (NodeImpl<T>) node;
            // dirty hack to make copied graph work with valueOf
            @SuppressWarnings("unchecked")
            var fixed = (NodeImpl<T>) this.rootGraph.draw.getNodes().get(casted.index);
            var promise = new PromiseOfImpl<T>(null, fixed);
            this.newPromises.add(promise);
            return promise;
        }

        private <T> void createNode(NodeImpl<T> node, AtomicIntegerArray dependencies) {
            Callable<T> create = () -> {
                @SuppressWarnings("unchecked")
                var oldObject = (T) this.rootGraph.objects.get(node.index);
                if (dependencies.get(node.index) == 0) {
                    // dependencies were not updated so we keep old object
                    for (var dependentNode : node.getDependentNodes()) {
                        dependencies.decrementAndGet(dependentNode.index);
                    }
                    for (var interceptedNode : node.getIntercepts()) {
                        dependencies.decrementAndGet(interceptedNode.index);
                    }
                    this.inits.set(node.index, empty);
                    return oldObject;
                }
                if (this.rootGraph.log.isTraceEnabled()) {
                    var dependenciesStr = node.getDependencyNodes().stream().map(n -> String.valueOf(n.index)).collect(Collectors.joining(",", "[", "]"));
                    this.rootGraph.log.trace("Creating node {}, dependencies {}", node.index, dependenciesStr);
                }
                var newObject = node.factory.get(this);
                if (Objects.equals(newObject, oldObject)) {
                    // we should notify dependent objects that dependency was not changed
                    for (var dependentNode : node.getDependentNodes()) {
                        dependencies.decrementAndGet(dependentNode.index);
                    }
                    for (var interceptedNode : node.getIntercepts()) {
                        dependencies.decrementAndGet(interceptedNode.index);
                    }
                    return null;
                }
                if (newObject instanceof RefreshListener) {
                    synchronized (this.rootGraph.refreshListenerNodes) {
                        this.rootGraph.refreshListenerNodes.add(node.index);
                    }
                }
                this.rootGraph.log.trace("Created node {} {}", node.index, newObject.getClass());
                var init = newObject instanceof Lifecycle lifecycle
                    ? this.initializeNode(node, lifecycle)
                    : empty;

                var objectFuture = init.thenApply(v -> newObject);
                for (var interceptor : node.getInterceptors()) {
                    @SuppressWarnings("unchecked")
                    var interceptorObject = (GraphInterceptor<T>) this.tmpArray.get(interceptor.index);
                    // todo handle somehow errors on that stage
                    objectFuture = objectFuture.thenApplyAsync(o -> {
                        this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {}", node.index, o.getClass(), interceptor.index, interceptorObject.getClass());
                        try {
                            var intercepted = interceptorObject.init(o);
                            this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} complete", node.index, o.getClass(), interceptor.index, interceptorObject.getClass());
                            return intercepted;
                        } catch (RuntimeException | Error e) {
                            this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} error", node.index, o.getClass(), interceptor.index, interceptorObject.getClass(), e);
                            throw e;
                        } catch (Throwable e) {
                            this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} error", node.index, o.getClass(), interceptor.index, interceptorObject.getClass(), e);
                            throw new IllegalStateException(e);
                        }
                    }, this.executor);
                }
                var result = objectFuture.join();
                this.tmpArray.set(node.index, result);
                return result;
            };
            var dependencyInitializationFutures = new CompletableFuture<?>[node.getDependencyNodes().size() + node.getInterceptors().size()];
            for (int i = 0; i < node.getDependencyNodes().size(); i++) {
                var dependency = node.getDependencyNodes().get(i);
                if (dependency.index >= 0) {
                    dependencyInitializationFutures[i] = Objects.requireNonNullElse(this.inits.get(dependency.index), empty);
                } else {
                    dependencyInitializationFutures[i] = empty;
                }
            }
            for (int i = 0; i < node.getInterceptors().size(); i++) {
                var dependency = node.getInterceptors().get(i);
                if (dependency.index >= 0) {
                    dependencyInitializationFutures[node.getDependencyNodes().size() + i] = Objects.requireNonNullElse(this.inits.get(dependency.index), empty);
                } else {
                    dependencyInitializationFutures[node.getDependencyNodes().size() + i] = empty;
                }
            }
            var dependencyInitialization = CompletableFuture.allOf(dependencyInitializationFutures)
                .exceptionallyCompose(e -> CompletableFuture.failedFuture(new DependencyInitializationFailedException()));
            this.inits.set(node.index, dependencyInitialization.thenAcceptAsync(v -> {
                try {
                    create.call();
                } catch (CompletionException e) {
                    if (e.getCause() instanceof RuntimeException re) {
                        throw re;
                    }
                    if (e.getCause() instanceof Error re) {
                        throw re;
                    }
                    throw e;
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            }, this.executor));
        }

        private static class DependencyInitializationFailedException extends RuntimeException {
            @Override
            public Throwable fillInStackTrace() {
                return this;
            }
        }

        private CompletableFuture<Void> initializeNode(NodeImpl<?> node, Lifecycle lifecycle) {
            var index = node.index;
            this.rootGraph.log.trace("Initializing node {} of class {} cancelled", index, lifecycle.getClass());
            return CompletableFuture.runAsync(() -> {
                try {
                    lifecycle.init();
                    synchronized (TmpGraph.this) {
                        this.initialized.set(node.index);
                    }
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
            }, this.executor);
        }

        private CompletionStage<Void> init(BitSet root) {
            var dependencies = new AtomicIntegerArray(this.tmpArray.length());
            var visitor = new Object() {
                private final BitSet processed = new BitSet(tmpArray.length());

                public void apply(NodeImpl<?> node) {
                    if (processed.get(node.index)) {
                        return;
                    }
                    processed.set(node.index);
                    for (var dependentNode : node.getDependentNodes()) {
                        if (!dependentNode.isValueOf()) {
                            dependencies.incrementAndGet(dependentNode.index);
                            this.apply(dependentNode);
                        }
                    }
                    for (var interceptedNode : node.getIntercepts()) {
                        dependencies.incrementAndGet(interceptedNode.index);
                        this.apply(interceptedNode);
                    }
                }
            };
            var nodes = this.rootGraph.draw.getNodes();
            for (int i = 0; i < this.tmpArray.length(); i++) {
                if (root.get(i)) {
                    dependencies.incrementAndGet(i);
                    var node = (NodeImpl<?>) nodes.get(i);
                    visitor.apply(node);
                }
            }
            for (int i = 0; i < dependencies.length(); i++) {
                if (dependencies.getPlain(i) > 0) {
                    var node = (NodeImpl<?>) nodes.get(i);
                    this.createNode(node, dependencies);
                }
            }
            var startingFrom = Integer.MAX_VALUE;
            for (int i = 0; i < TmpGraph.this.inits.length(); i++) {
                var init = GraphImpl.TmpGraph.this.inits.get(i);
                if (init != null) {
                    startingFrom = i;
                    break;
                }
            }
            var inits = new ArrayList<CompletableFuture<Void>>();

            for (var i = startingFrom; i < GraphImpl.TmpGraph.this.inits.length(); i++) {
                var init = GraphImpl.TmpGraph.this.inits.get(i);
                if (init == null) {
                    continue;
                }
                inits.add(init.exceptionallyCompose(error -> {
                    if (error instanceof DependencyInitializationFailedException) {
                        return empty;
                    } else if (error instanceof CompletionException ce) {
                        if (ce.getCause() instanceof DependencyInitializationFailedException) {
                            return empty;
                        }
                        return CompletableFuture.failedFuture(ce.getCause());
                    } else {
                        return CompletableFuture.failedFuture(error);
                    }
                }));
            }
            return CompletableFuture.allOf(inits.toArray((CompletableFuture[]::new)));
        }
    }


    private static class TmpValueOf<T> implements ValueOf<T> {
        public volatile Graph tmpGraph;
        private final GraphImpl rootGraph;
        private final NodeImpl<? extends T> node;

        private TmpValueOf(NodeImpl<? extends T> node, Graph tmpGraph, GraphImpl rootGraph) {
            this.node = node;
            this.tmpGraph = tmpGraph;
            this.rootGraph = rootGraph;
        }

        @Override
        public T get() {
            return this.tmpGraph.get(this.node);
        }

        @Override
        public void refresh() {
            this.rootGraph.refresh(this.node);
        }
    }
}
