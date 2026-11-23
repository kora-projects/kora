package io.koraframework.http.server.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeSpec;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

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

    static List<ExecutableElement> collectMethods(Set<TypeElement> interfaces) {
        return interfaces.stream()
            .map(TypeElement::getEnclosedElements).flatMap(Collection::stream)
            .filter(t -> t.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(e -> !e.getModifiers().contains(Modifier.PRIVATE))
            .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
            .toList();
    }

    /**
     * Groups methods that override each other, the most specific declaration first
     */
    private List<List<ExecutableElement>> groupByOverride(TypeElement controller, List<ExecutableElement> methods) {
        var chains = new ArrayList<List<ExecutableElement>>();
        for (var method : methods) {
            var chain = chains.stream()
                .filter(c -> c.stream().anyMatch(m -> this.elements.overrides(m, method, controller) || this.elements.overrides(method, m, controller)))
                .findFirst()
                .orElse(null);
            if (chain == null) {
                chains.add(new ArrayList<>(List.of(method)));
                continue;
            }
            var index = IntStream.range(0, chain.size())
                .filter(i -> this.elements.overrides(method, chain.get(i), controller))
                .findFirst()
                .orElse(chain.size());
            chain.add(index, method);
        }
        return chains;
    }

    static Set<TypeElement> collectInterfaces(Types types, TypeElement typeElement) {
        var result = new LinkedHashSet<TypeElement>();
        collectInterfaces(types, result, typeElement);
        return result;
    }

    private static void collectInterfaces(Types types, Set<TypeElement> collectedElements, TypeElement typeElement) {
        if (collectedElements.add(typeElement)) {
            if (typeElement.asType().getKind() == TypeKind.ERROR) {
                throw new ProcessingErrorException("""
                    HTTP controller can't be processed:
                      %s

                    Problem:
                      Controller type or one of its parent types is unresolved.

                    Hint:
                      This usually means a superclass or implemented interface is missing from the compilation classpath or failed to compile.

                    Fix:
                      Make sure all controller parent types are available and compile successfully before HTTP server annotation processing runs.
                    """.formatted(typeElement), typeElement);
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
        var classBuilder = TypeSpec.interfaceBuilder(String.join("_", ClassName.get(controller).simpleNames()) + "Module")
            .addOriginatingElement(controller)
            .addAnnotation(AnnotationUtils.generated(ControllerModuleGenerator.class))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module);

        var error = false;
        var classes = collectInterfaces(this.types, controller);

        var generatedNames = new HashSet<String>();
        for (var overrideChain : groupByOverride(controller, collectMethods(classes))) {
            var routeMethod = overrideChain.stream()
                .filter(m -> AnnotationUtils.findAnnotation(m, HttpServerClassNames.httpRoute) != null)
                .findFirst();
            if (routeMethod.isEmpty()) {
                continue;
            }
            var method = HttpServerUtils.extract(this.elements, this.types, controller, routeMethod.get());
            if (method == null) {
                continue;
            }
            var generatedMethod = this.requestHandlerGenerator.generate(controller, method, overrideChain, generatedNames);
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
