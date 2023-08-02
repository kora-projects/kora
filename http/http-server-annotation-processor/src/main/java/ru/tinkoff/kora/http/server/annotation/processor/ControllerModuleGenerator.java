package ru.tinkoff.kora.http.server.annotation.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.Nullable;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ControllerModuleGenerator {
    private final Types types;
    private final Elements elements;
    private final RoundEnvironment roundEnv;
    private final RequestHandlerGenerator requestHandlerGenerator;

    public ControllerModuleGenerator(Types types, Elements elements, RoundEnvironment roundEnv, RequestHandlerGenerator requestHandlerGenerator) {
        this.types = types;
        this.elements = elements;
        this.roundEnv = roundEnv;
        this.requestHandlerGenerator = requestHandlerGenerator;
    }

    static Set<ExecutableElement> collectMethods(Set<TypeElement> interfaces) {
        return interfaces.stream()
            .map(TypeElement::getEnclosedElements).flatMap(Collection::stream)
            .filter(t -> t.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(e -> !e.getModifiers().contains(Modifier.PRIVATE))
            .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
            .collect(Collectors.toSet());
    }

    static Set<TypeElement> collectInterfaces(Types types, TypeElement typeElement) {
        var result = new HashSet<TypeElement>();
        collectInterfaces(types, result, typeElement);
        return result;
    }

    private static void collectInterfaces(Types types, Set<TypeElement> collectedElements, TypeElement typeElement) {
        if (collectedElements.add(typeElement)) {
            if (typeElement.asType().getKind() == TypeKind.ERROR) {
                throw new ProcessingErrorException("Element is error: %s".formatted(typeElement.toString()), typeElement);
            }
            if (typeElement.getKind() == ElementKind.CLASS && typeElement.getSuperclass() != null && !typeElement.getSuperclass().toString().equals("java.lang.Object")) {
                var parentElement = (TypeElement) types.asElement(typeElement.getSuperclass());
                collectInterfaces(types, collectedElements, parentElement);
            }
            for (var directlyImplementedInterface : typeElement.getInterfaces()) {
                var interfaceElement = (TypeElement) types.asElement(directlyImplementedInterface);
                collectInterfaces(types, collectedElements, interfaceElement);
            }
        }
    }

    @Nullable
    public JavaFile generateController(TypeElement controller) {
        var classBuilder = TypeSpec.interfaceBuilder(controller.getSimpleName().toString() + "Module")
            .addOriginatingElement(controller)
            .addModifiers(Modifier.PUBLIC);
        classBuilder.addAnnotation(AnnotationSpec.builder(CommonClassNames.module).build());
        var error = false;
        var classes = collectInterfaces(this.types, controller);

        var methods = collectMethods(classes)
            .stream()
            .map(m -> HttpServerUtils.extract(this.elements, this.types, controller, m))
            .filter(Objects::nonNull)
            .toList();

        for (var method : methods) {
            var generatedMethod = this.requestHandlerGenerator.generate(controller, method);
            if (generatedMethod != null) {
                classBuilder.addMethod(generatedMethod);
            } else {
                error = true;
            }
        }

        if (error) {
            return null;
        }

        var packageName = this.elements.getPackageOf(controller);
        return JavaFile.builder(packageName.getQualifiedName().toString(), classBuilder.build()).build();
    }
}
