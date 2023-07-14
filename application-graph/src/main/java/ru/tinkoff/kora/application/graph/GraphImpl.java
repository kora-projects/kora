package ru.tinkoff.kora.application.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class GraphImpl implements RefreshableGraph, Lifecycle {
    private volatile AtomicReferenceArray<Object> objects;
    private final ApplicationGraphDraw draw;
    private final Logger log;
    private final Semaphore semaphore = new Semaphore(1);
    private final Set<Integer> refreshListenerNodes = new HashSet<>();

    GraphImpl(ApplicationGraphDraw draw) {
        this.draw = draw;
        this.log = LoggerFactory.getLogger(this.draw.getRoot());
        this.objects = new AtomicReferenceArray<>(this.draw.size());
    }

    @Override
    public ApplicationGraphDraw draw() {
        return this.draw;
    }

    @Override
    public <T> T get(Node<T> node) {
        if (node.graphDraw != this.draw) {
            throw new IllegalArgumentException("Node is from another graph");
        }
        @SuppressWarnings("unchecked")
        var value = (T) this.objects.get(node.index);
        if (value == null) {
            throw new IllegalStateException("Value was note initialized");
        }
        return value;
    }

    @Override
    public <T> ValueOf<T> valueOf(final Node<? extends T> node) {
        if (node.graphDraw != this.draw) {
            throw new IllegalArgumentException("Node is from another graph");
        }
        return new ValueOf<>() {
            @Override
            public T get() {
                return GraphImpl.this.get(node);
            }

            @Override
            public Mono<Void> refresh() {
                return GraphImpl.this.refresh(node);
            }
        };
    }

    @Override
    public <T> PromiseOf<T> promiseOf(final Node<T> node) {
        if (node.index >= 0 && node.graphDraw != this.draw) {
            throw new IllegalArgumentException("Node is from another graph");
        }
        return new PromiseOfImpl<>(this, node);
    }

    @Override
    public Mono<Void> refresh(Node<?> fromNode) {
        return Mono.defer(() -> {
            var root = new BitSet(this.objects.length());
            root.set(fromNode.index);
            this.semaphore.acquireUninterruptibly();

            log.debug("Refreshing Graph from node {} of class {}", fromNode.index, this.objects.get(fromNode.index).getClass());
            final long started = System.nanoTime();
            return this.initializeSubgraph(root)
                .doOnEach(s -> {
                    switch (s.getType()) {
                        case CANCEL -> {
                            this.semaphore.release();
                            log.debug("Refreshing Graph cancelled");
                        }
                        case ON_ERROR -> {
                            this.semaphore.release();
                            log.debug("Refreshing Graph error", s.getThrowable());
                        }
                        case ON_COMPLETE -> {
                            this.semaphore.release();
                            log.debug("Refreshing Graph completed in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                        }
                        default -> {}
                    }
                });
        });
    }

    @Override
    public Mono<Void> init() {
        return Mono.defer(() -> {
            var root = new BitSet(this.objects.length());
            root.set(0, this.objects.length());
            this.semaphore.acquireUninterruptibly();

            log.debug("Graph Initializing...");
            final long started = System.nanoTime();
            return this.initializeSubgraph(root)
                .doOnEach(s -> {
                    switch (s.getType()) {
                        case CANCEL -> {
                            this.semaphore.release();
                            log.debug("Graph Initializing cancelled");
                        }
                        case ON_ERROR -> {
                            this.semaphore.release();
                            log.debug("Graph Initializing error", s.getThrowable());
                        }
                        case ON_COMPLETE -> {
                            this.semaphore.release();
                            log.debug("Graph Initializing completed in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                        }
                        default -> {}
                    }
                });
        });
    }

    @Override
    public Mono<Void> release() {
        return Mono.defer(() -> {
            var root = new BitSet(this.objects.length());
            root.set(0, this.objects.length());
            this.semaphore.acquireUninterruptibly();
            log.debug("Graph Releasing...");
            final long started = System.nanoTime();
            return this.releaseNodes(this.objects, root)
                .doOnEach(s -> {
                    switch (s.getType()) {
                        case CANCEL -> {
                            this.semaphore.release();
                            log.debug("Graph Releasing cancelled");
                        }
                        case ON_ERROR -> {
                            this.semaphore.release();
                            log.debug("Graph Releasing error", s.getThrowable());
                        }
                        case ON_COMPLETE -> {
                            this.semaphore.release();
                            log.debug("Graph Releasing completed in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
                        }
                        default -> {}
                    }
                });
        });
    }

    private Mono<Void> initializeSubgraph(BitSet root) {
        log.trace("Materializing graph objects {}", root);
        var tmpGraph = new TmpGraph(this);
        return tmpGraph.init(root)
            .then(Mono.defer(() -> {
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
                    .onErrorResume(e -> {
                        this.log.warn("Error on releasing original objects after refresh", e);
                        return Mono.empty();
                    });
            }))
            .onErrorResume(e -> this.releaseNodes(tmpGraph.tmpArray, tmpGraph.initialized)
                .onErrorResume(e1 -> {
                    this.log.warn("Error on releasing temporary objects after init error", e1);
                    return Mono.empty();
                })
                .then(Mono.error(e)))
            .then();
    }

    private Mono<Void> releaseNodes(AtomicReferenceArray<Object> objects, BitSet root) {
        var release = new Mono<?>[objects.length()];
        for (int i = objects.length() - 1; i >= 0; i--) {
            if (!root.get(i)) {
                release[i] = Mono.empty();
                continue;
            }
            var node = this.draw.getNodes().get(i);
            release[i] = this.release(objects, release, node);
        }
        return Mono.whenDelayError(release);
    }

    private <T> Mono<Void> release(AtomicReferenceArray<Object> objects, Mono<?>[] releases, Node<T> node) {
        @SuppressWarnings("unchecked")
        var object = (T) objects.get(node.index);
        if (object == null) {
            return Mono.empty();
        }
        var dependentNodes = Stream.concat(node.getDependentNodes().stream(), node.getIntercepts().stream())
            .filter(n -> n.index >= 0)
            .map(n -> releases[n.index].onErrorResume(e -> Mono.empty()))
            .collect(Collectors.toList());
        var dependentReleases = Mono.when(dependentNodes);

        var intercept = Mono.just(object);
        var i = node.getInterceptors().listIterator(node.getInterceptors().size());
        while (i.hasPrevious()) {
            var interceptorNode = i.previous();
            @SuppressWarnings("unchecked")
            var interceptor = (GraphInterceptor<T>) objects.get(interceptorNode.index);
            intercept = intercept.flatMap(o -> interceptor.release(o)
                .switchIfEmpty(Mono.just(o))
                .doOnEach(s -> {
                    switch (s.getType()) {
                        case CANCEL ->
                            this.log.trace("Intercepting release node {} of class {} with node {} of class {} cancelled", node.index, o.getClass(), interceptorNode.index, interceptor.getClass());
                        case ON_SUBSCRIBE ->
                            this.log.trace("Intercepting release node {} of class {} with node {} of class {}", node.index, o.getClass(), interceptorNode.index, interceptor.getClass());
                        case ON_ERROR ->
                            this.log.trace("Intercepting release node {} of class {} with node {} of class {} error", node.index, o.getClass(), interceptorNode.index, interceptor.getClass(), s.getThrowable());
                        case ON_COMPLETE ->
                            this.log.trace("Intercepting release node {} of class {} with node {} of class {} complete", node.index, o.getClass(), interceptorNode.index, interceptor.getClass());
                        default -> {}
                    }
                })
            );
        }

        return dependentReleases.then(intercept)
            .filter(Lifecycle.class::isInstance)
            .cast(Lifecycle.class)
            .flatMap(lifecycle -> Mono.defer(() -> {
                log.trace("Releasing node {} of class {}", node.index, object.getClass());
                return lifecycle.release().then()
                    .doOnSuccess(v -> {
                        log.trace("Node {} of class {} released", node.index, object.getClass());
                    });
            }))
            .cache();
    }

    private static class TmpGraph implements Graph {
        private final GraphImpl rootGraph;
        private final AtomicReferenceArray<Object> tmpArray;
        private final Collection<TmpValueOf<?>> newValueOf = new ConcurrentLinkedDeque<>();
        private final Collection<PromiseOfImpl<?>> newPromises = new ConcurrentLinkedDeque<>();
        private final AtomicReferenceArray<Mono<Void>> inits;
        private final BitSet initialized;

        private TmpGraph(GraphImpl rootGraph) {
            this.rootGraph = rootGraph;
            this.tmpArray = new AtomicReferenceArray<>(this.rootGraph.objects.length());
            for (int i = 0; i < this.rootGraph.objects.length(); i++) {
                this.tmpArray.set(i, this.rootGraph.objects.get(i));
            }
            this.inits = new AtomicReferenceArray<>(this.tmpArray.length());
            this.initialized = new BitSet(this.tmpArray.length());
        }

        @Override
        public ApplicationGraphDraw draw() {
            return this.rootGraph.draw();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Node<T> node) {
            return (T) this.tmpArray.get(node.index);
        }

        @Override
        public <T> ValueOf<T> valueOf(Node<? extends T> node) {
            var value = new TmpValueOf<T>(node, this, this.rootGraph);
            this.newValueOf.add(value);
            return value;
        }

        @Override
        public <T> PromiseOf<T> promiseOf(Node<T> node) {
            var promise = new PromiseOfImpl<T>(null, node);
            this.newPromises.add(promise);
            return promise;
        }

        private <T> void createNode(Node<T> node, AtomicIntegerArray dependencies) {
            var dependenciesStr = node.getDependencyNodes().stream().map(n -> String.valueOf(n.index)).collect(Collectors.joining(",", "[", "]"));
            var create = Mono.<T>fromCallable(() -> {
                    if (dependencies.get(node.index) == 0) {
                        // dependencies were not updated so just return old object
                        @SuppressWarnings("unchecked")
                        var oldObject = (T) this.rootGraph.objects.get(node.index);
                        return oldObject;
                    }
                    this.rootGraph.log.trace("Creating node {}, dependencies {}", node.index, dependenciesStr);
                    return node.factory.get(this);
                })
                .flatMap(newObject -> {
                    var oldObject = this.rootGraph.objects.get(node.index);
                    if (Objects.equals(newObject, oldObject)) {
                        // we should notify dependent objects that dependency was not changed
                        for (var dependentNode : node.getDependentNodes()) {
                            dependencies.decrementAndGet(dependentNode.index);
                        }
                        for (var interceptedNode : node.getIntercepts()) {
                            dependencies.decrementAndGet(interceptedNode.index);
                        }
                        return Mono.empty();
                    }
                    if (newObject instanceof RefreshListener) {
                        synchronized (this.rootGraph.refreshListenerNodes) {
                            this.rootGraph.refreshListenerNodes.add(node.index);
                        }
                    }
                    this.rootGraph.log.trace("Created node {} {}", node.index, newObject.getClass());
                    var init = newObject instanceof Lifecycle lifecycle
                        ? this.initializeNode(node, lifecycle)
                        : Mono.<Void>empty();
                    var objectMono = init.thenReturn(newObject);
                    for (var interceptor : node.getInterceptors()) {
                        @SuppressWarnings("unchecked")
                        var interceptorObject = (GraphInterceptor<T>) this.tmpArray.get(interceptor.index);
                        // todo handle somehow errors on that stage
                        objectMono = objectMono.flatMap(o -> interceptorObject.init(o)
                            .switchIfEmpty(Mono.just(o))
                            .doOnEach(s -> {
                                switch (s.getType()) {
                                    case CANCEL ->
                                        this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} cancelled", node.index, o.getClass(), interceptor.index, interceptorObject.getClass());
                                    case ON_SUBSCRIBE ->
                                        this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {}", node.index, o.getClass(), interceptor.index, interceptorObject.getClass());
                                    case ON_ERROR ->
                                        this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} error", node.index, o.getClass(), interceptor.index, interceptorObject.getClass(), s.getThrowable());
                                    case ON_COMPLETE ->
                                        this.rootGraph.log.trace("Intercepting init node {} of class {} with node {} of class {} complete", node.index, o.getClass(), interceptor.index, interceptorObject.getClass());
                                    default -> {}
                                }
                            }));
                    }
                    return objectMono;
                })
                .doOnNext(o -> this.tmpArray.set(node.index, o))
                .then();
            var dependencyInitializationMonos = Stream.concat(node.getDependencyNodes().stream(), node.getInterceptors().stream())
                .filter(n -> n.index >= 0)
                .map(n -> this.inits.get(n.index))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            var dependencyInitialization = Mono.when(dependencyInitializationMonos)
                .onErrorMap(e -> new DependencyInitializationFailedException());
            this.inits.set(node.index, dependencyInitialization.then(create).cache());
        }

        private static class DependencyInitializationFailedException extends RuntimeException {
            @Override
            public Throwable fillInStackTrace() {
                return this;
            }
        }

        private Mono<Void> initializeNode(Node<?> node, Lifecycle lifecycle) {
            var index = node.index;
            return lifecycle.init().then()
                .doOnEach(s -> {
                    switch (s.getType()) {
                        case CANCEL -> this.rootGraph.log.trace("Node Initializing {} of class {} cancelled", index, lifecycle.getClass());
                        case ON_SUBSCRIBE -> this.rootGraph.log.trace("Node Initializing {} of class {}", index, lifecycle.getClass());
                        case ON_ERROR -> this.rootGraph.log.trace("Node Initializing {} of class {} error", index, lifecycle.getClass(), s.getThrowable());
                        case ON_COMPLETE -> {
                            synchronized (TmpGraph.this) {
                                this.initialized.set(node.index);
                            }
                            this.rootGraph.log.trace("Node Initializing {} of class {} complete", index, lifecycle.getClass());
                        }
                        default -> {}
                    }
                });
        }

        private Mono<Void> init(BitSet root) {
            var dependencies = new AtomicIntegerArray(this.tmpArray.length());
            var visitor = new Object() {
                public void apply(Node<?> node) {
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
                    var node = nodes.get(i);
                    visitor.apply(node);
                }
            }
            for (int i = 0; i < dependencies.length(); i++) {
                if (dependencies.getPlain(i) > 0) {
                    this.createNode(nodes.get(i), dependencies);
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
            var startingFromFinal = startingFrom;

            return Flux.fromIterable(new Iterable<Mono<Void>>() {
                    @Override
                    public Iterator<Mono<Void>> iterator() {
                        return new Iterator<>() {
                            private int i = startingFromFinal;

                            @Override
                            public boolean hasNext() {
                                return this.i < GraphImpl.TmpGraph.this.inits.length();
                            }

                            @Override
                            public Mono<Void> next() {
                                var init = GraphImpl.TmpGraph.this.inits.get(this.i);
                                this.i++;
                                while (this.i < GraphImpl.TmpGraph.this.inits.length()) {
                                    var nextInit = GraphImpl.TmpGraph.this.inits.get(this.i);
                                    if (nextInit == null) {
                                        this.i++;
                                    } else {
                                        break;
                                    }
                                }
                                return init;
                            }
                        };
                    }
                })
                .flatMapDelayError(Function.identity(), Queues.SMALL_BUFFER_SIZE, Queues.XS_BUFFER_SIZE)
                .onErrorContinue(DependencyInitializationFailedException.class, (e, o) -> {})
                .then();
        }
    }


    private static class TmpValueOf<T> implements ValueOf<T> {
        public volatile Graph tmpGraph;
        private final GraphImpl rootGraph;
        private final Node<? extends T> node;

        private TmpValueOf(Node<? extends T> node, Graph tmpGraph, GraphImpl rootGraph) {
            this.node = node;
            this.tmpGraph = tmpGraph;
            this.rootGraph = rootGraph;
        }

        @Override
        public T get() {
            return this.tmpGraph.get(this.node);
        }

        @Override
        public Mono<Void> refresh() {
            return this.rootGraph.refresh(this.node);
        }
    }
}
