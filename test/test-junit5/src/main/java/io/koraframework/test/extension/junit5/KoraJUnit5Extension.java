package io.koraframework.test.extension.junit5;

import io.koraframework.application.graph.*;
import io.koraframework.application.graph.internal.GraphImpl;
import io.koraframework.common.annotation.Tag;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.test.extension.junit5.mockito.MockitoStrictness;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class KoraJUnit5Extension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KoraJUnit5Extension.class);
    private static final Logger logger = LoggerFactory.getLogger(KoraJUnit5Extension.class);

    // Application class -> graph supplier
    private static final Map<Class<?>, Supplier<ApplicationGraphDraw>> GRAPH_SUPPLIER_MAP = new ConcurrentHashMap<>();

    private static final ExtensionContext.Namespace MOCKITO = ExtensionContext.Namespace.create("org.mockito.kora");

    static class KoraTestContext {

        @Nullable
        volatile TestGraph graph;
        @Nullable
        volatile TestClassMetadata metadata;
        final KoraAppTest annotation;
        final TestInstance.Lifecycle lifecycle;

        KoraTestContext(KoraAppTest annotation, TestInstance.Lifecycle lifecycle) {
            this.annotation = annotation;
            this.lifecycle = lifecycle;
        }
    }

    enum InitializeOrigin {
        CONSTRUCTOR,
        METHOD
    }

    record TestMethodMetadata(TestClassMetadata classMetadata,
                              String methodName,
                              Set<GraphCandidate> parameterComponents,
                              Set<GraphModification> parameterMocks) {

        public Set<GraphCandidate> getComponents() {
            final Set<GraphCandidate> roots = new HashSet<>();
            roots.addAll(classMetadata.annotationComponents);
            roots.addAll(classMetadata.annotationComponentsFromModules);
            roots.addAll(classMetadata.fieldComponents);
            roots.addAll(parameterComponents);
            roots.addAll(classMetadata.constructorComponents);
            return roots;
        }

        public Set<GraphCandidate> getMocks() {
            return getGraphMockCandidates(m -> m instanceof GraphMockitoMock || m instanceof GraphMockkMock);
        }

        public Set<GraphCandidate> getSpy() {
            return getGraphMockCandidates(m -> m instanceof GraphMockitoSpy || m instanceof GraphMockkSpyk);
        }

        private Set<GraphCandidate> getGraphMockCandidates(Predicate<GraphModification> predicate) {
            return Stream.of(
                    classMetadata.fieldMocks,
                    parameterMocks,
                    classMetadata.constructorMocks
                )
                .flatMap(Collection::stream)
                .filter(predicate)
                .map(GraphModification::candidate)
                .collect(Collectors.toSet());
        }
    }

    record TestClassMetadata(Class<?> testClass,
                             List<Field> fieldsForInjection,
                             @Nullable
                             Class<?> outerTestClass,
                             List<Field> outerFieldsForInjection,
                             KoraAppTest annotation,
                             TestInstance.Lifecycle lifecycle,
                             InitializeOrigin initializeOrigin,
                             Config config,
                             Set<GraphCandidate> annotationComponents,
                             Set<GraphCandidate> annotationComponentsFromModules,
                             Set<GraphCandidate> fieldComponents,
                             Set<GraphModification> fieldMocks,
                             Set<GraphCandidate> constructorComponents,
                             Set<GraphModification> constructorMocks) {

        interface Config {

            Config NONE = new Config() {

                @Override
                public Map<String, String> systemProperties() {
                    return Map.of();
                }

                @Override
                public void setup(ApplicationGraphDraw graphDraw) {
                    // do nothing
                }

                @Override
                public void cleanup() {
                    // do nothing
                }
            };

            Map<String, String> systemProperties();

            void setup(ApplicationGraphDraw graphDraw) throws IOException;

            void cleanup();
        }

        static class FileConfig implements Config {

            private final KoraConfigModification config;
            private final Map<String, String> systemProperties;

            @Nullable
            private Properties prevProperties;

            public FileConfig(KoraConfigModification config) {
                this.config = config;
                this.systemProperties = config.systemProperties();
            }

            @Override
            public Map<String, String> systemProperties() {
                return systemProperties;
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

    private static GraphMockitoContext getMockitoContext(ExtensionContext context) {
        return context.getStore(MOCKITO).computeIfAbsent(GraphMockitoContext.class, (k) -> new GraphMockitoContext(), GraphMockitoContext.class);
    }

    @Nullable
    private static GraphMockitoContext removeMockitoContext(ExtensionContext context) {
        var mockitoContext = context.getStore(MOCKITO).get(GraphMockitoContext.class, GraphMockitoContext.class);
        if (mockitoContext != null) {
            context.getStore(MOCKITO).remove(GraphMockitoContext.class, GraphMockitoContext.class);
        }
        return mockitoContext;
    }

    private static KoraTestContext getKoraTestContext(ExtensionContext context) {
        var storage = context.getStore(NAMESPACE);
        return storage.computeIfAbsent(KoraAppTest.class, (k -> {
            final KoraAppTest koraAppTest = findKoraAppTest(context)
                .orElseThrow(() -> new ExtensionConfigurationException("@KoraAppTest not found for: " + context.getRequiredTestClass()));

            var lifecycle = getLifecycle(context);
            return new KoraTestContext(koraAppTest, lifecycle);
        }), KoraTestContext.class);
    }

    private static TestInstance.Lifecycle getLifecycle(ExtensionContext context) {
        return context.getTestInstanceLifecycle().orElse(TestInstance.Lifecycle.PER_METHOD);
    }

    private void resetMocks(TestGraphContext graphInitialized) {
        logger.debug("Resetting mocks...");
        if (MockUtils.haveAnyMockEngine()) {
            for (var node : graphInitialized.graphDraw().getNodes()) {
                var mockCandidate = graphInitialized.initializedGraph().get(node);
                if (mockCandidate instanceof Wrapped<?> w) {
                    MockUtils.resetIfMock(w.value());
                } else {
                    MockUtils.resetIfMock(mockCandidate);
                }
            }
        }
    }

    private static Object getOuterClassFromNested(Object nestedInstance) {
        var nestedClass = nestedInstance.getClass();
        return Arrays.stream(nestedClass.getDeclaredFields())
            .filter(f -> f.getType().equals(nestedClass.getDeclaringClass()))
            .findFirst()
            .map(f -> {
                try {
                    f.setAccessible(true);
                    return f.get(nestedInstance);
                } catch (IllegalAccessException e) {
                    throw new ExtensionConfigurationException("Cannot access parent test instance for @Nested class: " + nestedClass.getName(), e);
                }
            })
            .orElseThrow(() -> new ExtensionConfigurationException("Cannot find parent test instance field for @Nested class: " + nestedClass.getName()));
    }

    private void injectComponentsToFields(TestClassMetadata metadata, TestGraphContext graph, ExtensionContext context) {
        if (metadata.fieldsForInjection.isEmpty() && metadata.outerFieldsForInjection.isEmpty()) {
            return;
        }

        var testInstance = context.getTestInstance()
            .map(inst -> inst.getClass().isAnnotationPresent(Nested.class) && metadata.outerTestClass == null
                ? getOuterClassFromNested(inst) // when per class lifecycle, we need to find outer class
                : inst)
            .orElseThrow(() -> missingTestInstanceError(context));
        injectToInstanceFields(testInstance, metadata.fieldsForInjection, graph, context);

        if (metadata.outerTestClass != null && context.getRequiredTestClass().isAnnotationPresent(Nested.class)) {
            var outerTestInstance = context.getTestInstance()
                .map(KoraJUnit5Extension::getOuterClassFromNested)
                .orElseThrow(() -> missingTestInstanceError(context));

            injectToInstanceFields(outerTestInstance, metadata.outerFieldsForInjection, graph, context);
        }
    }

    private static void injectToInstanceFields(Object testInstance,
                                               List<Field> fieldsForInjection,
                                               TestGraphContext graphInitialized,
                                               ExtensionContext context) {
        for (var field : fieldsForInjection) {
            final Class<?> tag = parseTag(field);
            final GraphCandidate candidate = new GraphCandidate(field.getGenericType(), tag);
            logger.debug("Looking for test method '{}' field '{}' inject candidate: {}",
                getTestMethodName(context), field.getName(), candidate);

            final Object component = getComponentFromGraph(graphInitialized, candidate,
                "field " + field.getDeclaringClass().getName() + "#" + field.getName());
            injectToField(testInstance, field, component);
        }
    }

    private static void injectToField(Object testInstance, Field field, Object value) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new ExtensionConfigurationException("""
                Cannot inject @TestComponent into field:
                  %s#%s

                Problem:
                  Injected fields cannot be static.

                Fix:
                  Remove static or use constructor/method parameter injection.
                """.formatted(field.getDeclaringClass().getName(), field.getName()));
        }

        if (Modifier.isFinal(field.getModifiers())) {
            throw new ExtensionConfigurationException("""
                Cannot inject @TestComponent into field:
                  %s#%s

                Problem:
                  Injected fields cannot be final.

                Fix:
                  Remove final or use constructor injection.
                """.formatted(field.getDeclaringClass().getName(), field.getName()));
        }

        try {
            field.setAccessible(true);
            field.set(testInstance, value);
        } catch (Exception e) {
            throw new ExtensionConfigurationException("Cannot inject @TestComponent into field: %s#%s"
                .formatted(field.getDeclaringClass().getName(), field.getName()), e);
        }
    }

    public static KoraTestContext getInitializedKoraTestContext(InitializeOrigin initializeOrigin, ExtensionContext context) {
        var started = TimeUtils.started();

        var koraTestContext = getKoraTestContext(context);
        final boolean haveMetadata = koraTestContext.metadata != null;
        final boolean haveGraph = koraTestContext.graph != null;
        final boolean isReady = haveMetadata && haveGraph;
        final String testTarget = (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_METHOD)
            ? "method '" + getTestMethodName(context) + "'"
            : "class '" + getTestClassName(context) + "'";

        if (!isReady) {
            logger.info("@KoraAppTest test {} context setup started...", testTarget);
        }

        var startedMeta = TimeUtils.started();
        if (!haveMetadata) {
            synchronized (koraTestContext) {
                if (koraTestContext.metadata == null) {
                    logger.debug("@KoraAppTest test class '{}' metadata scan started...", getTestClassName(context));

                    koraTestContext.metadata = getClassMetadata(koraTestContext, initializeOrigin, context);
                    logger.debug("@KoraAppTest test class '{}' metadata scan took: {}",
                        getTestClassName(context), TimeUtils.tookForLogging(startedMeta));
                }
            }
        }

        var startedGraph = TimeUtils.started();
        if (!haveGraph) {
            synchronized (koraTestContext) {
                if (koraTestContext.graph == null) {
                    logger.debug("@KoraAppTest test {} graph generation started...", testTarget);

                    var graph = generateTestGraph(Objects.requireNonNull(koraTestContext.metadata), context);
                    koraTestContext.graph = graph;
                    boolean isSubNodeGraph = !graph.getNodes().isEmpty();
                    boolean isSubMockGraph = !graph.getMocks().isEmpty();
                    if (isSubNodeGraph && isSubMockGraph) {
                        logger.debug("@KoraAppTest test {} graph initialization started in 'subgraph' mode...\nSubgraph consist of @Root nodes: {}\nSubgraph consist of mocks: {}",
                            testTarget, graph.getNodes(), graph.getMocks());
                    } else if (isSubNodeGraph) {
                        logger.debug("@KoraAppTest test {} graph initialization started in 'subgraph' mode...\nSubgraph consist of @Root nodes: {}",
                            testTarget, graph.getNodes());
                    } else if (isSubMockGraph) {
                        logger.debug("@KoraAppTest test {} graph initialization started in 'subgraph' mode...\nSubgraph consist of mocks: {}",
                            testTarget, graph.getMocks());
                    } else {
                        logger.debug("@KoraAppTest test {} graph initialization started in 'full graph' mode with all @Root nodes...", testTarget);
                    }

                    graph.initialize();

                    final String mode = isSubNodeGraph || isSubMockGraph
                        ? "subgraph"
                        : "full graph";

                    logger.debug("@KoraAppTest test {} graph initialization in '{}' mode took: {}",
                        testTarget, mode, TimeUtils.tookForLogging(startedGraph));
                }
            }
        }
        var graph = Objects.requireNonNull(koraTestContext.graph);

        if (!isReady) {
            List<Node<?>> nodes = graph.initialized().graphDraw().getNodes();
            if (!graph.getMocks().isEmpty() || !graph.getNodes().isEmpty()) {
                logger.info("@KoraAppTest test {} context setup in '{}' mode for '{}' nodes took: {}",
                    testTarget, "subgraph", nodes.size(), TimeUtils.tookForLogging(started));
            } else {
                logger.info("@KoraAppTest test {} context setup in '{}' mode for all '{}' nodes took: {}",
                    testTarget, "full graph", nodes.size(), TimeUtils.tookForLogging(started));
            }
        }

        return koraTestContext;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        MDC.clear();

        Class<?> testClass = context.getRequiredTestClass();
        if (testClass.isAnnotationPresent(Nested.class)) {
            var storage = context.getStore(NAMESPACE);
            var koraTestContext = storage.get(KoraAppTest.class, KoraTestContext.class);
            if (koraTestContext != null) {
                final List<Field> fieldsForInjection = ReflectionUtils.findFields(testClass,
                    KoraJUnit5Extension::isFieldInjectionCandidate,
                    ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);

                if (!fieldsForInjection.isEmpty()) {
                    throw new ExtensionConfigurationException("""
                        Cannot inject @TestComponent fields into @Nested class:
                          %s

                        Problem:
                          Outer test uses TestInstance.Lifecycle.PER_CLASS and its application graph is already initialized.
                          Nested class fields cannot change that graph's initialization context.

                        Fix:
                          Use TestInstance.Lifecycle.PER_METHOD on the outer test or move injection fields to the outer class.
                        """.formatted(testClass.getName()));
                }
            }
        }

        var lifecycle = getLifecycle(context);
        if (lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            // prepare and inject same context per class to share it across test methods
            getKoraTestContext(context);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        MDC.clear();

        var koraTestContext = getInitializedKoraTestContext(InitializeOrigin.METHOD, context);
        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            resetMocks(koraTestContext.graph.initialized()); // may be skip reset and pass it completely on user
        }
        injectComponentsToFields(koraTestContext.metadata, koraTestContext.graph.initialized(), context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        var mockitoContext = removeMockitoContext(context);

        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_METHOD) {
            if (koraTestContext.graph != null) {
                var lock = koraTestContext.graph;
                synchronized (lock) {
                    if (koraTestContext.graph.status() == TestGraph.Status.INITIALIZED) {
                        logger.debug("@KoraAppTest test method '{}' cleanup started...",
                            getTestMethodName(context));

                        var started = TimeUtils.started();
                        koraTestContext.graph.close();
                        koraTestContext.graph = null;
                        logger.info("@KoraAppTest test method '{}' cleanup took: {}",
                            getTestMethodName(context), TimeUtils.tookForLogging(started));
                    }
                }
            }
        }

        if (mockitoContext != null && !mockitoContext.isEmpty()) {
            var reporter = new MockitoUnusedStubbingReporter(mockitoContext, findStrictness(context));
            reporter.reportUnused(context);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            // check if created graph test class equal current test class (so nested class won't close upper class lifecycle graph)
            if (koraTestContext.graph != null
                && (koraTestContext.metadata.outerTestClass == null && !context.getRequiredTestClass().isAnnotationPresent(Nested.class))
                && koraTestContext.metadata.testClass().equals(context.getRequiredTestClass())) {
                var lock = koraTestContext.graph;
                synchronized (lock) {
                    if (koraTestContext.graph.status() == TestGraph.Status.INITIALIZED) {
                        logger.debug("@KoraAppTest test class '{}' cleanup started...",
                            getTestClassName(context));

                        var started = TimeUtils.started();
                        koraTestContext.graph.close();
                        logger.info("@KoraAppTest test class '{}' cleanup took: {}",
                            getTestClassName(context), TimeUtils.tookForLogging(started));
                    }
                }
            }
        }
    }

    private static Optional<KoraAppTest> findKoraAppTest(ExtensionContext context) {
        return findAnnotation(context, KoraAppTest.class);
    }

    private static Optional<MockitoStrictness> findMockStrictness(ExtensionContext context) {
        return findAnnotation(context, MockitoStrictness.class);
    }

    private static <A extends Annotation> Optional<A> findAnnotation(ExtensionContext context, Class<A> annotationClass) {
        Optional<ExtensionContext> current = Optional.of(context);
        while (current.isPresent()) {
            var testClass = current.get().getTestClass();
            if (testClass.isEmpty()) {
                return Optional.empty();
            }
            var requiredTestClass = testClass.get();
            while (!requiredTestClass.equals(Object.class)) {
                final Optional<A> annotation = AnnotationSupport.findAnnotation(requiredTestClass, annotationClass);
                if (annotation.isPresent()) {
                    return annotation;
                }
                requiredTestClass = requiredTestClass.getSuperclass();
            }
            current = current.get().getParent();
        }
        return Optional.empty();
    }

    private Strictness findStrictness(ExtensionContext context) {
        return findMockStrictness(context).map(MockitoStrictness::value).orElse(Strictness.WARN);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) throws ParameterResolutionException {
        return isCandidate(parameterContext.getParameter())
            || parameterContext.getParameter().getType().equals(KoraAppGraph.class)
            || parameterContext.getParameter().getType().equals(Graph.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) throws ParameterResolutionException {
        var koraTestContext = getInitializedKoraTestContext(InitializeOrigin.CONSTRUCTOR, context);
        var graphCandidate = getGraphCandidate(parameterContext);

        if (parameterContext.getDeclaringExecutable() instanceof Constructor<?>) {
            logger.debug("Looking for test class '{}' constructor parameter '{}' inject candidate: {}",
                getTestClassName(context), parameterContext.getParameter().getName(), graphCandidate);
        } else {
            logger.debug("Looking for test method '{}' parameter '{}' inject candidate: {}",
                getTestMethodName(context), parameterContext.getParameter().getName(), graphCandidate);
        }

        var parameter = parameterContext.getParameter();
        var executable = parameterContext.getDeclaringExecutable();
        var injectionPoint = "parameter '" + parameter.getName() + "' of "
                             + executable.getDeclaringClass().getName() + "#" + executable.getName();
        return getComponentFromGraph(koraTestContext.graph.initialized(), graphCandidate, injectionPoint);
    }

    private static String getTestClassName(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        String packageName = testClass.getPackageName();
        return (packageName.isBlank())
            ? testClass.getSimpleName()
            : testClass.getCanonicalName().substring(packageName.length() + 1);
    }

    private static String getTestMethodName(ExtensionContext context) {
        final String methodName = context.getTestMethod()
            .map(m -> m.getName() + Arrays.stream(m.getParameters())
                .map(p -> p.getType().getSimpleName())
                .collect(Collectors.joining(", ", "(", ")")))
            .orElse(context.getDisplayName());

        return getTestClassName(context) + "#" + methodName;
    }

    private static List<GraphModification> getGraphModifications(TestMethodMetadata metadata, ExtensionContext context) {
        var mockComponentFromParameters = metadata.parameterMocks();
        var mockComponentFromFields = metadata.classMetadata().fieldMocks();
        var mockComponentFromConstructor = metadata.classMetadata().constructorMocks();

        var mocks = new HashSet<>(mockComponentFromParameters);
        mocks.addAll(mockComponentFromFields);
        mocks.addAll(mockComponentFromConstructor);

        final KoraGraphModification koraGraphModification = context.getTestInstance()
            .filter(inst -> inst instanceof KoraAppTestGraphModifier)
            .map(inst -> ((KoraAppTestGraphModifier) inst).graph())
            .orElseGet(KoraGraphModification::create);

        var graphModifications = new ArrayList<>(koraGraphModification.getModifications());
        graphModifications.addAll(mocks);
        return graphModifications;
    }

    private static TestMethodMetadata getMethodMetadata(TestClassMetadata classMetadata, ExtensionContext context) {
        if (classMetadata.initializeOrigin == InitializeOrigin.CONSTRUCTOR) {
            context.getTestMethod().ifPresent(method -> {
                if (Arrays.stream(method.getParameters()).anyMatch(KoraJUnit5Extension::isCandidate)) {
                    throw new ExtensionConfigurationException("""
                        Cannot use @TestComponent or mock annotations on test method parameters after constructor injection initialized @KoraAppTest.

                        Fix:
                          Move these dependencies to constructor parameters or use field/method initialization instead.
                        """);
                }
            });
        }

        final GraphMockitoContext mockitoContext = getMockitoContext(context);
        final Set<GraphModification> parameterMocks = context.getTestMethod()
            .filter(method -> !method.isSynthetic())
            .stream()
            .flatMap(m -> Stream.of(m.getParameters()))
            .filter(KoraJUnit5Extension::isMock)
            .map(p -> mockParameter(p, mockitoContext))
            .collect(Collectors.toSet());

        if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_CLASS && !parameterMocks.isEmpty()) {
            throw new ExtensionConfigurationException("""
                Cannot inject mocks through test method parameters with TestInstance.Lifecycle.PER_CLASS.

                Problem:
                  One application graph is shared by all test methods, but method parameter mocks are method-specific.

                Fix:
                  Use TestInstance.Lifecycle.PER_METHOD or declare mocks as fields/constructor parameters.
                """);
        }

        final Set<GraphCandidate> parameterComponents = new HashSet<>();
        if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_METHOD) {
            if (classMetadata.initializeOrigin == InitializeOrigin.METHOD) {
                for (var parameter : context.getRequiredTestMethod().getParameters()) {
                    if (isComponent(parameter)) {
                        var tag = parseTag(parameter);
                        var type = parameter.getParameterizedType();
                        parameterComponents.add(new GraphCandidate(type, tag));
                    }
                }
            }
        } else if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            for (var method : context.getRequiredTestClass().getDeclaredMethods()) {
                for (var parameter : method.getParameters()) {
                    if (isComponent(parameter)) {
                        var tag = parseTag(parameter);
                        var type = parameter.getParameterizedType();
                        parameterComponents.add(new GraphCandidate(type, tag));
                    }
                }
            }
        }

        final String methodName = context.getTestMethod().map(Method::getName).orElse(null);
        return new TestMethodMetadata(classMetadata, methodName, parameterComponents, parameterMocks);
    }

    private static TestClassMetadata getClassMetadata(KoraTestContext koraAppTest,
                                                      InitializeOrigin initializeOrigin,
                                                      ExtensionContext context) {
        var testClass = context.getTestClass()
            .orElseThrow(() -> new ExtensionConfigurationException("Cannot resolve test class from JUnit ExtensionContext for @KoraAppTest"));

        if (initializeOrigin == InitializeOrigin.CONSTRUCTOR) {
            if (KoraAppTestGraphModifier.class.isAssignableFrom(testClass)) {
                throw constructorModifierError(testClass, KoraAppTestGraphModifier.class);
            } else if (KoraAppTestConfigModifier.class.isAssignableFrom(testClass)) {
                throw constructorModifierError(testClass, KoraAppTestConfigModifier.class);
            }
        }

        final Set<GraphCandidate> annotationCandidates = Arrays.stream(koraAppTest.annotation.components())
            .map(GraphCandidate::new)
            .collect(Collectors.toSet());

        final Set<GraphCandidate> koraModulesCandidates = getKoraModulesCandidates(koraAppTest);

        final TestClassMetadata.Config koraAppConfig = context.getTestInstance()
            .filter(inst -> inst instanceof KoraAppTestConfigModifier)
            .map(inst -> {
                final KoraConfigModification configModification = ((KoraAppTestConfigModifier) inst).config();
                return ((TestClassMetadata.Config) new TestClassMetadata.FileConfig(configModification));
            })
            .orElseGet(() -> {
                if (testClass.isAnnotationPresent(Nested.class)) {
                    return context.getTestInstance()
                        .map(KoraJUnit5Extension::getOuterClassFromNested)
                        .filter(inst -> inst instanceof KoraAppTestConfigModifier)
                        .map(inst -> {
                            final KoraConfigModification configModification = ((KoraAppTestConfigModifier) inst).config();
                            return ((TestClassMetadata.Config) new TestClassMetadata.FileConfig(configModification));
                        })
                        .orElse(TestClassMetadata.Config.NONE);
                } else {
                    return TestClassMetadata.Config.NONE;
                }
            });


        final List<Field> fieldsForInjection = ReflectionUtils.findFields(testClass,
            KoraJUnit5Extension::isFieldInjectionCandidate,
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);

        final Class<?> outerTestClass;
        final List<Field> outerFieldsForInjection;
        if (testClass.isAnnotationPresent(Nested.class)) {
            outerTestClass = testClass.getDeclaringClass();
            outerFieldsForInjection = ReflectionUtils.findFields(outerTestClass,
                KoraJUnit5Extension::isFieldInjectionCandidate,
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);
        } else {
            outerTestClass = null;
            outerFieldsForInjection = List.of();
        }

        final Set<GraphCandidate> fieldComponents = Stream.concat(fieldsForInjection.stream(), outerFieldsForInjection.stream())
            .filter(KoraJUnit5Extension::isComponent)
            .map(field -> {
                final Class<?> tags = parseTag(field);
                return new GraphCandidate(field.getGenericType(), tags);
            })
            .collect(Collectors.toSet());

        final GraphMockitoContext mockitoContext = getMockitoContext(context);
        final Set<GraphModification> fieldMocks = Stream.concat(fieldsForInjection.stream(), outerFieldsForInjection.stream())
            .filter(KoraJUnit5Extension::isMock)
            .map(f -> {
                Object fieldValue = null;
                if (isMockitoSpy(f) || isMockKSpyk(f)) {
                    fieldValue = context.getTestInstance()
                        .map(inst -> {
                            try {
                                f.setAccessible(true);
                                return f.get(inst);
                            } catch (IllegalAccessException e) {
                                final Class<?> tags = parseTag(f);
                                final GraphCandidate candidate = new GraphCandidate(f.getGenericType(), tags);
                                throw new ExtensionConfigurationException("Cannot read @Spy field: %s#%s (component: %s)"
                                    .formatted(f.getDeclaringClass().getName(), f.getName(), candidate), e);
                            }
                        })
                        .orElse(null);
                }

                return mockField(f, fieldValue, mockitoContext);
            })
            .collect(Collectors.toSet());

        final Set<GraphCandidate> constructorComponents = new HashSet<>();
        final Set<GraphModification> constructorMocks = new HashSet<>();
        if (initializeOrigin == InitializeOrigin.CONSTRUCTOR) {
            var constructor = testClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            for (Parameter parameter : constructor.getParameters()) {
                if (isComponent(parameter)) {
                    var tag = parseTag(parameter);
                    var type = parameter.getParameterizedType();
                    constructorComponents.add(new GraphCandidate(type, tag));
                } else if (isMock(parameter)) {
                    constructorMocks.add(mockParameter(parameter, mockitoContext));
                }
            }
        }

        return new TestClassMetadata(testClass, fieldsForInjection, outerTestClass, outerFieldsForInjection,
            koraAppTest.annotation, koraAppTest.lifecycle, initializeOrigin, koraAppConfig,
            annotationCandidates, koraModulesCandidates,
            fieldComponents, fieldMocks,
            constructorComponents, constructorMocks);
    }

    private static Set<GraphCandidate> getKoraModulesCandidates(KoraTestContext koraAppTest) {
        final Set<Class<?>> moduleInterfaces = Arrays.stream(koraAppTest.annotation.modules())
            .filter(c -> {
                if (c.isInterface()) {
                    return true;
                } else {
                    throw new ExtensionConfigurationException("""
                        Invalid @KoraAppTest module:
                          %s

                        Problem:
                          Entries in @KoraAppTest(modules = ...) must be interfaces.

                        Fix:
                          Convert this module to an interface or remove it from modules.
                        """.formatted(c.getName()));
                }
            })
            .collect(Collectors.toSet());

        final Set<GraphCandidate> factoryCandidates = new HashSet<>();
        for (Class<?> module : moduleInterfaces) {
            final Method[] declaredMethods = module.getDeclaredMethods();
            final List<Method> factoryMethods;
            if (Arrays.stream(module.getAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata"))) {
                // assume all kotlin interface methods are factories
                factoryMethods = Arrays.stream(declaredMethods).toList();
            } else {
                factoryMethods = Arrays.stream(declaredMethods)
                    .filter(Method::isDefault)
                    .toList();
            }

            for (Method factoryMethod : factoryMethods) {
                Type returnType = factoryMethod.getGenericReturnType();
                Tag tag = factoryMethod.getAnnotation(Tag.class);
                if (tag == null) {
                    factoryCandidates.add(new GraphCandidate(returnType));
                } else {
                    factoryCandidates.add(new GraphCandidate(returnType, tag.value()));
                }
            }
        }

        return factoryCandidates;
    }

    private static GraphCandidate getGraphCandidate(ParameterContext parameterContext) {
        final Type parameterType = parameterContext.getParameter().getParameterizedType();
        final Class<?> tag = parseTag(parameterContext.getParameter());
        return new GraphCandidate(parameterType, tag);
    }

    @Nullable
    private static Class<?> parseTag(AnnotatedElement object) {
        final Annotation[] annotations = object.getDeclaredAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(Tag.class)) {
                return ((Tag) annotation).value();
            }
        }

        for (Annotation annotation : annotations) {
            final Tag metaTag = annotation.annotationType().getAnnotation(Tag.class);
            if (metaTag != null) {
                return metaTag.value();
            }
        }
        return null;
    }

    private static Object getComponentFromGraph(TestGraphContext graph, GraphCandidate candidate, String injectionPoint) {
        if (KoraAppGraph.class.equals(candidate.type())) {
            return graph.koraAppGraph();
        }

        if (Graph.class.equals(candidate.type())
            || GraphImpl.class.equals(candidate.type())
            || RefreshableGraph.class.equals(candidate.type())) {
            return graph.initializedGraph();
        }

        Set<Node<?>> nodes = GraphUtils.findNodeByTypeOrAssignable(graph.graphDraw(), candidate);
        if (nodes.size() == 1) {
            Node<?> node = nodes.iterator().next();
            var object = graph.initializedGraph().get(node);
            boolean isNodeWrapped = GraphUtils.isWrapped(node.type());
            boolean isCandidateWrapped = GraphUtils.isWrapped(candidate.type());
            // a component can wrap a value and implement the requested contract itself, the way JdbcDataSource and
            // CassandraSession do, and then the component is what was asked for, not the value it wraps
            if (isNodeWrapped && !isCandidateWrapped && object instanceof Wrapped<?> w && !isInstanceOfCandidate(object, candidate.type())) {
                return w.value();
            } else {
                return object;
            }
        }
        if (nodes.size() > 1) {
            var matches = nodes.stream()
                .map(node -> "  - " + node)
                .sorted()
                .collect(Collectors.joining("\n"));
            throw new ExtensionConfigurationException("""
                Cannot inject Kora component:
                  %s

                Injection point:
                  %s

                Problem:
                  Expected one matching graph component, but found %d:
                %s

                Fix:
                  Add or correct @Tag to select one component.
                """.formatted(candidate, injectionPoint, nodes.size(), matches));
        }

        throw new ExtensionConfigurationException("""
            Cannot inject Kora component:
              %s

            Injection point:
              %s

            Problem:
              No matching component was found in the application graph.

            Check:
              - the component exists in the application graph;
              - @Tag matches the graph component;
              - @KoraAppTest components/modules include the required graph root.
            """.formatted(candidate, injectionPoint));
    }

    private static boolean isInstanceOfCandidate(Object object, Type candidateType) {
        var candidateFlat = GraphUtils.getTypeFlat(candidateType);
        return !candidateFlat.isEmpty() && candidateFlat.get(0).isInstance(object);
    }

    private static boolean isCandidate(AnnotatedElement element) {
        return element.getAnnotation(TestComponent.class) != null;
    }

    private static boolean isFieldInjectionCandidate(Field field) {
        return !field.isSynthetic()
               && isCandidate(field)
               && !isKotlinConstructorParameterField(field);
    }

    private static boolean isKotlinConstructorParameterField(Field field) {
        if (!Modifier.isFinal(field.getModifiers())
            || !Arrays.stream(field.getDeclaringClass().getAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata"))) {
            return false;
        }

        return Arrays.stream(field.getDeclaringClass().getDeclaredConstructors())
            .flatMap(c -> Arrays.stream(c.getParameters()))
            .filter(KoraJUnit5Extension::isCandidate)
            .anyMatch(p -> p.isNamePresent()
                           && p.getName().equals(field.getName())
                           && p.getType().equals(field.getType()));
    }

    private static boolean isComponent(AnnotatedElement element) {
        return isCandidate(element) && !isAnnotatedAsMock(element);
    }

    private static boolean isMock(AnnotatedElement element) {
        return isCandidate(element) && isAnnotatedAsMock(element);
    }

    private static boolean isAnnotatedAsMock(AnnotatedElement element) {
        return isMockitoMock(element) || isMockitoSpy(element) || isMockKMock(element) || isMockKSpyk(element);
    }

    private static boolean isMockitoMock(AnnotatedElement element) {
        return getAnnotation(element, "org.mockito.Mock").isPresent();
    }

    private static boolean isMockitoSpy(AnnotatedElement element) {
        return getAnnotation(element, "org.mockito.Spy").isPresent();
    }

    private static boolean isMockKMock(AnnotatedElement element) {
        return getAnnotation(element, "io.mockk.impl.annotations.MockK").isPresent();
    }

    private static boolean isMockKSpyk(AnnotatedElement element) {
        return getAnnotation(element, "io.mockk.impl.annotations.SpyK").isPresent();
    }

    private static Optional<Annotation> getAnnotation(AnnotatedElement element, String annotationName) {
        Stream<Annotation> annotations = Arrays.stream(element.getAnnotations());

        // if kotlin.reflect is in classpath and the given element is a field,
        // then we should also check property annotations
        if (MockUtils.haveKotlinReflect()) {
            if (element instanceof Field field) {
                var prop = kotlin.reflect.jvm.ReflectJvmMapping.getKotlinProperty(field);
                if (prop != null) {
                    annotations = Stream.concat(annotations, prop.getAnnotations().stream());
                }
            }
        }

        return annotations
            .filter(a -> a.annotationType().getCanonicalName().equals(annotationName))
            .findFirst();
    }

    private static GraphModification mockField(Field field, Object fieldValue, GraphMockitoContext mockitoContext) {
        if (KoraAppGraph.class.isAssignableFrom(field.getType())) {
            throw graphMockError("field " + field.getDeclaringClass().getName() + "#" + field.getName(), KoraAppGraph.class);
        }
        if (Graph.class.isAssignableFrom(field.getType())) {
            throw graphMockError("field " + field.getDeclaringClass().getName() + "#" + field.getName(), Graph.class);
        }

        final Class<?> tags = parseTag(field);
        final GraphCandidate candidate = new GraphCandidate(field.getGenericType(), tags);

        if (isMockitoMock(field)) {
            return GraphMockitoMock.ofAnnotated(candidate, mockitoContext, field, field.getName());
        } else if (isMockitoSpy(field)) {
            return GraphMockitoSpy.ofField(candidate, mockitoContext, field, fieldValue);
        } else if (isMockKMock(field)) {
            return GraphMockkMock.ofAnnotated(candidate, field, field.getName());
        } else if (isMockKSpyk(field)) {
            return GraphMockkSpyk.ofField(candidate, field, fieldValue);
        } else {
            throw new ExtensionConfigurationException("Unsupported mocking annotation on field: "
                + field.getDeclaringClass().getName() + "#" + field.getName());
        }
    }

    private static GraphModification mockParameter(Parameter parameter, GraphMockitoContext mockitoContext) {
        if (KoraAppGraph.class.isAssignableFrom(parameter.getType())) {
            throw graphMockError("parameter " + parameter, KoraAppGraph.class);
        }
        if (Graph.class.isAssignableFrom(parameter.getType())) {
            throw graphMockError("parameter " + parameter, Graph.class);
        }

        final Class<?> tag = parseTag(parameter);
        final GraphCandidate candidate = new GraphCandidate(parameter.getParameterizedType(), tag);

        if (isMockitoMock(parameter)) {
            return GraphMockitoMock.ofAnnotated(candidate, mockitoContext, parameter, parameter.getName());
        } else if (isMockitoSpy(parameter)) {
            return GraphMockitoSpy.ofAnnotated(candidate, mockitoContext, parameter);
        } else if (isMockKMock(parameter)) {
            return GraphMockkMock.ofAnnotated(candidate, parameter, parameter.getName());
        } else if (isMockKSpyk(parameter)) {
            return GraphMockkSpyk.ofAnnotated(candidate, parameter, parameter.getName());
        } else {
            throw new ExtensionConfigurationException("Unsupported mocking annotation on parameter: " + parameter);
        }
    }

    private static Set<GraphCandidate> scanGraphRoots(TestMethodMetadata metadata) {
        final Set<GraphCandidate> components = metadata.getComponents();
        final Set<GraphCandidate> mocks = metadata.getMocks();
        final Set<GraphCandidate> spies = metadata.getSpy();

        for (GraphCandidate mock : mocks) {
            if (components.contains(mock)) {
                throw new ExtensionConfigurationException("@TestComponent cannot be declared as both component and mock: " + mock);
            }
        }

        for (GraphCandidate spy : spies) {
            if (components.contains(spy)) {
                throw new ExtensionConfigurationException("@TestComponent cannot be declared as both component and spy: " + spy);
            } else if (mocks.contains(spy)) {
                throw new ExtensionConfigurationException("@TestComponent cannot be declared as both mock and spy: " + spy);
            }
        }

        var mockGraphComponents = Stream.of(
                metadata.classMetadata.fieldMocks,
                metadata.parameterMocks,
                metadata.classMetadata.constructorMocks)
            .flatMap(Collection::stream)
            .filter(m -> {
                if (components.isEmpty() || components.stream().allMatch(KoraJUnit5Extension::isGraph)) {
                    return m instanceof GraphMockitoSpy spy && spy.isSpyGraph()
                        || m instanceof GraphMockkSpyk spyk && spyk.isSpyGraph();
                }

                return true;
            })
            .map(GraphModification::candidate)
            .collect(Collectors.toSet());

        var result = new HashSet<>(components);
        result.addAll(mockGraphComponents);
        return result;
    }

    private static boolean isGraph(GraphCandidate candidate) {
        return candidate.type() instanceof Class<?> cl
            && (KoraAppGraph.class.isAssignableFrom(cl) || Graph.class.isAssignableFrom(cl));
    }

    @SuppressWarnings("unchecked")
    private static TestGraph generateTestGraph(TestClassMetadata classMetadata, ExtensionContext context) {
        var applicationClass = classMetadata.annotation.value();
        long started = TimeUtils.started();
        var graphSupplier = GRAPH_SUPPLIER_MAP.computeIfAbsent(applicationClass, k -> {
            try {
                var clazz = applicationClass.getClassLoader().loadClass(applicationClass.getName() + "Graph");
                if (!Supplier.class.isAssignableFrom(clazz)) {
                    throw new ExtensionConfigurationException("Generated Kora application graph does not implement Supplier: " + clazz.getName());
                }
                var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
                if (constructors.length == 0) {
                    throw new ExtensionConfigurationException("Generated Kora application graph has no public constructor: " + clazz.getName());
                }
                var supplier = (Supplier<ApplicationGraphDraw>) constructors[0].newInstance();
                logger.info("@KoraApp application '{}' graph class loading took: {}", applicationClass.getSimpleName(), TimeUtils.tookForLogging(started));
                return supplier;
            } catch (ClassNotFoundException e) {
                throw new ExtensionConfigurationException(missingApplicationGraphMessage(applicationClass), e);
            } catch (ExtensionConfigurationException e) {
                throw e;
            } catch (LinkageError e) {
                throw new ExtensionConfigurationException("Cannot link generated Kora application graph: "
                    + applicationClass.getName() + "Graph. Check generated sources and runtime dependencies.", e);
            } catch (Exception e) {
                throw new ExtensionConfigurationException("Cannot instantiate generated Kora application graph: "
                    + applicationClass.getName() + "Graph", e);
            }
        });

        var methodMetadata = getMethodMetadata(classMetadata, context);
        var graphDraw = graphSupplier.get().copy();

        var roots = scanGraphRoots(methodMetadata);
        var nodesForSubGraph = roots.stream()
            .flatMap(component -> GraphUtils.findNodeByTypeOrAssignable(graphDraw, component).stream())
            .collect(Collectors.toSet());

        var mockCandidates = methodMetadata.getGraphMockCandidates(m -> m instanceof GraphMockitoMock
            || m instanceof GraphMockkMock
            || m instanceof GraphMockitoSpy spy && !spy.isSpyGraph()
            || m instanceof GraphMockkSpyk spyk && !spyk.isSpyGraph());

        var mocks = new ArrayList<Node<?>>();
        for (GraphCandidate mockCandidate : mockCandidates) {
            var mockNodes = GraphUtils.findNodeByTypeOrAssignable(graphDraw, mockCandidate);
            mocks.addAll(mockNodes);
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

        var graphModifications = getGraphModifications(methodMetadata, context);
        for (GraphModification modification : graphModifications) {
            modification.accept(subGraph);
        }

        return new TestGraph(subGraph, methodMetadata, List.copyOf(nodesForSubGraph), mocks);
    }

    static String missingApplicationGraphMessage(Class<?> applicationClass) {
        var applicationClassName = applicationClass.getName();
        return """
            Cannot find generated Kora application graph for:
              %s

            The @KoraApp test application graph was not generated or cannot include its
            parent application.

            Check both processor configurations:

            1. Test application is declared in src/test.
               Enable the processor for the test source set:

               Kotlin:
                 kspTest("io.koraframework:symbol-processors:${property("koraVersion")}")

               Java:
                 testAnnotationProcessor("io.koraframework:annotation-processors")

            2. Test application extends the main application.
               Generate the main application as a Kora submodule:

               Kotlin:
                 ksp {
                     arg("kora.app.submodule.enabled", "true")
                 }

               Java:
                 compileJava {
                     options.compilerArgs += [
                         "-Akora.app.submodule.enabled=true"
                     ]
                 }

            Expected generated artifacts include:
              - %sGraph from the test processor;
              - the main application submodule implementation.

            Also inspect:
              - kspKotlin / compileJava;
              - kspTestKotlin / compileTestJava;
              - build/generated for the missing generated classes.
            """.formatted(applicationClassName, applicationClassName);
    }

    private static ExtensionConfigurationException constructorModifierError(Class<?> testClass, Class<?> modifierClass) {
        return new ExtensionConfigurationException("""
            Cannot use %s with @KoraAppTest constructor injection in:
              %s

            Problem:
              The application graph is created while resolving constructor parameters, before the test instance exists.
              %s requires that test instance.

            Fix:
              Use field or test method injection, or remove %s.
            """.formatted(modifierClass.getSimpleName(), testClass.getName(), modifierClass.getSimpleName(), modifierClass.getSimpleName()));
    }

    private static ExtensionConfigurationException missingTestInstanceError(ExtensionContext context) {
        return new ExtensionConfigurationException("Cannot access test instance for @TestComponent field injection: "
            + context.getRequiredTestClass().getName());
    }

    private static ExtensionConfigurationException graphMockError(String declaration, Class<?> graphType) {
        return new ExtensionConfigurationException("""
            Cannot mock Kora graph object:
              %s

            Declaration:
              %s

            Fix:
              Inject %s directly without a mock annotation.
            """.formatted(graphType.getName(), declaration, graphType.getSimpleName()));
    }
}
