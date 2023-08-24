package ru.tinkoff.kora.test.extension.junit5;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Graph;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.common.Tag;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class KoraJUnit5Extension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KoraJUnit5Extension.class);

    private static final Logger logger = LoggerFactory.getLogger(KoraJUnit5Extension.class);

    // Application class -> graph supplier
    private static final Map<Class<?>, Supplier<ApplicationGraphDraw>> GRAPH_SUPPLIER_MAP = new ConcurrentHashMap<>();

    static class KoraTestContext {

        volatile TestGraph graph;
        volatile TestClassMetadata metadata;
        final KoraAppTest annotation;
        final TestInstance.Lifecycle lifecycle;

        KoraTestContext(KoraAppTest annotation, TestInstance.Lifecycle lifecycle) {
            this.annotation = annotation;
            this.lifecycle = lifecycle;
        }
    }

    enum InitMode {
        CONSTRUCTOR,
        METHOD
    }

    record TestMethodMetadata(TestClassMetadata classMetadata,
                              Set<GraphCandidate> parameterComponents,
                              Set<GraphMock> parameterMocks) {}

    record TestClassMetadata(KoraAppTest annotation,
                             TestInstance.Lifecycle lifecycle,
                             InitMode initMode,
                             Config config,
                             Set<GraphCandidate> annotationComponents,
                             List<Field> fieldsForInjection,
                             Set<GraphCandidate> fieldComponents,
                             Set<GraphMock> fieldMocks,
                             Set<GraphCandidate> constructorComponents,
                             Set<GraphMock> constructorMocks) {

        interface Config {

            Config NONE = new Config() {
                @Override
                public void setup(ApplicationGraphDraw graphDraw) {
                    // do nothing
                }

                @Override
                public void cleanup() {
                    // do nothing
                }
            };

            void setup(ApplicationGraphDraw graphDraw) throws IOException;

            void cleanup();
        }

        static class FileConfig implements Config {

            private final KoraConfigModification config;
            private final Map<String, String> systemProperties;

            private Properties prevProperties;

            public FileConfig(KoraConfigModification config) {
                this.config = config;
                this.systemProperties = config.systemProperties();
            }

            @Override
            public void setup(ApplicationGraphDraw graphDraw) throws IOException {
                prevProperties = (Properties) System.getProperties().clone();

                if (config instanceof KoraConfigFile kf) {
                    System.setProperty("config.resource", kf.configFile());
                } else if (config instanceof KoraConfigString ks) {
                    final String configFileName = "kora-app-test-config-" + UUID.randomUUID();
                    logger.trace("Preparing config setup with file name: {}", configFileName);
                    var tmpFile = Files.createTempFile(configFileName, ".txt");
                    Files.writeString(tmpFile, ks.config(), StandardCharsets.UTF_8);
                    var configPath = tmpFile.toAbsolutePath().toString();
                    System.setProperty("config.file", configPath);
                }

                if (!systemProperties.isEmpty()) {
                    systemProperties.forEach(System::setProperty);
                }
            }

            @Override
            public void cleanup() {
                if (prevProperties != null) {
                    logger.trace("Cleaning up after config setup");
                    System.setProperties(prevProperties);
                    prevProperties = null;
                }
            }
        }
    }

    @Nonnull
    private static KoraTestContext getKoraTestContext(ExtensionContext context) {
        var storage = context.getStore(NAMESPACE);
        var koraTestContext = storage.get(KoraAppTest.class, KoraTestContext.class);
        if (koraTestContext == null) {
            final KoraAppTest koraAppTest = findKoraAppTest(context)
                .orElseThrow(() -> new ExtensionConfigurationException("@KoraAppTest not found"));

            var lifecycle = context.getTestInstanceLifecycle().orElse(TestInstance.Lifecycle.PER_METHOD);
            koraTestContext = new KoraTestContext(koraAppTest, lifecycle);
            storage.put(KoraAppTest.class, koraTestContext);
        }

        return koraTestContext;
    }

    private static void prepareMocks(TestGraphInitialized graphInitialized) {
        logger.trace("Resetting mocks...");
        for (Node<?> node : graphInitialized.graphDraw().getNodes()) {
            var mockCandidate = graphInitialized.refreshableGraph().get(node);
            if (MockUtil.isMock(mockCandidate) || MockUtil.isSpy(mockCandidate)) {
                Mockito.reset(mockCandidate);
                if (mockCandidate instanceof Lifecycle lifecycle) {
                    Mockito.when(lifecycle.init()).thenReturn(Mono.empty());
                    Mockito.when(lifecycle.release()).thenReturn(Mono.empty());
                }
            }
        }
    }

    private static void injectComponentsToFields(TestClassMetadata metadata, TestGraphInitialized graphInitialized, ExtensionContext context) {
        if (metadata.fieldsForInjection.isEmpty()) {
            return;
        }

        var testInstance = context.getTestInstance().orElseThrow(() -> new ExtensionConfigurationException("@KoraAppTest can't get TestInstance for @TestComponent field injection"));
        for (var field : metadata.fieldsForInjection) {
            final Class<?>[] tags = parseTags(field);
            final GraphCandidate candidate = new GraphCandidate(field.getGenericType(), tags);
            logger.trace("Looking for test method '{}' field '{}' inject candidate: {}",
                context.getDisplayName(), field.getName(), candidate);

            final Object component = getComponentFromGraph(graphInitialized, candidate);
            injectToField(testInstance, field, component);
        }
    }

    private static void injectToField(Object testInstance, Field field, Object value) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new ExtensionConfigurationException("Field '%s' annotated have illegal 'static' modifier".formatted(field.getName()));
        }

        if (Modifier.isFinal(field.getModifiers())) {
            throw new ExtensionConfigurationException("Field '%s' annotated have illegal 'final' modifier".formatted(field.getName()));
        }

        try {
            field.setAccessible(true);
            field.set(testInstance, value);
        } catch (Exception e) {
            throw new ExtensionConfigurationException("Failed to inject field '%s' due to: ".formatted(field.getName()) + e);
        }
    }

    public static KoraTestContext getInitializedKoraTestContext(InitMode initMode, ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        if (koraTestContext.metadata == null) {
            koraTestContext.metadata = getClassMetadata(koraTestContext, initMode, context);
        }

        if (koraTestContext.graph == null) {
            koraTestContext.graph = KoraJUnit5Extension.generateTestGraph(koraTestContext.metadata, context);
            koraTestContext.graph.initialize();
        }

        return koraTestContext;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        getKoraTestContext(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        var started = System.nanoTime();
        var koraTestContext = getInitializedKoraTestContext(InitMode.METHOD, context);
        prepareMocks(koraTestContext.graph.initialized());
        injectComponentsToFields(koraTestContext.metadata, koraTestContext.graph.initialized(), context);
        logger.info("@KoraAppTest test method '{}' setup took: {}", context.getDisplayName(), Duration.ofNanos(System.nanoTime() - started));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_METHOD) {
            if (koraTestContext.graph != null) {
                koraTestContext.graph.close();
                koraTestContext.graph = null;
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            if (koraTestContext.graph != null) {
                koraTestContext.graph.close();
            }
        }
    }

    private static Optional<KoraAppTest> findKoraAppTest(ExtensionContext context) {
        Optional<ExtensionContext> current = Optional.of(context);
        while (current.isPresent()) {
            var requiredTestClass = current.get().getRequiredTestClass();
            while (!requiredTestClass.equals(Object.class)) {
                final Optional<KoraAppTest> annotation = AnnotationSupport.findAnnotation(requiredTestClass, KoraAppTest.class);
                if (annotation.isPresent()) {
                    return annotation;
                }

                requiredTestClass = requiredTestClass.getSuperclass();
            }

            current = current.get().getParent();
        }

        return Optional.empty();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) throws ParameterResolutionException {
        return Arrays.stream(parameterContext.getParameter().getDeclaredAnnotations())
                   .anyMatch(a -> a.annotationType().equals(TestComponent.class) || a.annotationType().equals(MockComponent.class))
               || parameterContext.getParameter().getType().equals(KoraAppGraph.class)
               || parameterContext.getParameter().getType().equals(Graph.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) throws ParameterResolutionException {
        var koraTestContext = getInitializedKoraTestContext(InitMode.CONSTRUCTOR, context);
        var graphCandidate = getGraphCandidate(parameterContext);
        logger.trace("Looking for test method '{}' parameter '{}' inject candidate: {}",
            context.getDisplayName(), parameterContext.getParameter().getName(), graphCandidate);

        return getComponentFromGraph(koraTestContext.graph.initialized(), graphCandidate);
    }

    private static Optional<KoraGraphModification> getGraphModification(ApplicationGraphDraw graphDraw,
                                                                        TestMethodMetadata metadata,
                                                                        ExtensionContext context) {
        final long started = System.nanoTime();
        var mockComponentFromParameters = metadata.parameterMocks();
        var mockComponentFromFields = metadata.classMetadata().fieldMocks();

        var mocks = new HashSet<>(mockComponentFromParameters);
        mocks.addAll(mockComponentFromFields);

        final KoraGraphModification koraGraphModification = context.getTestInstance()
            .filter(inst -> inst instanceof KoraAppTestGraphModifier)
            .map(inst -> ((KoraAppTestGraphModifier) inst).graph())
            .map(graph -> {
                mocks.forEach(m -> graph.mockComponent(m.candidate().type(), m.candidate().tags()));
                return graph;
            })
            .orElseGet(() -> {
                if (mocks.isEmpty()) {
                    return null;
                } else {
                    var graph = KoraGraphModification.create();
                    mocks.forEach(m -> graph.mockComponent(m.candidate().type(), m.candidate().tags()));
                    return graph;
                }
            });

        logger.debug("@KoraAppTest graph modification collecting took: {}", Duration.ofNanos(System.nanoTime() - started));
        return Optional.ofNullable(koraGraphModification);
    }

    private static TestMethodMetadata getMethodMetadata(TestClassMetadata classMetadata, ExtensionContext context) {
        var parameterMocks = context.getTestMethod()
            .filter(method -> !method.isSynthetic())
            .stream()
            .flatMap(m -> Stream.of(m.getParameters()))
            .filter(parameter -> parameter.getDeclaredAnnotation(MockComponent.class) != null)
            .map(parameter -> {
                if (KoraAppGraph.class.isAssignableFrom(parameter.getType())) {
                    throw new ExtensionConfigurationException("KoraAppGraph can't be target of @MockComponent");
                }

                final Class<?>[] tag = parseTags(parameter);
                return new GraphMock(new GraphCandidate(parameter.getParameterizedType(), tag));
            })
            .collect(Collectors.toSet());

        if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_CLASS && !parameterMocks.isEmpty()) {
            throw new ExtensionConfigurationException("@KoraAppTest when run in 'TestInstance.Lifecycle.PER_CLASS' test can't inject @MockComponent in method parameters");
        }

        final Set<GraphCandidate> parameterComponents = new HashSet<>();
        if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_METHOD) {
            if (classMetadata.initMode == InitMode.METHOD) {
                for (var parameter : context.getRequiredTestMethod().getParameters()) {
                    if (parameter.isAnnotationPresent(TestComponent.class)) {
                        var tag = parseTags(parameter);
                        var type = parameter.getParameterizedType();
                        parameterComponents.add(new GraphCandidate(type, tag));
                    }
                }
            } else {
                for (var method : context.getRequiredTestClass().getDeclaredMethods()) {
                    for (var parameter : method.getParameters()) {
                        if (parameter.isAnnotationPresent(TestComponent.class) || parameter.isAnnotationPresent(MockComponent.class)) {
                            throw new ExtensionConfigurationException("@TestComponent or @MockComponent can't be applied to method arguments when constructor injection is used, please use field injection");
                        }
                    }
                }
            }
        } else if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            for (var method : context.getRequiredTestClass().getDeclaredMethods()) {
                for (var parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(TestComponent.class)) {
                        var tag = parseTags(parameter);
                        var type = parameter.getParameterizedType();
                        parameterComponents.add(new GraphCandidate(type, tag));
                    }
                }
            }
        }

        return new TestMethodMetadata(classMetadata, parameterComponents, parameterMocks);
    }

    private static TestClassMetadata getClassMetadata(KoraTestContext koraAppTest, InitMode initMode, ExtensionContext context) {
        final long started = System.nanoTime();
        final Set<GraphCandidate> annotationCandidates = Arrays.stream(koraAppTest.annotation.components())
            .map(GraphCandidate::new)
            .collect(Collectors.toSet());

        final TestClassMetadata.Config koraAppConfig = context.getTestInstance()
            .filter(inst -> inst instanceof KoraAppTestConfigModifier)
            .map(inst -> {
                final KoraConfigModification configModification = ((KoraAppTestConfigModifier) inst).config();
                return ((TestClassMetadata.Config) new TestClassMetadata.FileConfig(configModification));
            })
            .orElse(TestClassMetadata.Config.NONE);

        var testClass = context.getTestClass()
            .orElseThrow(() -> new ExtensionConfigurationException("@KoraAppTest can't get TestInstance for @TestComponent field injection"));

        final List<Field> fieldsForInjection = ReflectionUtils.findFields(testClass,
            f -> !f.isSynthetic() && (f.getAnnotation(TestComponent.class) != null || f.getAnnotation(MockComponent.class) != null),
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);

        final Set<GraphCandidate> fieldComponents = fieldsForInjection.stream()
            .filter(f -> f.getAnnotation(TestComponent.class) != null)
            .map(field -> {
                final Class<?>[] tags = parseTags(field);
                return new GraphCandidate(field.getGenericType(), tags);
            })
            .collect(Collectors.toSet());

        final Set<GraphMock> fieldMocks = fieldsForInjection.stream()
            .filter(f -> f.getAnnotation(MockComponent.class) != null)
            .map(field -> {
                final Class<?>[] tags = parseTags(field);
                return new GraphMock(new GraphCandidate(field.getGenericType(), tags));
            })
            .collect(Collectors.toSet());

        final Set<GraphCandidate> constructorComponents = new HashSet<>();
        final Set<GraphMock> constructorMocks = new HashSet<>();
        if (initMode == InitMode.CONSTRUCTOR) {
            var constructor = context.getRequiredTestClass().getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            for (Parameter parameter : constructor.getParameters()) {
                if (parameter.isAnnotationPresent(TestComponent.class)) {
                    var tag = parseTags(parameter);
                    var type = parameter.getParameterizedType();
                    constructorComponents.add(new GraphCandidate(type, tag));
                } else if (parameter.isAnnotationPresent(MockComponent.class)) {
                    var tag = parseTags(parameter);
                    var type = parameter.getParameterizedType();
                    constructorMocks.add(new GraphMock(new GraphCandidate(type, tag)));
                }
            }
        }

        logger.debug("@KoraAppTest class metadata collecting took: {}", Duration.ofNanos(System.nanoTime() - started));
        return new TestClassMetadata(koraAppTest.annotation, koraAppTest.lifecycle, initMode, koraAppConfig, annotationCandidates, fieldsForInjection, fieldComponents, fieldMocks, constructorComponents, constructorMocks);
    }

    private static GraphCandidate getGraphCandidate(ParameterContext parameterContext) {
        final Type parameterType = parameterContext.getParameter().getParameterizedType();
        final Class<?>[] tags = parseTags(parameterContext.getParameter());
        return new GraphCandidate(parameterType, tags);
    }

    private static Class<?>[] parseTags(AnnotatedElement object) {
        return Arrays.stream(object.getDeclaredAnnotations())
            .filter(a -> a.annotationType().equals(Tag.class))
            .map(a -> ((Tag) a).value())
            .findFirst()
            .orElse(null);
    }

    private static Object getComponentFromGraph(TestGraphInitialized graph, GraphCandidate candidate) {
        if (KoraAppGraph.class.equals(candidate.type())) {
            return graph.koraAppGraph();
        }
        if (Graph.class.equals(candidate.type())) {
            return graph.refreshableGraph();
        }
        var nodes = graph.graphDraw().findNodesByType(candidate.type(), candidate.tagsAsArray());
        if (nodes.size() == 1) {
            return graph.refreshableGraph().get(nodes.get(0));
        }
        if (nodes.size() > 1) {
            throw new ExtensionConfigurationException(candidate + " expected to have one suitable component, got " + nodes.size());
        }
        if (candidate.type() instanceof Class<?> clazz) {
            var objects = new ArrayList<Object>();
            for (var node : graph.graphDraw().getNodes()) {
                var object = graph.refreshableGraph().get(node);
                if (clazz.isInstance(object)) {
                    if (candidate.tags().isEmpty() && node.tags().length == 0) {
                        objects.add(object);
                    } else if (candidate.tags().size() == 1 && candidate.tags().get(0).getCanonicalName().equals("ru.tinkoff.kora.common.Tag.Any")) {
                        objects.add(object);
                    } else if (Arrays.equals(candidate.tagsAsArray(), node.tags())) {
                        objects.add(object);
                    }
                }
            }
            if (objects.size() == 1) {
                return objects.get(0);
            }
            if (objects.size() > 1) {
                throw new ExtensionConfigurationException(candidate + " expected to have one suitable component, got " + objects.size());
            }
        }
        throw new ExtensionConfigurationException(candidate + " was not found in graph, please check @KoraAppTest configuration");
    }

    private static Set<GraphCandidate> scanGraphRoots(TestMethodMetadata metadata, ExtensionContext context) {
        final Set<GraphCandidate> roots = new HashSet<>(metadata.classMetadata.annotationComponents);
        roots.addAll(metadata.classMetadata.fieldComponents);
        roots.addAll(metadata.parameterComponents);
        roots.addAll(metadata.classMetadata.constructorComponents);
        return roots;
    }

    @SuppressWarnings("unchecked")
    private static TestGraph generateTestGraph(TestClassMetadata classMetadata, ExtensionContext context) {
        var applicationClass = classMetadata.annotation.value();
        var graphSupplier = GRAPH_SUPPLIER_MAP.computeIfAbsent(applicationClass, k -> {
            try {
                final long startedLoading = System.nanoTime();
                var clazz = KoraJUnit5Extension.class.getClassLoader().loadClass(applicationClass.getName() + "Graph");
                var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
                var supplier = (Supplier<ApplicationGraphDraw>) constructors[0].newInstance();
                logger.debug("@KoraAppTest loading took: {}", Duration.ofNanos(System.nanoTime() - startedLoading));
                return supplier;
            } catch (ClassNotFoundException e) {
                throw new ExtensionConfigurationException("@KoraAppTest#value must be annotated with @KoraApp, but probably wasn't: " + applicationClass, e);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        var startedSubgraph = System.nanoTime();
        var methodMetadata = getMethodMetadata(classMetadata, context);
        var graphDraw = graphSupplier.get().copy();

        var roots = scanGraphRoots(methodMetadata, context);
        var nodesForSubGraph = roots.stream()
            .flatMap(component -> GraphUtils.findNodeByTypeOrAssignable(graphDraw, component).stream())
            .collect(Collectors.toSet());

        var mocks = new ArrayList<Node<?>>();
        for (var fieldMock : classMetadata.fieldMocks) {
            var mockCandidates = GraphUtils.findNodeByTypeOrAssignable(graphDraw, fieldMock.candidate());
            mocks.addAll(mockCandidates);
        }
        for (var parameterMock : methodMetadata.parameterMocks) {
            var mockCandidates = GraphUtils.findNodeByTypeOrAssignable(graphDraw, parameterMock.candidate());
            mocks.addAll(mockCandidates);
        }
        for (var constructorMock : methodMetadata.classMetadata.constructorMocks) {
            var mockCandidates = GraphUtils.findNodeByTypeOrAssignable(graphDraw, constructorMock.candidate());
            mocks.addAll(mockCandidates);
        }

        final ApplicationGraphDraw subGraph;
        if (nodesForSubGraph.isEmpty()) {
            if (mocks.isEmpty()) {
                subGraph = graphDraw;
            } else {
                subGraph = graphDraw.subgraph(mocks, graphDraw.getNodes());
            }
        } else {
            subGraph = graphDraw.subgraph(mocks, nodesForSubGraph);
        }

        getGraphModification(graphDraw, methodMetadata, context).ifPresent(koraGraphModification -> {
            for (GraphModification modification : koraGraphModification.getModifications()) {
                modification.accept(subGraph);
            }
        });

        logger.debug("@KoraAppTest subgraph took: {}", Duration.ofNanos(System.nanoTime() - startedSubgraph));
        return new TestGraph(subGraph, classMetadata);
    }
}
