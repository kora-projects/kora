package ru.tinkoff.kora.kora.app.annotation.processor;

import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ModuleDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.exception.NewRoundException;
import ru.tinkoff.kora.kora.app.annotation.processor.interceptor.ComponentInterceptors;

import javax.annotation.Nullable;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedOptions("koraLogLevel")
public class KoraAppProcessor extends AbstractKoraProcessor {
    private static final Logger log = LoggerFactory.getLogger(KoraAppProcessor.class);
    private final Map<TypeElement, ProcessingState> annotatedElements = new HashMap<>();
    private final List<TypeElement> modules = new ArrayList<>();
    private final List<TypeElement> components = new ArrayList<>();
    private final ArrayList<TypeElement> appParts = new ArrayList<>();
    private volatile boolean initialized = false;
    private TypeElement koraAppElement;
    private TypeElement moduleElement;
    private TypeElement koraSubmoduleElement;
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
        this.initialized = true;
        this.ctx = new ProcessingContext(processingEnv);
        log.info("Starting kora processor");
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        if (!this.initialized) {
            return Set.of();
        }
        return Set.of(CommonClassNames.koraApp.canonicalName(), CommonClassNames.module.canonicalName(), CommonClassNames.component.canonicalName(), CommonClassNames.koraSubmodule.canonicalName(), CommonClassNames.koraGenerated.canonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
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
                log.info("Kora app found: {}", element);
                this.annotatedElements.putIfAbsent((TypeElement) element, parseNone(element));
            } else {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@KoraApp can be placed only on interfaces", element);
            }
        }

        var results = new HashMap<TypeElement, ProcessingState>();
        if (!roundEnv.processingOver()) for (var annotatedClass : this.annotatedElements.entrySet()) {
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
                log.info("Processing exception", e);
                results.put(annotatedClass.getKey(), new ProcessingState.Failed(e));
            } catch (Exception e) {
                if (e instanceof FilerException || e.getCause() instanceof FilerException) {
                    this.processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, e.getMessage());
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
        this.annotatedElements.putAll(results);
        if (roundEnv.processingOver()) {
            for (var element : this.annotatedElements.entrySet()) {
                var processingResult = element.getValue();
                if (processingResult instanceof ProcessingState.NewRoundRequired newRound) {
                    this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Component was expected to be generated by extension %s but was not: %s/%s".formatted(newRound.source(), newRound.type(), newRound.tag())
                    );
                }
                if (processingResult instanceof ProcessingState.Failed failed) {
                    failed.detailedException().printError(this.processingEnv);
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
        log.info("Generated from prev round:\n{}", roundEnv.getElementsAnnotatedWith(ru.tinkoff.kora.common.annotation.Generated.class)
            .stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n"))
            .indent(4)
        );
    }

    private boolean processComponents(RoundEnvironment roundEnv) {
        var componentOfElements = roundEnv.getElementsAnnotatedWith(ru.tinkoff.kora.common.Component.class);
        for (var componentElement : componentOfElements) {
            if (componentElement.getKind() != ElementKind.CLASS) {
                continue;
            }
            var typeElement = (TypeElement) componentElement;
            if (CommonUtils.hasAopAnnotations(typeElement)) {
                log.info("Component found, waiting for aop proxy: {}", typeElement);
            } else {
                log.info("Component found: {}", typeElement);
                this.components.add(typeElement);
            }
        }
        return !componentOfElements.isEmpty();
    }

    private boolean processModules(RoundEnvironment roundEnv) {
        var moduleOfElements = roundEnv.getElementsAnnotatedWith(this.moduleElement);
        for (var moduleElement : moduleOfElements) {
            if (moduleElement.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            log.info("Module found: {}", moduleElement);
            this.modules.add((TypeElement) moduleElement);
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
            return new ProcessingState.Failed(new ProcessingErrorException("@KoraApp is only applicable to interfaces", classElement));
        }
        try {
            var type = (TypeElement) classElement;
            var interfaces = KoraAppUtils.collectInterfaces(this.types, type);
            if (log.isInfoEnabled()) {
                log.info("Effective modules of {}:\n{}", classElement, Stream.concat(interfaces.stream(), this.modules.stream()).map(Object::toString).sorted().collect(Collectors.joining("\n")).indent(4));
            }
            var mixedInModuleComponents = KoraAppUtils.parseComponents(this.types, this.elements, interfaces.stream().map(ModuleDeclaration.MixedInModule::new).toList());
            if (log.isDebugEnabled()) {
                log.info("Effective methods of {}:\n{}", classElement, mixedInModuleComponents.stream().map(Object::toString).sorted().collect(Collectors.joining("\n")).indent(4));
            }
            var submodules = KoraAppUtils.findKoraSubmoduleModules(this.elements, interfaces);
            var allModules = Stream.concat(this.modules.stream(), submodules.stream()).sorted(Comparator.comparing(Objects::toString)).toList();
            var annotatedModulesComponents = KoraAppUtils.parseComponents(this.types, this.elements, allModules.stream().map(ModuleDeclaration.AnnotatedModule::new).toList());
            var allComponents = new ArrayList<ComponentDeclaration>(this.components.size() + mixedInModuleComponents.size() + annotatedModulesComponents.size());
            for (var component : this.components) {
                allComponents.add(ComponentDeclaration.fromAnnotated(component));
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
                .filter(cd -> this.ctx.serviceTypeHelper.isLifecycle(cd.type()))
                .toList();
            return new ProcessingState.None(type, allModules, sourceDescriptors, components.templates, rootSet);
        } catch (ProcessingErrorException e) {
            return new ProcessingState.Failed(e);
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


    private JavaFile generateApplicationGraph(Element classElement, List<TypeElement> allModules, ComponentInterceptors interceptors, List<ResolvedComponent> components) throws IOException {
        var graphDraw = this.elements.getTypeElement(CommonClassNames.applicationGraphDraw.canonicalName());
        var packageElement = (PackageElement) classElement.getEnclosingElement();
        var implClass = ClassName.get(packageElement.getQualifiedName().toString(), classElement.getSimpleName().toString() + "Impl");

        var classBuilder = TypeSpec.classBuilder(classElement.getSimpleName().toString() + "Graph")
            .addOriginatingElement(classElement)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Supplier.class), TypeName.get(graphDraw.asType())))
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Function.class), implClass, TypeName.get(graphDraw.asType())))
            .addMethod(MethodSpec
                .methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(CommonClassNames.applicationGraphDraw)
                .addStatement("return $L.graph(new $T())", classElement.getSimpleName().toString() + "Graph", implClass)
                .build())
            .addMethod(MethodSpec
                .methodBuilder("apply")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(implClass, "impl")
                .returns(CommonClassNames.applicationGraphDraw)
                .addStatement("return $L.graph(impl)", classElement.getSimpleName().toString() + "Graph")
                .build());

        for (var component : this.components) {
            classBuilder.addOriginatingElement(component);
        }
        for (var module : this.modules) {
            classBuilder.addOriginatingElement(module);
        }
        var functionMethodBuilder = MethodSpec.methodBuilder("graph")
            .addParameter(implClass, "impl")
            .returns(CommonClassNames.applicationGraphDraw)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addStatement("var graphDraw = new $T($T.class)", CommonClassNames.applicationGraphDraw, classElement);
        var promisedComponents = new TreeSet<Integer>();
        for (var component : components) {
            for (var dependency : component.dependencies()) {
                if (dependency instanceof ComponentDependency.PromiseOfDependency pod && pod.component() != null) {
                    promisedComponents.add(pod.component().index());
                }
                if (dependency instanceof ComponentDependency.PromisedProxyParameterDependency pod) {
                    var d = GraphResolutionHelper.findDependency(ctx, pod.declaration(), components, pod.claim());
                    promisedComponents.add(d.component().index());
                }
                if (dependency instanceof ComponentDependency.AllOfDependency allOf && allOf.claim().claimType().equals(DependencyClaim.DependencyClaimType.ALL_OF_PROMISE)) {
                    for (var d : GraphResolutionHelper.findDependenciesForAllOf(ctx, dependency.claim(), components)) {
                        promisedComponents.add(d.component().index());
                    }
                }
            }
        }
        for (var promisedComponent : promisedComponents) {
            functionMethodBuilder.addStatement("var component$L = new $T<$T<$T>>()", promisedComponent, AtomicReference.class, CommonClassNames.node, components.get(promisedComponent).type());
        }
        functionMethodBuilder.addCode("\n");


        for (var component : components) {
            var statement = this.generateComponentStatement(allModules, interceptors, components, promisedComponents, component);

            functionMethodBuilder.addStatement(statement);
        }
        functionMethodBuilder.addStatement("return graphDraw");
        var supplierMethodBuilder = MethodSpec.methodBuilder("graph")
            .returns(CommonClassNames.applicationGraphDraw)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addStatement("var impl = new $L()", implClass)
            .addStatement("return $L.graph(impl)", classElement.getSimpleName().toString() + "Graph");


        return JavaFile.builder(packageElement.getQualifiedName().toString(), classBuilder
                .addMethod(functionMethodBuilder.build())
                .addMethod(supplierMethodBuilder.build())
                .build())
            .build();
    }

    private CodeBlock generateComponentStatement(List<TypeElement> allModules, ComponentInterceptors interceptors, List<ResolvedComponent> components, Set<Integer> promisedComponents, ResolvedComponent component) {
        var statement = CodeBlock.builder();
        var declaration = component.declaration();
        var isPromised = promisedComponents.contains(component.index());
        if (isPromised) {
            statement.add("$L.set(graphDraw.addNode0(", component.name());
        } else {
            statement.add("var $L = graphDraw.addNode0(", component.name());
        }
        statement.add("new Class<?>[]{");
        for (var tag : component.tags()) {
            statement.add("$L.class, ", tag);
        }
        statement.add("}, g -> ");

        if (declaration instanceof ComponentDeclaration.AnnotatedComponent annotatedComponent) {
            statement.add("new $T", annotatedComponent.typeElement());
            if (!annotatedComponent.typeVariables().isEmpty()) {
                statement.add("<");
                for (int i = 0; i < annotatedComponent.typeVariables().size(); i++) {
                    if (i > 0) statement.add(", ");
                    statement.add("$T", annotatedComponent.typeVariables().get(i));
                }
                statement.add(">");
            }
            statement.add("(");
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
            statement.add("$L(", moduleComponent.method().getSimpleName());
        } else if (declaration instanceof ComponentDeclaration.FromExtensionComponent extension) {
            var elem = extension.sourceMethod();
            if (elem.getKind() == ElementKind.CONSTRUCTOR) {
                TypeElement typeElement = (TypeElement) elem.getEnclosingElement();
                var builderStr = typeElement.getTypeParameters().isEmpty() ? "new $T(" : "new $T<>(";
                var tpe = types.erasure(typeElement.asType());
                statement.add(builderStr, tpe);
            } else if (elem.getModifiers().contains(Modifier.STATIC)) {
                TypeElement typeElement = (TypeElement) elem.getEnclosingElement();
                var tpe = types.erasure(typeElement.asType());
                statement.add("$T.$L(", tpe, elem.getSimpleName());
            } else {
                throw new ProcessingErrorException("Result of extension processing should be constructor or static function", elem);
            }
        } else if (declaration instanceof ComponentDeclaration.DiscoveredAsDependencyComponent asDependencyComponent) {
            statement.add("new $T", ClassName.get(asDependencyComponent.typeElement()));
            if (!asDependencyComponent.typeElement().getTypeParameters().isEmpty()) {
                statement.add("<>");
            }
            statement.add("(");
        } else if (declaration instanceof ComponentDeclaration.PromisedProxyComponent promisedProxyComponent) {
            statement.add("new $T", promisedProxyComponent.className());
            if (!promisedProxyComponent.typeElement().getTypeParameters().isEmpty()) {
                statement.add("<>");
            }
            statement.add("(");
        } else if (declaration instanceof ComponentDeclaration.OptionalComponent optional) {
            statement.add("$T", Optional.class);
            statement.add(".<$T>ofNullable(", ((DeclaredType) optional.type()).getTypeArguments().get(0));
        } else {
            throw new RuntimeException("Unknown type " + declaration);
        }
        var resolvedDependencies = component.dependencies();
        if (!resolvedDependencies.isEmpty()) {
            statement.indent();
            statement.add("\n");
        }
        for (int i = 0, dependenciesSize = resolvedDependencies.size(); i < dependenciesSize; i++) {
            if (i > 0) statement.add(",\n");
            var resolvedDependency = resolvedDependencies.get(i);
            statement.add(resolvedDependency.write(this.ctx, components, promisedComponents));
        }
        if (!resolvedDependencies.isEmpty()) {
            statement.unindent();
            statement.add("\n");
        }
        statement.add(")");
        statement.add(", $T.of(", List.class);
        var interceptorsFor = interceptors.interceptorsFor(component);
        for (int i = 0; i < interceptorsFor.size(); i++) {
            var interceptor = interceptorsFor.get(i);
            statement.add("$L", interceptor.component().name());
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
                        statement.add(", $L", dependency.component().name());
                        if (promisedComponents.contains(dependency.component().index())) {
                            statement.add(".get()");
                        }
                        if (allOf.claim().claimType() == DependencyClaim.DependencyClaimType.ALL_OF_VALUE) {
                            statement.add(".valueOf()");
                        }
                    }
                }
                continue;
            }
            if (resolvedDependency instanceof ComponentDependency.PromiseOfDependency
                || resolvedDependency instanceof ComponentDependency.OptionalDependency od && od.delegate() instanceof ComponentDependency.PromiseOfDependency
                || resolvedDependency instanceof ComponentDependency.PromisedProxyParameterDependency
            ) {
                continue;
            }

            if (resolvedDependency instanceof ComponentDependency.SingleDependency dependency && dependency.component() != null) {
                statement.add(", $L", dependency.component().name());
                if (promisedComponents.contains(dependency.component().index())) {
                    statement.add(".get()");
                }
                if (resolvedDependency instanceof ComponentDependency.ValueOfDependency || resolvedDependency instanceof ComponentDependency.OptionalDependency od && od.delegate() instanceof ComponentDependency.ValueOfDependency) {
                    statement.add(".valueOf()");
                }
            }
        }
        statement.add(")");
        if (isPromised) {
            statement.add(")");
        }
        return statement.build();
    }

    private JavaFile generateImpl(TypeElement classElement, List<TypeElement> modules) throws IOException {
        var typeMirror = classElement.asType();
        var className = classElement.getSimpleName().toString() + "Impl";
        var packageElement = (PackageElement) classElement.getEnclosingElement();
        var classBuilder = TypeSpec.classBuilder(className)
            .addOriginatingElement(classElement)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(typeMirror);

        for (int i = 0; i < modules.size(); i++) {
            var module = modules.get(i);
            classBuilder.addField(FieldSpec.builder(TypeName.get(module.asType()), "module" + i, Modifier.PUBLIC)
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
    }

    private void generateAppParts() throws IOException {
        for (var appPart : this.appParts) {
            var packageElement = this.elements.getPackageOf(appPart);
            var b = TypeSpec.interfaceBuilder(appPart.getSimpleName() + "SubmoduleImpl")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(appPart.asType());
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
                mb.addCode("return new $T(", component);
                for (int i = 0; i < constructor.getParameters().size(); i++) {
                    var parameter = constructor.getParameters().get(i);
                    mb.addParameter(ParameterSpec.get(parameter));
                    if (i > 0) {
                        mb.addCode(", ");
                    }
                    mb.addCode("$L", parameter);
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
                    mb.addCode("return $L.$L(", moduleName, method.getSimpleName());
                    for (int i = 0; i < method.getParameters().size(); i++) {
                        var parameter = method.getParameters().get(i);
                        mb.addParameter(ParameterSpec.get(parameter));
                        if (i > 0) {
                            mb.addCode(", ");
                        }
                        mb.addCode("$L", parameter);
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
