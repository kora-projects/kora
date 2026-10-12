package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import io.koraframework.kora.app.annotation.processor.declaration.ModuleDeclaration;
import io.koraframework.kora.app.annotation.processor.exception.DependencySourceFormatter;
import io.koraframework.kora.app.annotation.processor.interceptor.ComponentInterceptors;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedOptions("koraLogLevel")
@NullMarked
public class KoraAppProcessor extends AbstractKoraProcessor {

    public static final int COMPONENTS_PER_HOLDER_CLASS = 500;

    private static final Logger logger = LoggerFactory.getLogger(KoraAppProcessor.class);

    private final List<TypeElement> annotatedInterfaceModules = new ArrayList<>();
    private final List<TypeElement> annotatedClassModules = new ArrayList<>(); // @Disabled("Haven't decided whether to release it yet")
    private final List<TypeElement> components = new ArrayList<>();
    private final List<TypeElement> koraApps = new ArrayList<>();
    private final Set<String> pendingSubmoduleImpls = new HashSet<>();
    private boolean graphGenerated = false;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        logger.info("@KoraApp processor started");
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(CommonClassNames.koraApp, CommonClassNames.module, CommonClassNames.component, CommonClassNames.koraSubmodule);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var knownComponents = this.components.size();
        var knownModules = this.annotatedInterfaceModules.size();
        this.processModules(annotatedElements);
        this.processComponents(annotatedElements);
        this.processApps(annotatedElements);
        for (var annotated : annotatedElements.getOrDefault(CommonClassNames.koraSubmodule, List.of())) {
            if (annotated.element() instanceof TypeElement submodule) {
                this.pendingSubmoduleImpls.add(submodule.getQualifiedName() + "SubmoduleImpl");
            }
        }
        this.pendingSubmoduleImpls.removeIf(name -> this.elements.getTypeElement(name) != null);
        if (this.graphGenerated) {
            reportLateDeclarations(this.processingEnv, this.components.subList(knownComponents, this.components.size()));
            reportLateDeclarations(this.processingEnv, this.annotatedInterfaceModules.subList(knownModules, this.annotatedInterfaceModules.size()));
        }

        if (this.koraApps.isEmpty()) {
            return;
        }
        // javac warns about every source created in the processingOver() round and that warning cannot be suppressed (it breaks -Werror builds),
        // so the graph is written in the last round that can still produce sources; processingOver() stays as a fallback.
        // The graph needs every <Submodule>SubmoduleImpl of this compilation, so it waits until all of them have been generated
        if (roundEnv.processingOver() || (!roundEnv.errorRaised() && this.pendingSubmoduleImpls.isEmpty() && isLastGenerationRound(this.elements, roundEnv))) {
            if (this.elements.getTypeElement(CommonClassNames.koraApp.canonicalName()) == null) {
                return;
            }
            var ctx = new ProcessingContext(processingEnv);
            LogUtils.logElementsFull(logger, Level.DEBUG, "Processing elements", this.koraApps);

            for (var element : this.koraApps) {
                try {
                    var result = buildGraph(roundEnv, ctx, element);
                    this.write(element, ctx, result);
                } catch (ProcessingErrorException e) {
                    e.printError(this.processingEnv);
                } catch (IOException e) {
                    throw new IllegalStateException("Kora internal error: failed to write generated graph for @KoraApp " + element.getQualifiedName(), e);
                }
            }
            this.koraApps.clear();
            this.graphGenerated = true;
        }
    }

    /**
     * Annotations that only the Kora DI processors consume.
     * Other annotations of the same package are hooks for other processors (e.g. {@code @AopProxy} for Kafka publisher modules) and must postpone the graph.
     */
    private static final Set<String> DI_ONLY_ANNOTATIONS = Stream.of(
        CommonClassNames.component, CommonClassNames.module, CommonClassNames.koraApp, CommonClassNames.koraSubmodule,
        CommonClassNames.root, CommonClassNames.tag, CommonClassNames.defaultComponent, CommonClassNames.koraGenerated
    ).map(ClassName::canonicalName).collect(Collectors.toUnmodifiableSet());

    /**
     * The round is the last one that can contribute to the graph when its sources carry no annotation that another processor reacts to:
     * only java.lang, JSpecify and Kora DI annotations.
     * In such a round no processor writes new components, so writing the graph here leaves only the processingOver() round after it.
     */
    static boolean isLastGenerationRound(Elements elements, RoundEnvironment roundEnv) {
        for (var root : roundEnv.getRootElements()) {
            if (!hasOnlyDiAnnotations(elements, root)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOnlyDiAnnotations(Elements elements, Element element) {
        for (var annotation : elements.getAllAnnotationMirrors(element)) {
            var name = ((TypeElement) annotation.getAnnotationType().asElement()).getQualifiedName().toString();
            if (!name.startsWith("java.lang.") && !name.startsWith("org.jspecify.annotations.") && !DI_ONLY_ANNOTATIONS.contains(name)) {
                return false;
            }
        }
        if (element instanceof PackageElement || element instanceof ModuleElement) {
            return true;
        }
        var children = new ArrayList<Element>(element.getEnclosedElements());
        if (element instanceof ExecutableElement method) {
            children.addAll(method.getParameters());
        }
        if (element instanceof Parameterizable parameterizable) {
            children.addAll(parameterizable.getTypeParameters());
        }
        for (var child : children) {
            if (!hasOnlyDiAnnotations(elements, child)) {
                return false;
            }
        }
        return true;
    }

    static void reportLateDeclarations(ProcessingEnvironment env, List<TypeElement> declarations) {
        for (var declaration : declarations) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, """
                Component or module was generated after Kora had already written the application graph, so it is missing from the graph:
                  type: %s

                Fix:
                  - Report this as a Kora bug together with the annotation processors used in this module.
                """.formatted(declaration.getQualifiedName()).stripTrailing(), declaration);
        }
    }

    private void processApps(Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotated : annotatedElements.getOrDefault(CommonClassNames.koraApp, List.of())) {
            var element = annotated.element();
            if (element.getKind() == ElementKind.INTERFACE) {
                if (logger.isInfoEnabled()) {
                    logger.info("@KoraApp element found:\n{}", element.toString().indent(4));
                }
                this.koraApps.add((TypeElement) element);
            } else {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, """
                    @KoraApp can only be applied to interfaces.

                    Fix:
                      - Change this type to an interface.
                      - Move @KoraApp to an interface that declares root components and modules.
                    """.stripTrailing(), element);
            }
        }
    }

    private void processComponents(Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotated : annotatedElements.getOrDefault(CommonClassNames.component, List.of())) {
            var componentElement = annotated.element();
            if (componentElement.getKind() != ElementKind.CLASS) {
                continue;
            }
            if (componentElement.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }

            var typeElement = (TypeElement) componentElement;
            if (!CommonUtils.hasAopAnnotations(typeElement)) {
                this.components.add(typeElement);
            }
        }
    }

    private void processModules(Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotated : annotatedElements.getOrDefault(CommonClassNames.module, List.of())) {
            var kind = annotated.element().getKind();
            if (kind == ElementKind.INTERFACE) {
                var te = (TypeElement) annotated.element();
                for (var member : elements.getAllMembers(te)) {
                    if (member.getKind() == ElementKind.METHOD && member.getModifiers().contains(Modifier.DEFAULT)) {
                        var method = (ExecutableElement) member;
                        if (method.getReturnType().getKind() != TypeKind.DECLARED) {
                            messager.printMessage(Diagnostic.Kind.ERROR, """
                                Module method returns a non-reference type, so it cannot be used as a graph component:
                                  found: %s%s

                                Fix:
                                  - Return a class or interface type.
                                  - Wrap primitive values in a reference type.
                                """.formatted(DependencySourceFormatter.type(method.getReturnType()), DependencySourceFormatter.locationSection(method)).stripTrailing(), method);
                        }
                    }
                }
                this.annotatedInterfaceModules.add(te);
//            @Disabled("Haven't decided whether to release it yet")
//            } else if (kind == ElementKind.CLASS) {
//                var te = (TypeElement) annotated.element();
//                if (!te.getModifiers().contains(Modifier.ABSTRACT)) {
//                    this.annotatedClassModules.add(te);
//                }
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, """
                    @Module can only be applied to interfaces.

                    Fix:
                      - Change this type to an interface.
                      - Move module factory methods to an interface annotated with @Module.
                    """.stripTrailing(), annotated.element());
            }
        }
    }

    private ResolvedGraph buildGraph(RoundEnvironment roundEnv, ProcessingContext ctx, Element classElement) {
        if (classElement.getKind() != ElementKind.INTERFACE) {
            throw new ProcessingErrorException("""
                @KoraApp can only be applied to interfaces.

                Fix:
                  - Change this type to an interface.
                  - Move @KoraApp to an interface that declares root components and modules.
                """.stripTrailing(), classElement);
        }
        var type = (TypeElement) classElement;
        var interfaces = KoraAppUtils.collectInterfaces(this.types, type);
        if (logger.isTraceEnabled()) {
            logger.trace("Effective modules found:\n{}", Stream.concat(Stream.of(type), this.annotatedInterfaceModules.stream()).flatMap(t -> KoraAppUtils.collectInterfaces(this.types, t).stream())
                .map(Object::toString).sorted()
                .collect(Collectors.joining("\n")).indent(4));
        }
        var mixedInModuleComponents = KoraAppUtils.parseComponents(ctx, interfaces.stream().map(ModuleDeclaration.MixedInModule::new).toList());
        if (logger.isTraceEnabled()) {
            logger.trace("Effective methods of {}:\n{}", classElement, mixedInModuleComponents.stream().map(Object::toString).sorted().collect(Collectors.joining("\n")).indent(4));
        }
        var submodules = KoraAppUtils.findKoraSubmoduleModules(this.elements, interfaces, type, processingEnv);
        var discoveredModules = this.annotatedInterfaceModules.stream().flatMap(t -> KoraAppUtils.collectInterfaces(this.types, t).stream());
        var allModules = Stream.concat(discoveredModules, submodules.stream()).sorted(Comparator.comparing(Objects::toString)).toList();
        var annotatedModulesComponents = KoraAppUtils.parseComponents(ctx, allModules.stream().map(ModuleDeclaration.AnnotatedModule::new).toList());
        var allComponents = new ArrayList<ComponentDeclaration>(this.components.size() + mixedInModuleComponents.size() + annotatedModulesComponents.size());
        for (var component : this.components) {
            allComponents.add(ComponentDeclaration.fromAnnotated(ctx, component));
        }
        allComponents.addAll(mixedInModuleComponents);
        allComponents.addAll(annotatedModulesComponents);
        for (var factoryModule : this.annotatedClassModules) {
            var classModuleDecl = new ModuleDeclaration.ClassModule(factoryModule);
            allComponents.add(ComponentDeclaration.fromAnnotated(ctx, factoryModule));
            allComponents.addAll(KoraAppUtils.parseClassModuleComponents(ctx, classModuleDecl));
        }
        allComponents.sort(Comparator.comparing(Objects::toString));

        record Components(List<ComponentDeclaration> templates, List<ComponentDeclaration> nonTemplates) {}
        var components = allComponents.stream().collect(Collectors.teeing(
            Collectors.filtering(ComponentDeclaration::isTemplate, Collectors.toList()),
            Collectors.filtering(Predicate.not(ComponentDeclaration::isTemplate), Collectors.toCollection(ArrayList::new)),
            Components::new
        ));


        var graphBuilder = new GraphBuilder(ctx, roundEnv, type, allModules, components.nonTemplates, components.templates);

        return graphBuilder.build();
    }

    private void write(TypeElement type, ProcessingContext ctx, ResolvedGraph ok) throws IOException {
        var interceptors = ComponentInterceptors.parseInterceptors(ctx, ok.components());

        var applicationImplFile = this.generateImpl(type, ok.allModules());
        var applicationGraphFile = new GraphFileGenerator(ctx, type, ok.allModules(), interceptors, ok.components(), ok.conditionByTag())
            .generate();

        applicationImplFile.writeTo(this.processingEnv.getFiler());
        applicationGraphFile.writeTo(this.processingEnv.getFiler());
    }


    private JavaFile generateImpl(TypeElement classElement, List<TypeElement> modules) throws IOException {
        var typeMirror = classElement.asType();
        var packageElement = (PackageElement) classElement.getEnclosingElement();
        var className = "$" + classElement.getSimpleName().toString() + "Impl";
        var classBuilder = TypeSpec.classBuilder(className)
            .addAnnotation(AnnotationUtils.generated(KoraAppProcessor.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(typeMirror);

        for (int i = 0; i < modules.size(); i++) {
            var module = modules.get(i);
            classBuilder.addField(FieldSpec.builder(TypeName.get(module.asType()), "module" + i, Modifier.PUBLIC, Modifier.FINAL)
                .initializer("new $T(){}", module.asType())
                .build());
        }

        return JavaFile.builder(packageElement.getQualifiedName().toString(), classBuilder.build())
            .build();
    }

}
