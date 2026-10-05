package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.kora.app.annotation.processor.exception.DependencySourceFormatter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class KoraSubmoduleProcessor extends AbstractKoraProcessor {
    private static final String OPTION_SUBMODULE_GENERATION = "kora.app.submodule.enabled";
    private final List<TypeElement> appParts = new ArrayList<>();
    private final List<TypeElement> modules = new ArrayList<>();
    private final List<TypeElement> components = new ArrayList<>();

    private volatile boolean isKoraAppSubmoduleEnabled = false;

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(OPTION_SUBMODULE_GENERATION);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.isKoraAppSubmoduleEnabled = Boolean.parseBoolean(processingEnv.getOptions().getOrDefault(OPTION_SUBMODULE_GENERATION, "false"));
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(CommonClassNames.koraSubmodule, CommonClassNames.koraApp, CommonClassNames.component, CommonClassNames.module);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        this.processAppParts(annotatedElements);
        this.processModules(annotatedElements);
        this.processComponents(annotatedElements);


        if (roundEnv.processingOver() && !roundEnv.errorRaised()) {
            try {
                this.generateAppParts();
            } catch (IOException e) {
                throw new IllegalStateException("Kora internal error: failed to write generated @KoraSubmodule implementation", e);
            }
        }
    }


    private void processModules(Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var moduleElements = annotatedElements.getOrDefault(CommonClassNames.module, List.of());
        for (var annotated : moduleElements) {
            if (annotated.element().getKind() != ElementKind.INTERFACE) {
                continue;
            }
            var te = (TypeElement) annotated.element();
            this.modules.add(te);
        }
    }

    private void processComponents(Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var componentOfElements = annotatedElements.getOrDefault(CommonClassNames.component, List.of());

        for (var annotated : componentOfElements) {
            var componentElement = annotated.element();
            if (componentElement.getKind() != ElementKind.CLASS && componentElement.getKind() != ElementKind.RECORD) {
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

    private void processAppParts(Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        annotatedElements.getOrDefault(CommonClassNames.koraSubmodule, List.of())
            .stream()
            .map(AnnotatedElement::element)
            .filter(e -> e.getKind().isInterface())
            .map(TypeElement.class::cast)
            .forEach(this.appParts::add);

        if (isKoraAppSubmoduleEnabled) {
            annotatedElements.getOrDefault(CommonClassNames.koraApp, List.of())
                .stream()
                .map(AnnotatedElement::element)
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
                var constructor = this.findSinglePublicConstructor(component);
                if (constructor == null) {
                    return;
                }
                // generated AOP proxy is registered with the type it proxies, same as KoraAppProcessor does for @Component classes
                var componentType = AnnotationUtils.isAnnotationPresent(component, CommonClassNames.aopProxy)
                    ? component.getSuperclass()
                    : component.asType();
                var mb = MethodSpec.methodBuilder("_component" + componentNumber++)
                    .returns(TypeName.get(componentType))
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);
                for (var thrownType : constructor.getThrownTypes()) {
                    mb.addException(TypeName.get(thrownType));
                }

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
                    var type = TypeName.get(parameter.asType());
                    var name = parameter.getSimpleName().toString();
                    if (CommonUtils.isNullable(parameter)) {
                        type = type.annotated(CommonClassNames.nullableAnnotation);
                    }
                    var pb = ParameterSpec.builder(type, name)
                        .addModifiers(parameter.getModifiers());
                    var tag = TagUtils.parseTagValue(parameter);
                    if (tag != null) {
                        pb.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                    }
                    mb.addParameter(pb.build());
                    if (i > 0) {
                        mb.addCode(", ");
                    }
                    mb.addCode("$L", parameter);
                }
                var tag = TagUtils.parseTagValue(component);
                if (tag != null) {
                    mb.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                }
                var root = AnnotationUtils.isAnnotationPresent(component, CommonClassNames.root);
                if (root) {
                    mb.addAnnotation(CommonClassNames.root);
                }
                this.copyAnnotations(component, mb, CommonClassNames.defaultComponent, CommonClassNames.conditional);
                mb.addCode(");\n");
                b.addMethod(mb.build());
            }
            var moduleNumber = 0;
            var moduleNames = this.modules.stream().map(m -> m.getQualifiedName().toString()).collect(Collectors.toSet());
            for (var module : this.modules) {
                var moduleName = "_module" + moduleNumber++;
                var typeName = TypeName.get(module.asType());
                b.addField(FieldSpec.builder(typeName, moduleName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T(){}", typeName)
                    .build());
                for (var enclosedElement : this.elements.getAllMembers(module)) {
                    if (enclosedElement.getKind() != ElementKind.METHOD) {
                        continue;
                    }
                    if (!enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                        continue;
                    }
                    var method = (ExecutableElement) enclosedElement;
                    var declaringType = ((TypeElement) method.getEnclosingElement()).getQualifiedName();
                    if (!declaringType.equals(module.getQualifiedName()) && moduleNames.contains(declaringType.toString())) {
                        // that @Module is processed on its own, emitting its factories here would duplicate them
                        continue;
                    }
                    var methodType = (ExecutableType) this.types.asMemberOf((DeclaredType) module.asType(), method);
                    var mb = MethodSpec.methodBuilder("_component" + componentNumber++)
                        .returns(TypeName.get(methodType.getReturnType()))
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);
                    for (var thrownType : methodType.getThrownTypes()) {
                        mb.addException(TypeName.get(thrownType));
                    }
                    for (var tp : method.getTypeParameters()) {
                        mb.addTypeVariable(TypeVariableName.get(tp));
                    }
                    mb.addCode("return $L.$L(", moduleName, method.getSimpleName());
                    for (int i = 0; i < method.getParameters().size(); i++) {
                        var parameter = method.getParameters().get(i);
                        var type = TypeName.get(methodType.getParameterTypes().get(i));
                        var name = parameter.getSimpleName().toString();
                        if (CommonUtils.isNullable(parameter)) {
                            type = type.annotated(CommonClassNames.nullableAnnotation);
                        }
                        var pb = ParameterSpec.builder(type, name)
                            .addModifiers(parameter.getModifiers());
                        var tag = TagUtils.parseTagValue(parameter);
                        if (tag != null) {
                            pb.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                        }
                        mb.addParameter(pb.build());
                        if (i > 0) {
                            mb.addCode(", ");
                        }
                        mb.addCode("$L", parameter);
                    }
                    var tag = TagUtils.parseTagValue(method);
                    if (tag != null) {
                        mb.addAnnotation(TagUtils.makeAnnotationSpec(tag));
                    }
                    var root = AnnotationUtils.isAnnotationPresent(method, CommonClassNames.root);
                    if (root) {
                        mb.addAnnotation(CommonClassNames.root);
                    }
                    this.copyAnnotations(method, mb, CommonClassNames.defaultComponent, CommonClassNames.conditional, CommonClassNames.factoryModule);
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

    private void copyAnnotations(Element source, MethodSpec.Builder target, ClassName... annotations) {
        for (var annotation : annotations) {
            var mirror = AnnotationUtils.findAnnotation(source, annotation);
            if (mirror != null) {
                target.addAnnotation(AnnotationSpec.get(mirror));
            }
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
                """
                    @Component type has no public constructors:
                      class: %s

                    Fix:
                      - Add one public constructor.
                      - Move construction to a module method if constructor cannot be public.
                    """.formatted(element.getQualifiedName()).stripTrailing(), element
            );
        }
        if (constructors.size() > 1) {
            throw new ProcessingErrorException(
                """
                    @Component type has more than one public constructor:
                      class: %s
                      found: %s

                    Fix:
                      - Keep exactly one public constructor.
                      - Make extra constructors non-public.
                    """.formatted(element.getQualifiedName(), DependencySourceFormatter.constructors(constructors)).stripTrailing(), element
            );
        }
        return constructors.get(0);
    }
}
