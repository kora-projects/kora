package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.internal.configuration.plugins.Plugins;
import org.mockito.internal.session.MockitoSessionLoggerAdapter;
import org.mockito.internal.util.MockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.tinkoff.kora.application.graph.*;
import ru.tinkoff.kora.application.graph.internal.GraphImpl;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.util.TimeUtils;

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
    private static final String SESSION = "session";
    private static final Logger logger = LoggerFactory.getLogger(KoraJUnit5Extension.class);

    // Application class -> graph supplier
    private static final Map<Class<?>, Supplier<ApplicationGraphDraw>> GRAPH_SUPPLIER_MAP = new ConcurrentHashMap<>();

    private static final ExtensionContext.Namespace MOCKITO = ExtensionContext.Namespace.create("org.mockito");
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

    @Nonnull
    private static KoraTestContext getKoraTestContext(ExtensionContext context) {
        var storage = context.getStore(NAMESPACE);
        return storage.getOrComputeIfAbsent(KoraAppTest.class, (k -> {
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
                var mockCandidate = graphInitialized.refreshableGraph().get(node);
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
                    throw new IllegalStateException("Failed retrieving parent test class inside @Nested test class: " + nestedClass);
                }
            })
            .orElseThrow(() -> new IllegalStateException("Failed searching parent test class inside @Nested test class: " + nestedClass));
    }

    private void injectComponentsToFields(TestClassMetadata metadata, TestGraphContext graph, ExtensionContext context) {
        if (metadata.fieldsForInjection.isEmpty() && metadata.outerFieldsForInjection.isEmpty()) {
            return;
        }

        var testInstance = context.getTestInstance()
            .map(inst -> inst.getClass().isAnnotationPresent(Nested.class) && metadata.outerTestClass == null
                ? getOuterClassFromNested(inst) // when per class lifecycle, we need to find outer class
                : inst)
            .orElseThrow(() -> new ExtensionConfigurationException("@KoraAppTest can't get TestInstance for @TestComponent field injection"));
        injectToInstanceFields(testInstance, metadata.fieldsForInjection, graph, context);

        if (metadata.outerTestClass != null && context.getRequiredTestClass().isAnnotationPresent(Nested.class)) {
            var outerTestInstance = context.getTestInstance()
                .map(KoraJUnit5Extension::getOuterClassFromNested)
                .orElseThrow(() -> new ExtensionConfigurationException("@KoraAppTest can't get TestInstance for @TestComponent field injection"));

            if (outerTestInstance != null) {
                injectToInstanceFields(outerTestInstance, metadata.outerFieldsForInjection, graph, context);
            }
        }
    }

    private static void injectToInstanceFields(Object testInstance,
                                               List<Field> fieldsForInjection,
                                               TestGraphContext graphInitialized,
                                               ExtensionContext context) {
        for (var field : fieldsForInjection) {
            final Class<?>[] tags = parseTags(field);
            final GraphCandidate candidate = new GraphCandidate(field.getGenericType(), tags);
            logger.debug("Looking for test method '{}' field '{}' inject candidate: {}",
                getTestMethodName(context), field.getName(), candidate);

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
            throw new ExtensionConfigurationException("Failed to inject field '%s' due to: ".formatted(field.getName()) + e.getMessage(), e);
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
                    logger.debug("@KoraAppTest test {} graph initialization started...", testTarget);

                    koraTestContext.graph = generateTestGraph(koraTestContext.metadata, context);
                    koraTestContext.graph.initialize();
                    logger.debug("@KoraAppTest test {} graph initialization took: {}",
                        testTarget, TimeUtils.tookForLogging(startedGraph));
                }
            }
        }

        if (!isReady) {
            logger.info("@KoraAppTest test {} context setup took: {}",
                testTarget, TimeUtils.tookForLogging(started));
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
                    f -> !f.isSynthetic() && isCandidate(f),
                    ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);

                if (!fieldsForInjection.isEmpty()) {
                    throw new ExtensionConfigurationException("@KoraAppTest can't use @Nested class field injection when outer class lifecycle is 'PER_CLASS', " +
                                                              "cause graph is already initialized on the top level");
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
        initMockitoSession(context);

        var koraTestContext = getInitializedKoraTestContext(InitializeOrigin.METHOD, context);
        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            resetMocks(koraTestContext.graph.initialized()); // may be skip reset and pass it completely on user
        }
        injectComponentsToFields(koraTestContext.metadata, koraTestContext.graph.initialized(), context);
    }

    private void initMockitoSession(ExtensionContext context) {
        var testInstance = context.getRequiredTestInstance();
        var spyFields = new ArrayList<Field>();
        for (Field declaredField : testInstance.getClass().getDeclaredFields()) {
            if (declaredField.isAnnotationPresent(Spy.class)) {
                declaredField.setAccessible(true);
                try {
                    if (declaredField.get(testInstance) == null) {
                        declaredField.set(testInstance, Mockito.mock(declaredField.getType())); // Here we initialize spy to prevent initMocks in mockitoSession
                        spyFields.add(declaredField);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        var annotation = findKoraAppTest(context);
        MockitoSession session = Mockito.mockitoSession()
            .strictness(annotation.map(KoraAppTest::strictness).orElse(null))
            .initMocks(testInstance)
            .logger(new MockitoSessionLoggerAdapter(Plugins.getMockitoLogger())).startMocking();
        context.getStore(MOCKITO).put(SESSION, session);
        for (Field declaredField : spyFields) {
            try {
                declaredField.set(testInstance, null);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        context.getStore(MOCKITO).remove(SESSION, MockitoSession.class).finishMocking(context.getExecutionException().orElse(null));

        var mockParameters = context.getStore(MOCKITO).getOrComputeIfAbsent(HashSet.class);
        var reporter = new MockitoUnusedStubbingReporter(mockParameters, koraTestContext.annotation.strictness());

        context.getStore(MOCKITO).remove(HashSet.class);
        reporter.reportUnused(context);

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
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            // check if created graph test class equal current test class (so nested class won't close upper class lifecycle graph)
            if (koraTestContext.graph != null && koraTestContext.metadata.testClass().equals(context.getRequiredTestClass())) {
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
        return isCandidate(parameterContext.getParameter())
               || parameterContext.getParameter().getType().equals(KoraAppGraph.class)
               || parameterContext.getParameter().getType().equals(Graph.class);
    }

    @SuppressWarnings("unchecked")
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
        var component = getComponentFromGraph(koraTestContext.graph.initialized(), graphCandidate);

        if (MockUtil.isMock(component) || MockUtil.isSpy(component)) {
            context.getStore(MOCKITO).getOrComputeIfAbsent(HashSet.class).add(component);
        }

        return component;
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
                    throw new ExtensionConfigurationException("@KoraAppTest when uses constructor injection, can't inject @TestComponents or @Mock as method parameters");
                }
            });
        }

        final Set<GraphModification> parameterMocks = context.getTestMethod()
            .filter(method -> !method.isSynthetic())
            .stream()
            .flatMap(m -> Stream.of(m.getParameters()))
            .filter(KoraJUnit5Extension::isMock)
            .map(KoraJUnit5Extension::mockParameter)
            .collect(Collectors.toSet());

        if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_CLASS && !parameterMocks.isEmpty()) {
            throw new ExtensionConfigurationException("@KoraAppTest when run in 'TestInstance.Lifecycle.PER_CLASS' test can't inject Mocks in method parameters");
        }

        final Set<GraphCandidate> parameterComponents = new HashSet<>();
        if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_METHOD) {
            if (classMetadata.initializeOrigin == InitializeOrigin.METHOD) {
                for (var parameter : context.getRequiredTestMethod().getParameters()) {
                    if (isComponent(parameter)) {
                        var tag = parseTags(parameter);
                        var type = parameter.getParameterizedType();
                        parameterComponents.add(new GraphCandidate(type, tag));
                    }
                }
            }
        } else if (classMetadata.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            for (var method : context.getRequiredTestClass().getDeclaredMethods()) {
                for (var parameter : method.getParameters()) {
                    if (isComponent(parameter)) {
                        var tag = parseTags(parameter);
                        var type = parameter.getParameterizedType();
                        parameterComponents.add(new GraphCandidate(type, tag));
                    }
                }
            }
        }

        final String methodName = context.getTestMethod().map(Method::getName).orElse(null);
        return new TestMethodMetadata(classMetadata, methodName, parameterComponents, parameterMocks);
    }

    private static TestClassMetadata getClassMetadata(KoraTestContext koraAppTest, InitializeOrigin initializeOrigin, ExtensionContext context) {
        var testClass = context.getTestClass()
            .orElseThrow(() -> new ExtensionConfigurationException("@KoraAppTest can't get TestInstance for @TestComponent field injection"));

        if (initializeOrigin == InitializeOrigin.CONSTRUCTOR) {
            if (KoraAppTestGraphModifier.class.isAssignableFrom(testClass)) {
                throw new ExtensionConfigurationException("@KoraAppTest when uses constructor injection, can't use KoraAppTestGraphModifier cause it requires test class instance first, use field injection");
            } else if (KoraAppTestConfigModifier.class.isAssignableFrom(testClass)) {
                throw new ExtensionConfigurationException("@KoraAppTest when uses constructor injection, can't use KoraAppTestConfigModifier cause it requires test class instance first, use field injection");
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
            f -> !f.isSynthetic() && isCandidate(f),
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);

        final Class<?> outerTestClass;
        final List<Field> outerFieldsForInjection;
        if (testClass.isAnnotationPresent(Nested.class)) {
            outerTestClass = testClass.getDeclaringClass();
            outerFieldsForInjection = ReflectionUtils.findFields(outerTestClass,
                f -> !f.isSynthetic() && isCandidate(f),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);
        } else {
            outerTestClass = null;
            outerFieldsForInjection = List.of();
        }

        final Set<GraphCandidate> fieldComponents = Stream.concat(fieldsForInjection.stream(), outerFieldsForInjection.stream())
            .filter(KoraJUnit5Extension::isComponent)
            .map(field -> {
                final Class<?>[] tags = parseTags(field);
                return new GraphCandidate(field.getGenericType(), tags);
            })
            .collect(Collectors.toSet());

        final Set<GraphModification> fieldMocks = Stream.concat(fieldsForInjection.stream(), outerFieldsForInjection.stream())
            .filter(KoraJUnit5Extension::isMock)
            .map(f -> {
                Object fieldValue = null;
                if (isMockitoSpy(f) || isMockKSpyk(f) || isMockitoMock(f)) {
                    fieldValue = context.getTestInstance()
                        .map(inst -> {
                            try {
                                f.setAccessible(true);
                                return f.get(inst);
                            } catch (IllegalAccessException e) {
                                final Class<?>[] tags = parseTags(f);
                                final GraphCandidate candidate = new GraphCandidate(f.getGenericType(), tags);
                                throw new IllegalArgumentException("Can't extract @Spy component '%s' for field: %s".formatted(candidate.type(), f.getName()));
                            }
                        })
                        .orElse(null);
                }

                return mockField(f, fieldValue);
            })
            .collect(Collectors.toSet());

        final Set<GraphCandidate> constructorComponents = new HashSet<>();
        final Set<GraphModification> constructorMocks = new HashSet<>();
        if (initializeOrigin == InitializeOrigin.CONSTRUCTOR) {
            var constructor = testClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            for (Parameter parameter : constructor.getParameters()) {
                if (isComponent(parameter)) {
                    var tag = parseTags(parameter);
                    var type = parameter.getParameterizedType();
                    constructorComponents.add(new GraphCandidate(type, tag));
                } else if (isMock(parameter)) {
                    constructorMocks.add(mockParameter(parameter));
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
                    throw new ExtensionConfigurationException("@KoraAppTest(modules = %s.class) is not an interface, only interfaces can be a module".formatted(c.getCanonicalName()));
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

    private static Object getComponentFromGraph(TestGraphContext graph, GraphCandidate candidate) {
        if (KoraAppGraph.class.equals(candidate.type())) {
            return graph.koraAppGraph();
        }

        if (Graph.class.equals(candidate.type())
            || GraphImpl.class.equals(candidate.type())
            || RefreshableGraph.class.equals(candidate.type())) {
            return graph.refreshableGraph();
        }

        Set<Node<?>> nodes = GraphUtils.findNodeByTypeOrAssignable(graph.graphDraw(), candidate);
        if (nodes.size() == 1) {
            Node<?> node = nodes.iterator().next();
            var object = graph.refreshableGraph().get(node);
            boolean isNodeWrapped = GraphUtils.isWrapped(node.type());
            boolean isCandidateWrapped = GraphUtils.isWrapped(candidate.type());
            if (isNodeWrapped && !isCandidateWrapped && object instanceof Wrapped<?> w) {
                return w.value();
            } else {
                return object;
            }
        }
        if (nodes.size() > 1) {
            throw new ExtensionConfigurationException(candidate + " expected to have one suitable component, got " + nodes.size());
        }

        throw new ExtensionConfigurationException(candidate + " wasn't found in graph, please check @KoraAppTest configuration");
    }

    private static boolean isCandidate(AnnotatedElement element) {
        return element.getAnnotation(TestComponent.class) != null;
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

    private static GraphModification mockField(Field field, Object fieldValue) {
        if (KoraAppGraph.class.isAssignableFrom(field.getType())) {
            throw new ExtensionConfigurationException("KoraAppGraph can't be target of @Mock");
        }
        if (Graph.class.isAssignableFrom(field.getType())) {
            throw new ExtensionConfigurationException("Graph can't be target of @Mock");
        }

        final Class<?>[] tags = parseTags(field);
        final GraphCandidate candidate = new GraphCandidate(field.getGenericType(), tags);

        if (isMockitoMock(field)) {
            return GraphMockitoMock.ofField(candidate, field, field.getName(), fieldValue);
        } else if (isMockitoSpy(field)) {
            return GraphMockitoSpy.ofField(candidate, field, fieldValue);
        } else if (isMockKMock(field)) {
            return GraphMockkMock.ofAnnotated(candidate, field, field.getName());
        } else if (isMockKSpyk(field)) {
            return GraphMockkSpyk.ofField(candidate, field, fieldValue);
        } else {
            throw new IllegalArgumentException("Unsupported Mocking behavior for field: " + field.getName());
        }
    }

    private static GraphModification mockParameter(Parameter parameter) {
        if (KoraAppGraph.class.isAssignableFrom(parameter.getType())) {
            throw new ExtensionConfigurationException("KoraAppGraph can't be target of @Mock");
        }
        if (Graph.class.isAssignableFrom(parameter.getType())) {
            throw new ExtensionConfigurationException("Graph can't be target of @Mock");
        }

        final Class<?>[] tag = parseTags(parameter);
        final GraphCandidate candidate = new GraphCandidate(parameter.getParameterizedType(), tag);

        if (isMockitoMock(parameter)) {
            return GraphMockitoMock.ofAnnotated(candidate, parameter, parameter.getName());
        } else if (isMockitoSpy(parameter)) {
            return GraphMockitoSpy.ofAnnotated(candidate, parameter);
        } else if (isMockKMock(parameter)) {
            return GraphMockkMock.ofAnnotated(candidate, parameter, parameter.getName());
        } else if (isMockKSpyk(parameter)) {
            return GraphMockkSpyk.ofAnnotated(candidate, parameter, parameter.getName());
        } else {
            throw new UnsupportedOperationException("Unsupported Mocking behavior for parameter: " + parameter.getName());
        }
    }

    private static Set<GraphCandidate> scanGraphRoots(TestMethodMetadata metadata) {
        final Set<GraphCandidate> components = metadata.getComponents();
        final Set<GraphCandidate> mocks = metadata.getMocks();
        final Set<GraphCandidate> spies = metadata.getSpy();

        for (GraphCandidate mock : mocks) {
            if (components.contains(mock)) {
                throw new IllegalStateException("@TestComponent can't be injected as Component and Mock simultaneously, check test declaration for: " + mock);
            }
        }

        for (GraphCandidate spy : spies) {
            if (components.contains(spy)) {
                throw new IllegalStateException("@TestComponent can't be injected as Component and Spy simultaneously, check test declaration for: " + spy);
            } else if (mocks.contains(spy)) {
                throw new IllegalStateException("@TestComponent can't be injected as Mock and Spy simultaneously, check test declaration for: " + spy);
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
                var clazz = KoraJUnit5Extension.class.getClassLoader().loadClass(applicationClass.getName() + "Graph");
                var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
                var supplier = (Supplier<ApplicationGraphDraw>) constructors[0].newInstance();
                logger.info("@KoraApp application '{}' graph class loading took: {}", applicationClass.getSimpleName(), TimeUtils.tookForLogging(started));
                return supplier;
            } catch (ClassNotFoundException e) {
                throw new ExtensionConfigurationException("@KoraAppTest#value must be annotated with @KoraApp, can't find generated application graph: " + applicationClass, e);
            } catch (Exception e) {
                throw new IllegalStateException(e);
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

        return new TestGraph(subGraph, methodMetadata);
    }
}
