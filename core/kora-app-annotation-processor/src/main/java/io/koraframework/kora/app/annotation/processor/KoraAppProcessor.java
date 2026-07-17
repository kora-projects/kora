package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import io.koraframework.kora.app.annotation.processor.declaration.ModuleDeclaration;
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
        this.processModules(annotatedElements);
        this.processComponents(annotatedElements);
        this.processApps(annotatedElements);

        if (roundEnv.processingOver()) {
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
                    throw new UncheckedIOException(e);
                }
            }
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
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@KoraApp can be placed only on interfaces", element);
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
                            messager.printMessage(Diagnostic.Kind.ERROR, "Only reference types are allowed as graph components");
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
                messager.printMessage(Diagnostic.Kind.ERROR, "Only interfaces are allowed as modules");
            }
        }
    }

    private ResolvedGraph buildGraph(RoundEnvironment roundEnv, ProcessingContext ctx, Element classElement) {
        if (classElement.getKind() != ElementKind.INTERFACE) {
            throw new ProcessingErrorException("@KoraApp is only applicable to interfaces", classElement);
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
