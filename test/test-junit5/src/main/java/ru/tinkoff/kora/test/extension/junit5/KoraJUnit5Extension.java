package ru.tinkoff.kora.test.extension.junit5;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Graph;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.application.graph.Wrapped;
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

    enum InitializeOrigin {
        CONSTRUCTOR,
        METHOD
    }

    record TestMethodMetadata(TestClassMetadata classMetadata,
                              Set<GraphCandidate> parameterComponents,
                              Set<GraphModification> parameterMocks) {

        public Set<GraphCandidate> getComponents() {
            final Set<GraphCandidate> roots = new HashSet<>();
            roots.addAll(classMetadata.annotationComponents);
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
                             KoraAppTest annotation,
                             TestInstance.Lifecycle lifecycle,
                             InitializeOrigin initializeOrigin,
                             Config config,
                             Set<GraphCandidate> annotationComponents,
                             List<Field> fieldsForInjection,
                             Set<GraphCandidate> fieldComponents,
                             Set<GraphModification> fieldMocks,
                             Set<GraphCandidate> constructorComponents,
                             Set<GraphModification> constructorMocks) {

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

    private void prepareMocks(TestGraphInitialized graphInitialized) {
        logger.debug("Resetting mocks...");
        if (MockUtils.haveAnyMockEngine()) {
            for (var node : graphInitialized.graphDraw().getNodes()) {
                var mockCandidate = graphInitialized.refreshableGraph().get(node);
                MockUtils.resetIfMock(mockCandidate);
            }
        }
    }

    private void injectComponentsToFields(TestClassMetadata metadata, TestGraphInitialized graphInitialized, ExtensionContext context) {
        if (metadata.fieldsForInjection.isEmpty()) {
            return;
        }

        var testInstance = context.getTestInstance()
            .map(inst -> {
                if (inst.getClass().isAnnotationPresent(Nested.class)) {
                    return Arrays.stream(inst.getClass().getDeclaredFields())
                        .filter(f -> f.getType().equals(metadata.testClass()))
                        .findFirst()
                        .map(f -> {
                            try {
                                f.setAccessible(true);
                                return f.get(inst);
                            } catch (IllegalAccessException e) {
                                throw new IllegalStateException("Failed retrieving parent test class inside @Nested test class: " + inst.getClass());
                            }
                        })
                        .orElseThrow(() -> new IllegalStateException("Failed searching parent test class inside @Nested test class: " + inst.getClass()));
                }

                return inst;
            })
            .orElseThrow(() -> new ExtensionConfigurationException("@KoraAppTest can't get TestInstance for @TestComponent field injection"));

        for (var field : metadata.fieldsForInjection) {
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
            throw new ExtensionConfigurationException("Failed to inject field '%s' due to: ".formatted(field.getName()) + e);
        }
    }

    public static KoraTestContext getInitializedKoraTestContext(InitializeOrigin initializeOrigin, ExtensionContext context) {
        var started = TimeUtils.started();

        var koraTestContext = getKoraTestContext(context);
        final boolean isReady = koraTestContext.metadata != null && koraTestContext.graph != null;
        if (!isReady) {
            if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_METHOD) {
                logger.info("@KoraAppTest test method '{}' setup started...", getTestMethodName(context));
            } else {
                logger.info("@KoraAppTest test class '{}' setup started...", getTestClassName(context));
            }
        }

        if (koraTestContext.metadata == null) {
            logger.trace("@KoraAppTest class metadata extracting started...");
            long startedMeta = TimeUtils.started();
            koraTestContext.metadata = getClassMetadata(koraTestContext, initializeOrigin, context);
            logger.debug("@KoraAppTest class metadata extracting took: {}", TimeUtils.tookForLogging(startedMeta));
        }

        if (koraTestContext.graph == null) {
            koraTestContext.graph = generateTestGraph(koraTestContext.metadata, context);
            koraTestContext.graph.initialize();
        }

        if (!isReady) {
            if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_METHOD) {
                logger.info("@KoraAppTest test method '{}' setup took: {}", getTestMethodName(context), TimeUtils.tookForLogging(started));
            } else {
                logger.info("@KoraAppTest test class '{}' setup took: {}", getTestClassName(context), TimeUtils.tookForLogging(started));
            }
        }

        return koraTestContext;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        MDC.clear();
        getKoraTestContext(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        var koraTestContext = getInitializedKoraTestContext(InitializeOrigin.METHOD, context);
        MDC.clear();
        prepareMocks(koraTestContext.graph.initialized());
        injectComponentsToFields(koraTestContext.metadata, koraTestContext.graph.initialized(), context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_METHOD) {
            if (koraTestContext.graph != null) {
                logger.debug("@KoraAppTest test method '{}' cleanup started...", getTestMethodName(context));
                var started = TimeUtils.started();
                koraTestContext.graph.close();
                koraTestContext.graph = null;
                logger.info("@KoraAppTest test method '{}' cleanup took: {}", getTestMethodName(context), TimeUtils.tookForLogging(started));
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var koraTestContext = getKoraTestContext(context);
        if (koraTestContext.lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            if (!context.getRequiredTestClass().isAnnotationPresent(Nested.class)) {
                if (koraTestContext.graph != null) {
                    var started = TimeUtils.started();
                    logger.debug("@KoraAppTest test class '{}' cleanup started...", getTestClassName(context));
                    koraTestContext.graph.close();
                    logger.info("@KoraAppTest test class '{}' cleanup took: {}", getTestClassName(context), TimeUtils.tookForLogging(started));
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

        return getComponentFromGraph(koraTestContext.graph.initialized(), graphCandidate);
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

        return new TestMethodMetadata(classMetadata, parameterComponents, parameterMocks);
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

        final TestClassMetadata.Config koraAppConfig = context.getTestInstance()
            .filter(inst -> inst instanceof KoraAppTestConfigModifier)
            .map(inst -> {
                final KoraConfigModification configModification = ((KoraAppTestConfigModifier) inst).config();
                return ((TestClassMetadata.Config) new TestClassMetadata.FileConfig(configModification));
            })
            .orElse(TestClassMetadata.Config.NONE);

        final List<Field> fieldsForInjection = ReflectionUtils.findFields(testClass,
            f -> !f.isSynthetic() && isCandidate(f),
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);

        final Set<GraphCandidate> fieldComponents = fieldsForInjection.stream()
            .filter(KoraJUnit5Extension::isComponent)
            .map(field -> {
                final Class<?>[] tags = parseTags(field);
                return new GraphCandidate(field.getGenericType(), tags);
            })
            .collect(Collectors.toSet());

        final Set<GraphModification> fieldMocks = fieldsForInjection.stream()
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

        return new TestClassMetadata(testClass, koraAppTest.annotation, koraAppTest.lifecycle, initializeOrigin, koraAppConfig, annotationCandidates,
            fieldsForInjection, fieldComponents, fieldMocks, constructorComponents, constructorMocks);
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
            var objects = new ArrayList<>();
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
                } else if(object instanceof Wrapped<?> wo && clazz.isInstance(wo.value())) {
                    if (candidate.tags().isEmpty() && node.tags().length == 0) {
                        objects.add(wo.value());
                    } else if (candidate.tags().size() == 1 && candidate.tags().get(0).getCanonicalName().equals("ru.tinkoff.kora.common.Tag.Any")) {
                        objects.add(wo.value());
                    } else if (Arrays.equals(candidate.tagsAsArray(), node.tags())) {
                        objects.add(wo.value());
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
        if (candidate.type() instanceof ParameterizedType parameterizedType) {
            var objects = new ArrayList<>();
            var clazz = (Class<?>) parameterizedType.getRawType();
            for (var node : graph.graphDraw().getNodes()) {
                var object = graph.refreshableGraph().get(node);
                if (clazz.isInstance(object) && doesExtendOrImplement(object.getClass(), parameterizedType)) {
                    if (candidate.tags().isEmpty() && node.tags().length == 0) {
                        objects.add(object);
                    } else if (candidate.tags().size() == 1 && candidate.tags().get(0).getCanonicalName().equals("ru.tinkoff.kora.common.Tag.Any")) {
                        objects.add(object);
                    } else if (Arrays.equals(candidate.tagsAsArray(), node.tags())) {
                        objects.add(object);
                    }
                } else if (object instanceof Wrapped<?> wo && clazz.isInstance(wo.value()) && doesExtendOrImplement(object.getClass(), parameterizedType)) {
                    if (candidate.tags().isEmpty() && node.tags().length == 0) {
                        objects.add(wo.value());
                    } else if (candidate.tags().size() == 1 && candidate.tags().get(0).getCanonicalName().equals("ru.tinkoff.kora.common.Tag.Any")) {
                        objects.add(wo.value());
                    } else if (Arrays.equals(candidate.tagsAsArray(), node.tags())) {
                        objects.add(wo.value());
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

    private static boolean doesImplement(Class<?> aClass, ParameterizedType parameterizedType) {
        for (var genericInterface : aClass.getGenericInterfaces()) {
            if (genericInterface.equals(parameterizedType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean doesExtendOrImplement(Class<?> aClass, ParameterizedType parameterizedType) {
        if (doesImplement(aClass, parameterizedType)) {
            return true;
        }
        var superclass = aClass.getGenericSuperclass();
        if (superclass == null) {
            return false;
        }
        if (superclass.equals(parameterizedType)) {
            return true;
        }
        if (superclass instanceof Class<?> clazz) {
            return doesExtendOrImplement(clazz, parameterizedType);
        }
        if (superclass instanceof ParameterizedType clazz) {
            return doesExtendOrImplement((Class<?>) clazz.getRawType(), parameterizedType);
        }
        return false;
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

        final Class<?>[] tags = parseTags(field);
        final GraphCandidate candidate = new GraphCandidate(field.getGenericType(), tags);

        if (isMockitoMock(field)) {
            return GraphMockitoMock.ofAnnotated(candidate, field, field.getName());
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

        var spyGraphComponents = Stream.of(
                metadata.classMetadata.fieldMocks,
                metadata.parameterMocks,
                metadata.classMetadata.constructorMocks)
            .flatMap(Collection::stream)
            .filter(m -> m instanceof GraphMockitoSpy spy && spy.isSpyGraph()
                || m instanceof GraphMockkSpyk spyk && spyk.isSpyGraph())
            .map(GraphModification::candidate)
            .collect(Collectors.toSet());

        var result = new HashSet<>(components);
        result.addAll(spyGraphComponents);
        return result;
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
                logger.info("Instantiated and cached @KoraApp application class '{}' in {}", applicationClass.getSimpleName(), TimeUtils.tookForLogging(started));
                return supplier;
            } catch (ClassNotFoundException e) {
                throw new ExtensionConfigurationException("@KoraAppTest#value must be annotated with @KoraApp, but probably wasn't: " + applicationClass, e);
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

        return new TestGraph(subGraph, classMetadata);
    }
}
