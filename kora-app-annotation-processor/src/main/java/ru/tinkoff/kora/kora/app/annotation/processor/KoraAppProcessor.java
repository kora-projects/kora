package ru.tinkoff.kora.kora.app.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ModuleDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.NewRoundException;
import ru.tinkoff.kora.kora.app.annotation.processor.interceptor.ComponentInterceptors;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedOptions("koraLogLevel")
public class KoraAppProcessor extends AbstractKoraProcessor {

    private static final String OPTION_SUBMODULE_GENERATION = "kora.app.submodule.enabled";

    public static final int COMPONENTS_PER_HOLDER_CLASS = 500;

    private static final Logger log = LoggerFactory.getLogger(KoraAppProcessor.class);

    private final Set<TypeElement> loggedComponents = new LinkedHashSet<>();
    private final Set<TypeElement> loggedApplicationModules = new LinkedHashSet<>();
    private final Set<TypeElement> loggedExternalModules = new LinkedHashSet<>();

    private final Map<TypeElement, ProcessingState> annotatedElements = new HashMap<>();
    private final List<TypeElement> modules = new ArrayList<>();
    private final List<TypeElement> components = new ArrayList<>();
    private final ArrayList<TypeElement> appParts = new ArrayList<>();
    private volatile boolean initialized = false;
    private volatile boolean isKoraAppSubmoduleEnabled = false;
    private TypeElement koraAppElement;
    private TypeElement moduleElement;
    private TypeElement koraSubmoduleElement;
    private TypeElement componentElement;
    private ProcessingContext ctx;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.koraAppElement = this.elements.getTypeElement(CommonClassNames.koraApp.canonicalName());
        if (this.koraAppElement == null) {
            return;
        }
        this.moduleElement = this.elements.getTypeElement(CommonClassNames.module.canonicalName());
        this.koraSubmoduleElement = this.elements.getTypeElement(CommonClassNames.koraSubmodule.canonicalName());
        this.componentElement = this.elements.getTypeElement(CommonClassNames.component.canonicalName());
        this.initialized = true;
        this.isKoraAppSubmoduleEnabled = Boolean.parseBoolean(processingEnv.getOptions().getOrDefault(OPTION_SUBMODULE_GENERATION, "false"));
        this.ctx = new ProcessingContext(processingEnv);
        log.info("@KoraApp processor started");
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        if (!this.initialized) {
            return Set.of();
        }
        return Set.of(CommonClassNames.koraApp.canonicalName(), CommonClassNames.module.canonicalName(), CommonClassNames.component.canonicalName(), CommonClassNames.koraSubmodule.canonicalName(), CommonClassNames.koraGenerated.canonicalName());
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(OPTION_SUBMODULE_GENERATION);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!this.initialized) {
            return false;
        }
        this.processGenerated(roundEnv);
        var newModules = this.processModules(roundEnv);
        var newComponents = this.processComponents(roundEnv);
        this.processAppParts(roundEnv);
        if (newModules || newComponents) {
            for (var app : new ArrayList<>(this.annotatedElements.keySet())) {
                this.annotatedElements.put(app, parseNone(app));
            }
        }
        var koraAppElements = roundEnv.getElementsAnnotatedWith(this.koraAppElement);
        for (var element : koraAppElements) {
            if (element.getKind() == ElementKind.INTERFACE) {
                if (log.isInfoEnabled()) {
                    log.info("@KoraApp element found:\n{}", element.toString().indent(4));
                }
                this.annotatedElements.computeIfAbsent((TypeElement) element, k -> parseNone(element));
            } else {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@KoraApp can be placed only on interfaces", element);
            }
        }

        var results = new HashMap<TypeElement, ProcessingState>();
        if (!roundEnv.processingOver()) {
            LogUtils.logElementsFull(log, Level.DEBUG, "Processing elements this Round", this.annotatedElements.keySet());

            for (var annotatedClass : this.annotatedElements.entrySet()) {
                var processingResult = annotatedClass.getValue();
                if (processingResult instanceof ProcessingState.Ok) {
                    continue;
                }
                try {
                    if (processingResult instanceof ProcessingState.Failed failed) {
                        processingResult = this.parseNone(annotatedClass.getKey());
                    }
                    if (processingResult instanceof ProcessingState.None none) {
                        processingResult = this.processNone(none);
                    }
                    if (processingResult instanceof ProcessingState.NewRoundRequired newRound) {
                        var newState = this.processProcessing(roundEnv, newRound.processing());
                        results.put(annotatedClass.getKey(), newState);
                        continue;
                    }
                    if (processingResult instanceof ProcessingState.Processing processing) {
                        var newState = this.processProcessing(roundEnv, processing);
                        results.put(annotatedClass.getKey(), newState);
                    }
                } catch (NewRoundException e) {
                    if (!roundEnv.processingOver() || processingResult instanceof ProcessingState.None) {
                        results.put(annotatedClass.getKey(), new ProcessingState.NewRoundRequired(e.getSource(), e.getType(), e.getTag(), e.getResolving()));// todo
                    }
                } catch (ProcessingErrorException e) {
                    results.put(annotatedClass.getKey(), new ProcessingState.Failed(e, processingResult.stack()));
                } catch (Exception e) {
                    if (e instanceof FilerException || e.getCause() instanceof FilerException) {
                        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, e.getMessage());
                    } else {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        this.annotatedElements.putAll(results);
        if (roundEnv.processingOver()) {
            LogUtils.logElementsFull(log, Level.DEBUG, "Processing elements this Round", this.annotatedElements.keySet());

            for (var element : this.annotatedElements.entrySet()) {
                var processingResult = element.getValue();
                if (processingResult instanceof ProcessingState.NewRoundRequired newRound) {
                    this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Component was expected to be generated by extension %s but was not: %s/%s".formatted(newRound.source(), newRound.type(), newRound.tag())
                    );
                }
                if (processingResult instanceof ProcessingState.Failed failed) {
                    failed.detailedException().printError(this.processingEnv);
                    if (!failed.stack().isEmpty()) {
                        log.error("Processing exception", failed.detailedException());

                        var i = processingResult.stack().descendingIterator();
                        var frames = new ArrayList<ProcessingState.ResolutionFrame.Component>();
                        while (i.hasNext()) {
                            var frame = i.next();
                            if (frame instanceof ProcessingState.ResolutionFrame.Component c) {
                                frames.add(0, c);
                            } else {
                                break;
                            }
                        }
                        var chain = frames.stream()
                            .map(c -> c.declaration().declarationString() + "   " + c.dependenciesToFind().get(c.currentDependency()))
                            .collect(Collectors.joining("\n            ^            \n            |            \n"));
                        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Dependency resolve process: \n" + chain);
                    }
                }
                if (processingResult instanceof ProcessingState.Ok ok) {
                    try {
                        this.write(element.getKey(), ok);
                    } catch (NewRoundException e) {
                        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Component was expected to be generated by extension %s but was not: %s/%s".formatted(e.getSource(), e.getType(), e.getTag())
                        );
                    } catch (ProcessingErrorException e) {
                        e.printError(this.processingEnv);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            try {
                this.generateAppParts();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private ProcessingState processProcessing(RoundEnvironment roundEnv, ProcessingState.Processing processing) {
        return GraphBuilder.processProcessing(ctx, roundEnv, processing);
    }

    private void processGenerated(RoundEnvironment roundEnv) {
        if (log.isDebugEnabled()) {
            var elements = roundEnv.getElementsAnnotatedWith(Generated.class);
            if (!elements.isEmpty()) {
                LogUtils.logElementsFull(log, Level.DEBUG, "Generated previous Round", elements);
            } else {
                log.debug("Nothing was generated previous Round.");
            }
        }
    }

    private boolean processComponents(RoundEnvironment roundEnv) {
        var componentOfElements = roundEnv.getElementsAnnotatedWith(this.componentElement);

        List<TypeElement> processedComponents = new ArrayList<>();
        List<TypeElement> processedWaitsProxy = new ArrayList<>();

        for (var componentElement : componentOfElements) {
            if (componentElement.getKind() != ElementKind.CLASS) {
                continue;
            }
            if (componentElement.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }

            var typeElement = (TypeElement) componentElement;
            if (CommonUtils.hasAopAnnotations(typeElement)) {
                processedWaitsProxy.add(typeElement);
            } else {
                this.components.add(typeElement);
                processedComponents.add(typeElement);
            }
        }

        if (!processedWaitsProxy.isEmpty()) {
            LogUtils.logElementsFull(log, Level.TRACE, "Components waiting for aspects found", processedWaitsProxy);
        }
        if (!processedComponents.isEmpty()) {
            var logComponents = processedComponents.stream()
                .filter(c -> !loggedComponents.contains(c))
                .toList();
            if (!logComponents.isEmpty()) {
                LogUtils.logElementsFull(log, Level.TRACE, "Components ready for injection found", logComponents);
                loggedComponents.addAll(logComponents);
            }
        }

        return !componentOfElements.isEmpty();
    }

    private boolean processModules(RoundEnvironment roundEnv) {
        var moduleOfElements = roundEnv.getElementsAnnotatedWith(this.moduleElement);

        List<TypeElement> processedModules = new ArrayList<>();
        for (var moduleElement : moduleOfElements) {
            if (moduleElement.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            this.modules.add((TypeElement) moduleElement);
            processedModules.add((TypeElement) moduleElement);
        }

        if (!processedModules.isEmpty()) {
            var logModules = processedModules.stream()
                .filter(c -> !loggedApplicationModules.contains(c))
                .toList();
            if (!logModules.isEmpty()) {
                LogUtils.logElementsFull(log, Level.INFO, "Application modules found", logModules);
                loggedApplicationModules.addAll(logModules);
            }
        }

        return !moduleOfElements.isEmpty();
    }

    private ProcessingState.Processing processNone(ProcessingState.None none) {
        var stack = new ArrayDeque<ProcessingState.ResolutionFrame>();
        for (int i = 0; i < none.rootSet().size(); i++) {
            stack.addFirst(new ProcessingState.ResolutionFrame.Root(i));
        }
        return new ProcessingState.Processing(none.root(), none.allModules(), none.sourceDeclarations(), none.templates(), none.rootSet(), new ArrayList<>(256), stack);
    }

    private ProcessingState parseNone(Element classElement) {
        if (classElement.getKind() != ElementKind.INTERFACE) {
            return new ProcessingState.Failed(new ProcessingErrorException("@KoraApp is only applicable to interfaces", classElement), new ArrayDeque<>());
        }
        try {
            var type = (TypeElement) classElement;
            var interfaces = KoraAppUtils.collectInterfaces(this.types, type);

            if (!interfaces.isEmpty()) {
                var logExtModules = interfaces.stream()
                    .filter(c -> !loggedExternalModules.contains(c))
                    .toList();
                if (!logExtModules.isEmpty()) {
                    LogUtils.logElementsFull(log, Level.INFO, "External modules found", logExtModules);
                    loggedExternalModules.addAll(logExtModules);
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("Effective modules found:\n{}", Stream.concat(Stream.of(type), this.modules.stream()).flatMap(t -> KoraAppUtils.collectInterfaces(this.types, t).stream())
                    .map(Object::toString).sorted()
                    .collect(Collectors.joining("\n")).indent(4));
            }
            var mixedInModuleComponents = KoraAppUtils.parseComponents(this.ctx, interfaces.stream().map(ModuleDeclaration.MixedInModule::new).toList());
            if (log.isTraceEnabled()) {
                log.trace("Effective methods of {}:\n{}", classElement, mixedInModuleComponents.stream().map(Object::toString).sorted().collect(Collectors.joining("\n")).indent(4));
            }
            var submodules = KoraAppUtils.findKoraSubmoduleModules(this.elements, interfaces, type, processingEnv);
            var discoveredModules = this.modules.stream().flatMap(t -> KoraAppUtils.collectInterfaces(this.types, t).stream());
            var allModules = Stream.concat(discoveredModules, submodules.stream()).sorted(Comparator.comparing(Objects::toString)).toList();
            var annotatedModulesComponents = KoraAppUtils.parseComponents(this.ctx, allModules.stream().map(ModuleDeclaration.AnnotatedModule::new).toList());
            var allComponents = new ArrayList<ComponentDeclaration>(this.components.size() + mixedInModuleComponents.size() + annotatedModulesComponents.size());
            for (var component : this.components) {
                allComponents.add(ComponentDeclaration.fromAnnotated(ctx, component));
            }
            allComponents.addAll(mixedInModuleComponents);
            allComponents.addAll(annotatedModulesComponents);
            allComponents.sort(Comparator.comparing(Objects::toString));

            record Components(List<ComponentDeclaration> templates, List<ComponentDeclaration> nonTemplates) {}
            var components = allComponents.stream().collect(Collectors.teeing(
                Collectors.filtering(ComponentDeclaration::isTemplate, Collectors.toList()),
                Collectors.filtering(Predicate.not(ComponentDeclaration::isTemplate), Collectors.toCollection(ArrayList::new)),
                Components::new
            ));

            var sourceDescriptors = components.nonTemplates;
            var rootSet = sourceDescriptors.stream()
                .filter(cd -> AnnotationUtils.isAnnotationPresent(cd.source(), CommonClassNames.root)
                              || cd instanceof ComponentDeclaration.AnnotatedComponent ac && AnnotationUtils.isAnnotationPresent(ac.typeElement(), CommonClassNames.root))
                .toList();
            return new ProcessingState.None(type, allModules, sourceDescriptors, components.templates, rootSet);
        } catch (ProcessingErrorException e) {
            return new ProcessingState.Failed(e, new ArrayDeque<>());
        }
    }

    @Nullable
    private ExecutableElement findSinglePublicConstructor(TypeElement element) {
        var constructors = element.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .map(ExecutableElement.class::cast)
            .toList();
        if (constructors.isEmpty()) {
            throw new ProcessingErrorException(
                "Type annotated with @Component has no public constructors", element
            );
        }
        if (constructors.size() > 1) {
            throw new ProcessingErrorException(
                "Type annotated with @Component has more then one public constructor", element
            );
        }
        return constructors.get(0);
    }


    private void write(TypeElement type, ProcessingState.Ok ok) throws IOException {
        var interceptors = ComponentInterceptors.parseInterceptors(this.ctx, ok.components());

        var applicationImplFile = this.generateImpl(type, ok.allModules());
        var applicationGraphFile = this.generateApplicationGraph(type, ok.allModules(), interceptors, ok.components());

        applicationImplFile.writeTo(this.processingEnv.getFiler());
        applicationGraphFile.writeTo(this.processingEnv.getFiler());
    }


    private JavaFile generateApplicationGraph(Element classElement, List<TypeElement> allModules, ComponentInterceptors interceptors, List<ResolvedComponent> components) {
        var packageElement = (PackageElement) classElement.getEnclosingElement();
        var implClass = ClassName.get(packageElement.getQualifiedName().toString(), "$" + classElement.getSimpleName().toString() + "Impl");
        var graphName = classElement.getSimpleName().toString() + "Graph";
        var graphTypeName = ClassName.get(packageElement.getQualifiedName().toString(), graphName);
        var classBuilder = TypeSpec.classBuilder(graphName)
            .addAnnotation(AnnotationUtils.generated(KoraAppProcessor.class))
            .addOriginatingElement(classElement)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Supplier.class), CommonClassNames.applicationGraphDraw))
            .addField(CommonClassNames.applicationGraphDraw, "graphDraw", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .addMethod(MethodSpec
                .methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(CommonClassNames.applicationGraphDraw)
                .addStatement("return graphDraw")
                .build());
        for (var component : this.components) {
            classBuilder.addOriginatingElement(component);
        }
        for (var module : this.modules) {
            classBuilder.addOriginatingElement(module);
        }

        var currentClass = (TypeSpec.Builder) null;
        var currentConstructor = (MethodSpec.Builder) null;
        int holders = 0;
        for (int i = 0; i < components.size(); i++) {
            var componentNumber = i % COMPONENTS_PER_HOLDER_CLASS;
            if (componentNumber == 0) {
                if (currentClass != null) {
                    currentClass.addMethod(currentConstructor.build());
                    classBuilder.addType(currentClass.build());
                    var prevNumber = ((i / COMPONENTS_PER_HOLDER_CLASS) - 1);
                    classBuilder.addField(graphTypeName.nestedClass("ComponentHolder" + prevNumber), "holder" + prevNumber, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
                }
                holders++;
                var className = graphTypeName.nestedClass("ComponentHolder" + i / COMPONENTS_PER_HOLDER_CLASS);
                currentClass = TypeSpec.classBuilder(className)
                    .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", CodeBlock.of("$S", KoraAppProcessor.class.getCanonicalName()))
                        .build())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
                currentConstructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(CommonClassNames.applicationGraphDraw, "graphDraw")
                    .addParameter(implClass, "impl")
                    .addStatement("var map = new $T<$T, $T>()", HashMap.class, String.class, Type.class)
                    .beginControlFlow("for (var field : $T.class.getDeclaredFields())", className)
                    .addStatement("if (!field.getName().startsWith($S)) continue", "component")
                    .addStatement("map.put(field.getName(), (($T) field.getGenericType()).getActualTypeArguments()[0])", ParameterizedType.class)
                    .endControlFlow();
                for (int j = 0; j < i / COMPONENTS_PER_HOLDER_CLASS; j++) {
                    currentConstructor.addParameter(graphTypeName.nestedClass("ComponentHolder" + j), "ComponentHolder" + j);
                }
            }
            var component = components.get(i);
            TypeName componentTypeName = TypeName.get(component.type()).box();
            var typeMirrorElement = types.asElement(component.type());
            if (typeMirrorElement instanceof TypeElement te) {
                var annotation = AnnotationUtils.findAnnotation(typeMirrorElement, CommonClassNames.aopProxy);
                if (annotation != null) {
                    var superElement = types.asElement(te.getSuperclass());
                    var aopProxyName = NameUtils.generatedType(superElement, "_AopProxy");
                    if (typeMirrorElement.getSimpleName().contentEquals(aopProxyName)) {
                        componentTypeName = TypeName.get(te.getSuperclass()).box();
                    }
                }
            }

            currentClass.addField(FieldSpec.builder(ParameterizedTypeName.get(CommonClassNames.node, componentTypeName), component.fieldName(), Modifier.PRIVATE, Modifier.FINAL).build());
            currentConstructor.addStatement("var _type_of_$L = map.get($S)", component.fieldName(), component.fieldName());
            var statement = this.generateComponentStatement(graphTypeName, allModules, interceptors, components, component);
            currentConstructor.addStatement(statement);
        }
        if (components.size() > 0) {
            var lastComponentNumber = components.size() / COMPONENTS_PER_HOLDER_CLASS;
            if (components.size() % COMPONENTS_PER_HOLDER_CLASS == 0) {
                lastComponentNumber--;
            }
            currentClass.addMethod(currentConstructor.build());
            classBuilder.addType(currentClass.build());
            classBuilder.addField(graphTypeName.nestedClass("ComponentHolder" + lastComponentNumber), "holder" + lastComponentNumber, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        }


        var staticBlock = CodeBlock.builder();

        staticBlock
            .addStatement("var impl = new $T()", implClass)
            .addStatement("graphDraw = new $T($T.class)", CommonClassNames.applicationGraphDraw, classElement);
        for (int i = 0; i < holders; i++) {
            staticBlock.add("$N = new $T(graphDraw, impl", "holder" + i, graphTypeName.nestedClass("ComponentHolder" + i));
            for (int j = 0; j < i; j++) {
                staticBlock.add(", holder" + j);
            }
            staticBlock.add(");\n");
        }

        var supplierMethodBuilder = MethodSpec.methodBuilder("graph")
            .returns(CommonClassNames.applicationGraphDraw)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addStatement("return graphDraw", graphName);


        return JavaFile.builder(packageElement.getQualifiedName().toString(), classBuilder
                .addMethod(supplierMethodBuilder.build())
                .addStaticBlock(staticBlock.build())
                .build())
            .build();
    }

    private CodeBlock generateComponentStatement(ClassName graphTypeName, List<TypeElement> allModules, ComponentInterceptors interceptors, List<ResolvedComponent> components, ResolvedComponent component) {
        var statement = CodeBlock.builder();
        var declaration = component.declaration();
        statement.add("$L = graphDraw.addNode0(_type_of_$L, ", component.fieldName(), component.fieldName());
        statement.add("new Class<?>[]{");
        for (var tag : component.tags()) {
            statement.add("$L.class, ", tag);
        }
        statement.add("}, g -> ");
        var dependenciesCode = this.generateDependenciesCode(component, graphTypeName, components);

        if (declaration instanceof ComponentDeclaration.AnnotatedComponent annotatedComponent) {
            statement.add("new $T", ClassName.get(annotatedComponent.typeElement()));
            if (!annotatedComponent.typeVariables().isEmpty()) {
                statement.add("<");
                for (int i = 0; i < annotatedComponent.typeVariables().size(); i++) {
                    if (i > 0) statement.add(", ");
                    statement.add("$T", annotatedComponent.typeVariables().get(i));
                }
                statement.add(">");
            }
            statement.add("($L)", dependenciesCode);
        } else if (declaration instanceof ComponentDeclaration.FromModuleComponent moduleComponent) {
            if (moduleComponent.module() instanceof ModuleDeclaration.AnnotatedModule annotatedModule) {
                statement.add("impl.module$L.", allModules.indexOf(annotatedModule.element()));
            } else {
                statement.add("impl.");
            }
            if (!moduleComponent.typeVariables().isEmpty()) {
                statement.add("<");
                for (int i = 0; i < moduleComponent.typeVariables().size(); i++) {
                    if (i > 0) statement.add(", ");
                    statement.add("$T", moduleComponent.typeVariables().get(i));
                }
                statement.add(">");
            }
            statement.add("$L($L)", moduleComponent.method().getSimpleName(), dependenciesCode);
        } else if (declaration instanceof ComponentDeclaration.FromExtensionComponent extension) {
            statement.add(extension.generator().apply(dependenciesCode));
        } else if (declaration instanceof ComponentDeclaration.DiscoveredAsDependencyComponent asDependencyComponent) {
            if (asDependencyComponent.typeElement().getTypeParameters().isEmpty()) {
                statement.add("new $T($L)", ClassName.get(asDependencyComponent.typeElement()), dependenciesCode);
            } else {
                statement.add("new $T<>($L)", ClassName.get(asDependencyComponent.typeElement()), dependenciesCode);
            }
        } else if (declaration instanceof ComponentDeclaration.PromisedProxyComponent promisedProxyComponent) {
            if (promisedProxyComponent.typeElement().getTypeParameters().isEmpty()) {
                statement.add("new $T($L)", promisedProxyComponent.className(), dependenciesCode);
            } else {
                statement.add("new $T<>($L)", promisedProxyComponent.className(), dependenciesCode);
            }
        } else if (declaration instanceof ComponentDeclaration.OptionalComponent optional) {
            var optionalOf = ((DeclaredType) optional.type()).getTypeArguments().get(0);
            statement.add("$T.<$T>ofNullable($L)", Optional.class, optionalOf, dependenciesCode);
        } else {
            throw new RuntimeException("Unknown type " + declaration);
        }
        var resolvedDependencies = component.dependencies();
        statement.add(", $T.of(", List.class);
        var interceptorsFor = interceptors.interceptorsFor(component);
        for (int i = 0; i < interceptorsFor.size(); i++) {
            var interceptor = interceptorsFor.get(i);
            if (component.holderName().equals(interceptor.component().holderName())) {
                statement.add("$N", interceptor.component().fieldName());
            } else {
                statement.add("$N.$N", interceptor.component().holderName(), interceptor.component().fieldName());
            }
            if (i < interceptorsFor.size() - 1) {
                statement.add(", ");
            }
        }
        statement.add(")");
        for (var resolvedDependency : resolvedDependencies) {
            if (resolvedDependency instanceof ComponentDependency.AllOfDependency allOf) {
                if (allOf.claim().claimType() != DependencyClaim.DependencyClaimType.ALL_OF_PROMISE) {
                    var dependencies = GraphResolutionHelper.findDependenciesForAllOf(ctx, allOf.claim(), components);
                    for (var dependency : dependencies) {
                        if (component.holderName().equals(dependency.component().holderName())) {
                            statement.add(", $N", dependency.component().fieldName());
                        } else {
                            statement.add(", $N.$N", dependency.component().holderName(), dependency.component().fieldName());
                        }
                        if (allOf.claim().claimType() == DependencyClaim.DependencyClaimType.ALL_OF_VALUE) {
                            statement.add(".valueOf()");
                        }
                    }
                }
                continue;
            }
            if (resolvedDependency instanceof ComponentDependency.PromiseOfDependency || resolvedDependency instanceof ComponentDependency.PromisedProxyParameterDependency) {
                continue;
            }

            if (resolvedDependency instanceof ComponentDependency.SingleDependency dependency && dependency.component() != null) {
                if (component.holderName().equals(dependency.component().holderName())) {
                    statement.add(", $N", dependency.component().fieldName());
                } else {
                    statement.add(", $N.$N", dependency.component().holderName(), dependency.component().fieldName());
                }
                if (resolvedDependency instanceof ComponentDependency.ValueOfDependency) {
                    statement.add(".valueOf()");
                }
            }
        }
        statement.add(")");
        return statement.build();
    }

    private CodeBlock generateDependenciesCode(ResolvedComponent component, ClassName graphTypeName, List<ResolvedComponent> components) {
        var resolvedDependencies = component.dependencies();
        if (resolvedDependencies.isEmpty()) {
            return CodeBlock.of("");
        }
        var b = CodeBlock.builder();
        b.indent();
        b.add("\n");
        for (int i = 0, dependenciesSize = resolvedDependencies.size(); i < dependenciesSize; i++) {
            if (i > 0) b.add(",\n");
            var resolvedDependency = resolvedDependencies.get(i);
            b.add(resolvedDependency.write(this.ctx, graphTypeName, components));
        }
        b.unindent();
        b.add("\n");
        return b.build();
    }

    private JavaFile generateImpl(TypeElement classElement, List<TypeElement> modules) throws IOException {
        var typeMirror = classElement.asType();
        var packageElement = (PackageElement) classElement.getEnclosingElement();
        var className = "$" + classElement.getSimpleName().toString() + "Impl";
        var classBuilder = TypeSpec.classBuilder(className)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated).addMember("value", CodeBlock.of("$S", KoraAppProcessor.class.getCanonicalName())).build())
            .addOriginatingElement(classElement)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(typeMirror);

        for (int i = 0; i < modules.size(); i++) {
            var module = modules.get(i);
            classBuilder.addField(FieldSpec.builder(TypeName.get(module.asType()), "module" + i, Modifier.PUBLIC, Modifier.FINAL)
                .initializer("new $T(){}", module.asType())
                .build());
            classBuilder.addOriginatingElement(module);
        }
        for (var component : this.components) {
            classBuilder.addOriginatingElement(component);
        }


        return JavaFile.builder(packageElement.getQualifiedName().toString(), classBuilder.build())
            .build();
    }

    private void processAppParts(RoundEnvironment roundEnv) {
        roundEnv.getElementsAnnotatedWith(this.koraSubmoduleElement)
            .stream()
            .filter(e -> e.getKind().isInterface())
            .map(TypeElement.class::cast)
            .forEach(this.appParts::add);

        if (isKoraAppSubmoduleEnabled) {
            roundEnv.getElementsAnnotatedWith(this.koraAppElement)
                .stream()
                .filter(e -> e.getKind().isInterface())
                .map(TypeElement.class::cast)
                .forEach(this.appParts::add);
        }
    }

    private void generateAppParts() throws IOException {
        for (var appPart : this.appParts) {
            var packageElement = this.elements.getPackageOf(appPart);
            var b = TypeSpec.interfaceBuilder(appPart.getSimpleName() + "SubmoduleImpl")
                .addModifiers(Modifier.PUBLIC);
            var componentNumber = 0;
            for (var component : this.components) {
                b.addOriginatingElement(component);
                var constructor = this.findSinglePublicConstructor(component);
                if (constructor == null) {
                    return;
                }
                var mb = MethodSpec.methodBuilder("_component" + componentNumber++)
                    .returns(TypeName.get(component.asType()))
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);

                if (component.getTypeParameters().isEmpty()) {
                    mb.addCode("return new $T(", ClassName.get(component));
                } else {
                    for (var tp : component.getTypeParameters()) {
                        mb.addTypeVariable(TypeVariableName.get(tp));
                    }
                    mb.addCode("return new $T<>(", ClassName.get(component));
                }
                for (int i = 0; i < constructor.getParameters().size(); i++) {
                    var parameter = constructor.getParameters().get(i);
                    var pb = ParameterSpec.get(parameter).toBuilder();
                    var tag = TagUtils.parseTagValue(parameter);
                    if (!tag.isEmpty()) {
                        pb.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                    }
                    if (CommonUtils.isNullable(parameter)) {
                        pb.addAnnotation(CommonClassNames.nullable);
                    }
                    mb.addParameter(pb.build());
                    if (i > 0) {
                        mb.addCode(", ");
                    }
                    mb.addCode("$L", parameter);
                }
                var tag = TagUtils.parseTagValue(component);
                if (!tag.isEmpty()) {
                    mb.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                }
                var root = AnnotationUtils.isAnnotationPresent(component, CommonClassNames.root);
                if (root) {
                    mb.addAnnotation(CommonClassNames.root);
                }
                mb.addCode(");\n");
                b.addMethod(mb.build());
            }
            var moduleNumber = 0;
            for (var module : this.modules) {
                var moduleName = "_module" + moduleNumber++;
                var typeName = TypeName.get(module.asType());
                b.addField(FieldSpec.builder(typeName, moduleName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T(){}", typeName)
                    .build());
                for (var enclosedElement : module.getEnclosedElements()) {
                    if (enclosedElement.getKind() != ElementKind.METHOD) {
                        continue;
                    }
                    if (!enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                        continue;
                    }
                    var method = (ExecutableElement) enclosedElement;
                    var mb = MethodSpec.methodBuilder("_component" + componentNumber++)
                        .returns(TypeName.get(method.getReturnType()))
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);
                    for (var tp : method.getTypeParameters()) {
                        mb.addTypeVariable(TypeVariableName.get(tp));
                    }
                    mb.addCode("return $L.$L(", moduleName, method.getSimpleName());
                    for (int i = 0; i < method.getParameters().size(); i++) {
                        var parameter = method.getParameters().get(i);
                        var pb = ParameterSpec.get(parameter).toBuilder();
                        var tag = TagUtils.parseTagValue(parameter);
                        if (!tag.isEmpty()) {
                            pb.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                        }
                        if (CommonUtils.isNullable(parameter)) {
                            pb.addAnnotation(CommonClassNames.nullable);
                        }
                        mb.addParameter(pb.build());
                        if (i > 0) {
                            mb.addCode(", ");
                        }
                        mb.addCode("$L", parameter);
                    }
                    var tag = TagUtils.parseTagValue(method);
                    if (!tag.isEmpty()) {
                        mb.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                    }
                    if (AnnotationUtils.findAnnotation(method, CommonClassNames.defaultComponent) != null) {
                        mb.addAnnotation(CommonClassNames.defaultComponent);
                    }
                    var root = AnnotationUtils.isAnnotationPresent(method, CommonClassNames.root);
                    if (root) {
                        mb.addAnnotation(CommonClassNames.root);
                    }
                    mb.addCode(");\n");
                    b.addMethod(mb.build());
                }
            }

            var typeSpec = b.build();
            JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec)
                .build()
                .writeTo(this.processingEnv.getFiler());
        }
    }
}
