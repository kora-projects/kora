package ru.tinkoff.kora.application.graph.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.*;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

public final class GraphImpl implements RefreshableGraph, Lifecycle {
    private static final long SLOW_NODE_INIT_THRESHOLD = Long.parseLong(System.getProperty("kora.graph.slowNodeInitThresholdMillis", "100"));
    private static final CompletableFuture<Void> EMPTY_FUTURE = CompletableFuture.completedFuture(null);

    private final Executor executor;
    private final ApplicationGraphDraw draw;
    private final Logger log;
    private final Semaphore semaphore = new Semaphore(1);
    private final Set<Integer> refreshListenerNodes = new HashSet<>();

    private volatile AtomicReferenceArray<Object> objects;

    public GraphImpl(ApplicationGraphDraw draw) {
        this.draw = draw;
        this.log = LoggerFactory.getLogger(this.draw.getRoot());
        this.objects = new AtomicReferenceArray<>(this.draw.size());
        var loomExecutor = VirtualThreadExecutorHolder.executor();
        this.executor = Objects.requireNonNullElse(loomExecutor, ForkJoinPool.commonPool());

        var loomLogger = LoggerFactory.getLogger(VirtualThreadExecutorHolder.class);
        var status = VirtualThreadExecutorHolder.status();
        if (status == VirtualThreadExecutorHolder.VirtualThreadStatus.ENABLED) {
            loomLogger.info("VirtualThreadExecutor enabled");
        } else if (status == VirtualThreadExecutorHolder.VirtualThreadStatus.DISABLED) {
            loomLogger.info("VirtualThreadExecutor disabled");
        } else {
            loomLogger.info("VirtualThreadExecutor unavailable");
        }
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

        log.debug("Dependency container refreshing from node {} of class {}...", fromNode.index, this.objects.get(fromNode.index).getClass());
        final long started = log.isDebugEnabled() ? started() : 0;
        try {
            this.initializeSubgraph(root).toCompletableFuture().join();
            if (log.isDebugEnabled()) {
                log.debug("Dependency container refreshed in {}", tookForLogging(started));
            }
        } catch (Throwable e) {
            if (e instanceof CancellationException) {
                log.debug("Dependency container refresh cancelled");
            } else {
                log.debug("Dependency container refresh error", e);
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

        log.debug("Dependency container initializing...");
        final long started = started();
        var f = this.initializeSubgraph(root).whenComplete((unused, throwable) -> {
            this.semaphore.release();
            if (throwable == null) {
                log.debug("Dependency container initialized in {}", tookForLogging(started));
                return;
            }
            if (throwable instanceof CancellationException) {
                log.debug("Dependency container initialization cancelled");
            } else if (throwable instanceof CompletionException ce) {
                log.debug("Dependency container initialization failed", ce.getCause());
            } else {
                log.debug("Dependency container initialization failed", throwable);
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
        log.debug("Dependency container releasing...");
        final long started = started();
        var f = this.releaseNodes(this.objects, root).whenComplete((unused, throwable) -> {
            this.semaphore.release();
            if (throwable == null) {
                log.debug("Dependency container released in {}", tookForLogging(started));
                return;
            }
            if (throwable instanceof CancellationException) {
                log.debug("Dependency container releasing cancelled");
            } else {
                log.debug("Dependency container releasing failed", throwable);
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
                log.trace("Dependency container refreshed, ");
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
                release[i] = EMPTY_FUTURE;
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
            return EMPTY_FUTURE;
        }
        var dependentNodes = new CompletableFuture<?>[node.getDependentNodes().size() + node.getIntercepts().size()];
        for (int i = 0; i < node.getDependentNodes().size(); i++) {
            var n = node.getDependentNodes().get(i);
            if (n.index >= 0) {
                dependentNodes[i] = Objects.requireNonNullElse(releases[n.index], EMPTY_FUTURE).exceptionally(e -> null);
            } else {
                dependentNodes[i] = EMPTY_FUTURE;
            }
        }
        for (int i = 0; i < node.getIntercepts().size(); i++) {
            var interceptor = node.getIntercepts().get(i);
            if (interceptor.index >= 0) {
                dependentNodes[node.getDependentNodes().size() + i] = Objects.requireNonNullElse(releases[interceptor.index], EMPTY_FUTURE).exceptionally(e -> null);
            } else {
                dependentNodes[node.getDependentNodes().size() + i] = EMPTY_FUTURE;
            }
        }
        var dependentReleases = CompletableFuture.allOf(dependentNodes);

        var intercept = dependentReleases.thenApply(v -> object);
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
        return finalIntercept
            .thenComposeAsync(v -> {
                if (v instanceof Lifecycle lifecycle) {
                    log.trace("Releasing node {} of class {}", node.index, object.getClass());
                    try {
                        lifecycle.release();
                    } catch (Error | Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    log.trace("Node {} of class {} released", node.index, object.getClass());
                } else if (v instanceof AutoCloseable closeable) {
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
        private final boolean debugEnabled;

        private TmpGraph(GraphImpl rootGraph) {
            this.rootGraph = rootGraph;
            this.tmpArray = new AtomicReferenceArray<>(this.rootGraph.objects.length());
            for (int i = 0; i < this.rootGraph.objects.length(); i++) {
                this.tmpArray.set(i, this.rootGraph.objects.get(i));
            }
            this.inits = new AtomicReferenceArray<>(this.tmpArray.length());
            this.initialized = new BitSet(this.tmpArray.length());
            this.executor = rootGraph.executor;
            this.debugEnabled = this.rootGraph.log.isDebugEnabled();
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
            @SuppressWarnings("unchecked")
            var oldObject = (T) this.rootGraph.objects.get(node.index);
            var nodeDependencies = dependencies.get(node.index);
            if (nodeDependencies == 0) {
                // dependencies were not updated so we keep old object
                for (var dependentNode : node.getDependentNodes()) {
                    var r = dependencies.decrementAndGet(dependentNode.index);
                    if (r < 0) {
                        throw new IllegalStateException();
                    }
                }
                for (var interceptedNode : node.getIntercepts()) {
                    var r = dependencies.decrementAndGet(interceptedNode.index);
                    if (r < 0) {
                        throw new IllegalStateException();
                    }
                }
                this.inits.set(node.index, EMPTY_FUTURE);
                this.tmpArray.set(node.index, oldObject);
                return;
            }
            if (nodeDependencies < 0) {
                this.inits.set(node.index, EMPTY_FUTURE);
                this.tmpArray.set(node.index, oldObject);
                return;
            }
            Callable<T> create = () -> {
                if (dependencies.get(node.index) == 0) {
                    // dependencies were not updated so we keep old object
                    for (var dependentNode : node.getDependentNodes()) {
                        dependencies.decrementAndGet(dependentNode.index);
                    }
                    for (var interceptedNode : node.getIntercepts()) {
                        dependencies.decrementAndGet(interceptedNode.index);
                    }
                    this.inits.set(node.index, EMPTY_FUTURE);
                    this.tmpArray.set(node.index, oldObject);
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
                    : EMPTY_FUTURE;

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
                    dependencyInitializationFutures[i] = Objects.requireNonNullElse(this.inits.get(dependency.index), EMPTY_FUTURE);
                } else {
                    dependencyInitializationFutures[i] = EMPTY_FUTURE;
                }
            }
            for (int i = 0; i < node.getInterceptors().size(); i++) {
                var dependency = node.getInterceptors().get(i);
                if (dependency.index >= 0) {
                    dependencyInitializationFutures[node.getDependencyNodes().size() + i] = Objects.requireNonNullElse(this.inits.get(dependency.index), EMPTY_FUTURE);
                } else {
                    dependencyInitializationFutures[node.getDependencyNodes().size() + i] = EMPTY_FUTURE;
                }
            }
            var dependencyInitialization = CompletableFuture.allOf(dependencyInitializationFutures)
                .exceptionallyCompose(e -> CompletableFuture.failedFuture(new DependencyInitializationFailedException()));
            this.inits.set(node.index, dependencyInitialization.thenAcceptAsync(v -> {
                var startTime = this.debugEnabled ? System.nanoTime() : 0L;
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
                } finally {
                    if (this.debugEnabled) {
                        var took = System.nanoTime() - startTime;
                        if (took > SLOW_NODE_INIT_THRESHOLD * 1_000_000) {
                            this.rootGraph.log.debug("Initialized node {} at index {} in {}ms", node.type(), node.index, took / 1_000_000);
                        }
                    }
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
                } else if (!visitor.processed.get(i)) {
                    dependencies.set(i, -1);
                }
            }
            for (int i = 0; i < dependencies.length(); i++) {
                var node = (NodeImpl<?>) nodes.get(i);
                this.createNode(node, dependencies);
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
                        return EMPTY_FUTURE;
                    } else if (error instanceof CompletionException ce) {
                        if (ce.getCause() instanceof DependencyInitializationFailedException) {
                            return EMPTY_FUTURE;
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

    private static long started() {
        return System.nanoTime();
    }

    private static String tookForLogging(long started) {
        return Duration.ofNanos(System.nanoTime() - started).truncatedTo(ChronoUnit.MILLIS).toString().substring(2).toLowerCase();
    }
}
