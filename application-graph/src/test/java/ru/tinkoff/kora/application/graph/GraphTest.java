package ru.tinkoff.kora.application.graph;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphTest {
    private static final Class<?>[] TAGS = new Class[0];

    static {
        if (LoggerFactory.getLogger(ReferenceGraph.class) instanceof Logger log) {
            log.setLevel(Level.DEBUG);
        }
    }

    @Test
    void objectsCreated() {
        var graph = ReferenceGraph.graph();

        assertThat(graph.root()).isNotNull();
        assertThat(graph.object1()).isNotNull();
        assertThat(graph.object2()).isNotNull();
        assertThat(graph.object3()).isNotNull();
        assertThat(graph.object4()).isNotNull();
        assertThat(graph.object5()).isNotNull();
    }

    @Test
    void initOrderIsCorrect() {
        var graph = ReferenceGraph.graph();

        graph.root()
            .verifyInitialized();

        graph.object1()
            .verifyInitialized()
            .verifyInitializedAfter(graph.root());

        graph.object2()
            .verifyInitialized()
            .verifyInitializedAfter(graph.root());

        graph.object3()
            .verifyInitialized()
            .verifyInitializedAfter(graph.object1());

        graph.object4()
            .verifyInitialized()
            .verifyInitializedAfter(graph.object1())
            .verifyInitializedAfter(graph.object2());

        graph.object5()
            .verifyInitialized()
            .verifyInitializedAfter(graph.object2());
    }

    @Test
    void refreshRefreshesObject() {
        var graph = ReferenceGraph.graph();
        var oldValue = graph.object5();

        graph.refresh(graph.node5());
        var newValue = graph.object5();

        assertThat(newValue).isNotSameAs(oldValue);
        newValue.verifyInitialized();
        oldValue.verifyReleased();
        graph.factory5().verifyCount(2);
    }

    @Test
    void refreshRefreshesDependentObject() {
        var graph = ReferenceGraph.graph();
        var oldObject3 = graph.object3();
        var oldObject4 = graph.object4();

        graph.refresh(graph.node1());
        var newObject3 = graph.object3();
        var newObject4 = graph.object4();

        assertThat(newObject3).isNotSameAs(oldObject3);
        newObject3.verifyInitialized();
        oldObject3.verifyReleased();
        graph.factory3().verifyCount(2);

        assertThat(newObject4).isNotSameAs(oldObject4);
        newObject4.verifyInitialized();
        oldObject4.verifyReleased();
        graph.factory4().verifyCount(2);
    }

    @Test
    void refreshWithSameObjectIsNoop() {
        var graph = ReferenceGraph.graph();
        var oldObject3 = graph.object3();
        var oldObject4 = graph.object4();

        graph.factory1().nextIsSame();
        graph.refresh(graph.node1());
        var newObject3 = graph.object3();
        var newObject4 = graph.object4();

        assertThat(newObject3).isSameAs(oldObject3);
        graph.factory3().verifyCount(1);

        assertThat(newObject4).isSameAs(oldObject4);
        graph.factory4().verifyCount(1);
    }

    @Test
    void refreshDoesntAffectDependentObjectWithValueOf() {
        var graph = ReferenceGraph.graph();
        var oldObject4 = graph.object4();

        graph.refresh(graph.node2());
        var newObject4 = graph.object4();

        assertThat(newObject4).isSameAs(oldObject4);
        graph.factory4().verifyCount(1);
    }

    @Test
    void refreshWithInitExceptionThrowsException() {
        var graph = ReferenceGraph.graph();

        graph.object5Factory.nextIsInitError();

        assertThatThrownBy(() -> graph.refresh(graph.node2()));
    }

    @Test
    void refreshWithInitExceptionReleasesCreatedObjects() {
        var graph = ReferenceGraph.graph();

        graph.object5Factory.nextIsInitError();

        try {
            graph.refresh(graph.node2());
        } catch (Exception e) {}

        graph.factory5().lastCreated().verifyInitialized();
        graph.factory2().lastCreated().verifyInitialized();
        graph.factory2().lastCreated().verifyReleased();
    }

    @Test
    void refreshWithInitExceptionDoesntAffectGraph() {
        var graph = ReferenceGraph.graph();
        var oldRoot = graph.root();
        var oldObject1 = graph.object1();
        var oldObject2 = graph.object2();
        var oldObject3 = graph.object3();
        var oldObject4 = graph.object4();
        var oldObject5 = graph.object5();

        graph.object5Factory.nextIsInitError();

        try {
            graph.refresh(graph.node2());
        } catch (Exception e) {}

        graph.factory2().verifyCount(2);
        graph.factory5().verifyCount(2);

        graph.root().verifyNotReleased();
        graph.object1().verifyNotReleased();
        graph.object2().verifyNotReleased();
        graph.object3().verifyNotReleased();
        graph.object4().verifyNotReleased();
        graph.object5().verifyNotReleased();

        assertThat(oldRoot).isSameAs(graph.root());
        assertThat(oldObject1).isSameAs(graph.object1());
        assertThat(oldObject2).isSameAs(graph.object2());
        assertThat(oldObject3).isSameAs(graph.object3());
        assertThat(oldObject4).isSameAs(graph.object4());
        assertThat(oldObject5).isSameAs(graph.object5());
    }

    @Test
    void releaseOrderIsCorrect() {
        var graph = ReferenceGraph.graph();
        graph.release();

        graph.object5()
            .verifyReleased();
        graph.object4()
            .verifyReleased();
        graph.object3()
            .verifyReleased();
        graph.object2()
            .verifyReleased()
            .verifyReleasedAfter(graph.object4())
            .verifyReleasedAfter(graph.object5());
        graph.object1()
            .verifyReleased()
            .verifyReleasedAfter(graph.object3())
            .verifyReleasedAfter(graph.object4());
        graph.root()
            .verifyReleased()
            .verifyReleasedAfter(graph.object1())
            .verifyReleasedAfter(graph.object2());
    }

    @Test
    void releaseWithReleaseExceptionThrowsException() {
        var graph = ReferenceGraph.graph();

        graph.factory5().nextIsReleaseError();
        graph.refresh(graph.node5());

        assertThatThrownBy(graph::release);
    }

    @Test
    void valueOfAlwaysPointsOnTheCurrentObject() {
        var graph = ReferenceGraph.graph();

        var value1 = graph.graph.valueOf(graph.node1());
        var currentObject = graph.object1();
        assertThat(value1.get()).isSameAs(currentObject);

        graph.refresh(graph.node1());
        currentObject = graph.object1();
        assertThat(value1.get()).isSameAs(currentObject);

        graph.refresh(graph.node1());
        currentObject = graph.object1();
        assertThat(value1.get()).isSameAs(currentObject);

        graph.refresh(graph.node1());
        currentObject = graph.object1();
        assertThat(value1.get()).isSameAs(currentObject);
    }

    @Test
    void valueOfRefreshLeadsToRefresh() {
        var graph = ReferenceGraph.graph();

        var value1 = graph.graph.valueOf(graph.node1());
        var oldObject1 = graph.object1();

        value1.refresh().block();

        assertThat(oldObject1).isNotSameAs(graph.object1());
    }

    @Test
    void interceptorCalledAfterInitAndBeforeDependentObjectCreated() {
        var graph = ReferenceGraph.graph();

        graph.object2().verifyInitialized();
        graph.object2().verifyInitializedAfter(graph.interceptor1());
        graph.object2().verifyInitIntercepted();
        assertThat(graph.object5().initTime).isGreaterThan(graph.object2().interceptInitTime);
    }

    @Test
    void interceptorCalledBeforeReleaseAndAfterDependentObjectReleased() {
        var graph = ReferenceGraph.graph();
        graph.release();

        graph.object2().verifyReleased();
        graph.interceptor1().verifyReleasedAfter(graph.object2());
        graph.object2().verifyReleaseIntercepted();
        assertThat(graph.object5().releaseTime).isLessThan(graph.object2().interceptReleaseTime);
    }

    @Test
    void interceptorRefreshRefreshesInterceptedNodes() {
        var graph = ReferenceGraph.graph();

        var oldObject2 = graph.object2();

        graph.refresh(graph.interceptor1Node());
        var newObject2 = graph.object2();

        assertThat(newObject2).isNotSameAs(oldObject2);
        newObject2.verifyInitialized();
        newObject2.verifyInitIntercepted();
        oldObject2.verifyReleased();
        oldObject2.verifyReleaseIntercepted();
        graph.factory2().verifyCount(2);
        graph.interceptor1Factory().verifyCount(2);
    }

    @Test
    void interceptorInitErrorLeadsToInitError() {
        var graph = ReferenceGraph.graph();
        graph.interceptor1Factory.nextIsInterceptInitError();

        assertThatThrownBy(() -> graph.refresh(graph.interceptor1Node()));
    }

    @Test
    @Disabled("we should fix this")
    void interceptorInitErrorLeadsToReleaseWithoutInterceptor() {
        var graph = ReferenceGraph.graph();
        graph.interceptor1Factory.nextIsInterceptInitError();

        try {
            graph.refresh(graph.interceptor1Node());
        } catch (Exception ignore) {}

        graph.object2().verifyReleaseNotIntercepted();
    }

    @Test
    void interceptorReleaseErrorLeadsToReleaseError() {
        var graph = ReferenceGraph.graph();
        graph.interceptor1Factory.nextIsInterceptReleaseError();

        graph.refresh(graph.interceptor1Node());

        assertThatThrownBy(graph::release);
    }

    @Test
    void initWithErrorReleasesGraph() {
        var g = ReferenceGraph.graph();
        var draw = g.draw;
        g.object5Factory.nextIsInitError();

        assertThatThrownBy(() -> draw.init().block())
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("mock");

    }

    @Test
    void graphRefreshCallsRefreshListeners() {
        var graph = ReferenceGraph.graph();

        var object2 = graph.object2();
        var beforeRefresh = System.nanoTime();

        graph.graph.valueOf(graph.node4()).refresh().block();

        assertThat(object2.refreshTime).isGreaterThan(beforeRefresh);
    }


    /**
     * <pre>
     * {@code
     *                ________ root _________
     *               |                      |
     *           _object1_              _object2_   <------ interceptor1
     *          /         \            /         \
     *     [direct]    [direct]   [valueOf]    [direct]
     *       /              \       /              \
     * _object3_          _object4_            _object5_
     * }
     * </pre>
     */
    private static class ReferenceGraph {
        private final ApplicationGraphDraw draw = new ApplicationGraphDraw(ReferenceGraph.class);
        private final TestObjectFactory rootFactory = factory();
        private final TestObjectFactory interceptor1Factory = factory();
        private final TestObjectFactory object1Factory = factory();
        private final TestObjectFactory object2Factory = factory();
        private final TestObjectFactory object3Factory = factory();
        private final TestObjectFactory object5Factory = factory();
        private final Node<TestObject> rootNode = draw.addNode0(TAGS, rootFactory);
        private final Node<TestObject> interceptor1 = draw.addNode0(TAGS, interceptor1Factory);
        private final Node<TestObject> object1Node = draw.addNode0(TAGS, object1Factory, rootNode);
        private final Node<TestObject> object2Node = draw.addNode0(TAGS, object2Factory, List.of(interceptor1), rootNode);
        private final Node<TestObject> object3Node = draw.addNode0(TAGS, object3Factory, object1Node);
        private final TestObjectFactory object4Factory = factory(object1Node, object2Node.valueOf());


        private final Node<TestObject> object4Node = draw.addNode0(TAGS, object4Factory, object1Node, object2Node.valueOf());
        private final Node<TestObject> object5Node = draw.addNode0(TAGS, object5Factory, object2Node);
        private final RefreshableGraph graph = this.draw.init().block();

        public static ReferenceGraph graph() {
            return new ReferenceGraph();
        }

        public void refresh(Node<?> node) {
            this.graph.refresh(node).block();
        }

        public void release() {
            this.graph.release().block();
        }

        public TestObject root() {
            return this.graph.get(rootNode);
        }

        public Node<TestObject> rootNode() {
            return this.rootNode;
        }

        public TestObjectFactory rootFactory() {
            return this.rootFactory;
        }

        public TestObject object1() {
            return this.graph.get(object1Node);
        }

        public TestObjectFactory factory1() {
            return this.object1Factory;
        }

        public Node<TestObject> node1() {
            return this.object1Node;
        }

        public TestObject object2() {
            return this.graph.get(object2Node);
        }

        public TestObjectFactory factory2() {
            return this.object2Factory;
        }

        public Node<TestObject> node2() {
            return this.object2Node;
        }

        public TestObject object3() {
            return this.graph.get(object3Node);
        }

        public TestObjectFactory factory3() {
            return this.object3Factory;
        }

        public Node<TestObject> node3() {
            return this.object3Node;
        }

        public TestObject object4() {
            return this.graph.get(object4Node);
        }

        public TestObjectFactory factory4() {
            return this.object4Factory;
        }


        public Node<TestObject> node4() {
            return this.object4Node;
        }

        public TestObject object5() {
            return this.graph.get(object5Node);
        }

        public TestObjectFactory factory5() {
            return this.object5Factory;
        }


        public Node<TestObject> node5() {
            return this.object5Node;
        }

        public TestObjectFactory interceptor1Factory() {
            return this.interceptor1Factory;
        }

        public Node<TestObject> interceptor1Node() {
            return this.interceptor1;
        }

        public TestObject interceptor1() {
            return this.graph.get(this.interceptor1);
        }
    }


    private static class TestObject implements Lifecycle, GraphInterceptor<TestObject>, RefreshListener {
        private final TestObjectFactory.Type type;
        private final List<Object> dependencies;

        private volatile long initTime = -1;
        private volatile long releaseTime = -1;
        private volatile long interceptInitTime = -1;
        private volatile long interceptReleaseTime = -1;
        private volatile long refreshTime = -1;

        private TestObject(TestObjectFactory.Type type, List<Object> dependencies) {
            this.type = type;
            this.dependencies = dependencies;
        }

        @Override
        public Mono<Void> init() {
            return Mono.<Void>fromRunnable(() -> {
                this.initTime = System.nanoTime();
                for (var dependency : dependencies) {
                    if (dependency instanceof ValueOf<?> valueOf) {
                        assert valueOf.get() != null;
                    }
                }
                if (this.type == TestObjectFactory.Type.INIT_ERROR) {
                    throw new RuntimeException("mock");
                }
            }).subscribeOn(Schedulers.parallel());
        }

        @Override
        public Mono<Void> release() {
            return Mono.<Void>fromRunnable(() -> {
                for (var dependency : dependencies) {
                    if (dependency instanceof ValueOf<?> valueOf) {
                        assert valueOf.get() != null;
                    }
                }
                this.releaseTime = System.nanoTime();
                if (this.type == TestObjectFactory.Type.RELEASE_ERROR) {
                    throw new RuntimeException();
                }
            }).subscribeOn(Schedulers.parallel());
        }


        @Override
        public Mono<TestObject> init(TestObject value) {
            return Mono.fromCallable(() -> {
                value.interceptInitTime = System.nanoTime();
                if (this.type == TestObjectFactory.Type.INTERCEPT_INIT_ERROR) {
                    throw new RuntimeException();
                }
                return value;
            }).subscribeOn(Schedulers.parallel());
        }

        @Override
        public Mono<TestObject> release(TestObject value) {
            return Mono.fromCallable(() -> {
                value.interceptReleaseTime = System.nanoTime();
                if (this.type == TestObjectFactory.Type.INTERCEPT_RELEASE_ERROR) {
                    throw new RuntimeException();
                }
                return value;
            }).subscribeOn(Schedulers.parallel());
        }

        @Override
        public void graphRefreshed() {
            this.refreshTime = System.nanoTime();
        }

        public TestObject verifyInitialized() {
            assertThat(this.initTime).isGreaterThan(0);
            return this;
        }

        public TestObject verifyReleased() {
            assertThat(this.releaseTime).isGreaterThan(0);
            return this;
        }

        public TestObject verifyNotReleased() {
            assertThat(this.releaseTime).isEqualTo(-1);
            return this;
        }

        public TestObject verifyInitializedAfter(TestObject other) {
            assertThat(this.initTime).isGreaterThan(other.initTime);
            return this;
        }

        public TestObject verifyReleasedAfter(TestObject other) {
            assertThat(this.releaseTime).isGreaterThan(other.releaseTime);
            return this;
        }

        public void verifyInitIntercepted() {
            assertThat(this.interceptInitTime).isGreaterThan(0);
        }

        public void verifyReleaseIntercepted() {
            assertThat(this.interceptReleaseTime).isGreaterThan(0);
        }

        public void verifyReleaseNotIntercepted() {
            assertThat(this.interceptReleaseTime).isLessThan(0);
        }

    }

    private static class TestObjectFactory implements Graph.Factory<TestObject> {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final AtomicReference<Type> type = new AtomicReference<>(Type.SIMPLE);
        private final ConcurrentLinkedDeque<TestObject> objects = new ConcurrentLinkedDeque<>();
        private final List<Node<TestObject>> dependencies;

        private TestObjectFactory(Node<TestObject>... dependencies) {
            this.dependencies = List.of(dependencies);
        }

        private enum Type {
            SIMPLE, INIT_ERROR, RELEASE_ERROR, INTERCEPT_INIT_ERROR, INTERCEPT_RELEASE_ERROR, SAME_VALUE
        }

        @Override
        public TestObject get(Graph graph) {
            if (this.type.compareAndSet(Type.SAME_VALUE, Type.SIMPLE)) {
                return this.objects.peekLast();
            }

            this.counter.incrementAndGet();
            var dependencies = this.dependencies.stream()
                .map(dep -> {
                    if (dep.isValueOf()) {
                        return graph.valueOf(dep);
                    } else {
                        return graph.get(dep);
                    }
                })
                .toList();
            var object = new TestObject(this.type.get(), dependencies);
            this.objects.offer(object);
            return object;
        }

        public void verifyCount(int count) {
            assertThat(counter.get()).isEqualTo(count);
        }

        public void nextIsSame() {
            this.type.set(Type.SAME_VALUE);
        }

        public void nextIsInitError() {
            this.type.set(Type.INIT_ERROR);
        }

        public void nextIsInterceptInitError() {
            this.type.set(Type.INTERCEPT_INIT_ERROR);
        }

        public void nextIsReleaseError() {
            this.type.set(Type.RELEASE_ERROR);
        }

        public void nextIsInterceptReleaseError() {
            this.type.set(Type.INTERCEPT_RELEASE_ERROR);
        }

        public TestObject lastCreated() {
            return this.objects.peekLast();
        }
    }

    @SafeVarargs
    private static TestObjectFactory factory(Node<TestObject>... dependencies) {
        return new TestObjectFactory(dependencies);
    }
}
