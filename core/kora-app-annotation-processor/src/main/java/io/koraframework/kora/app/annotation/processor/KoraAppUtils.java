package io.koraframework.kora.app.annotation.processor;

import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.annotation.processor.common.TagUtils;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import io.koraframework.kora.app.annotation.processor.declaration.ModuleDeclaration;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;

public class KoraAppUtils {

    static List<ComponentDeclaration> parseComponents(ProcessingContext ctx, Collection<? extends ModuleDeclaration> modules) {
        var result = new ArrayList<ComponentDeclaration>();
        for (var module : modules) {
            var anInterface = module.element();
            for (var element : anInterface.getEnclosedElements()) {
                if (element.getKind() != ElementKind.METHOD) {
                    continue;
                }
                var executableElement = (ExecutableElement) element;
                if (executableElement.getModifiers().contains(Modifier.PRIVATE)) {
                    continue;
                }
                if (executableElement.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }
                if (AnnotationUtils.isAnnotationPresent(executableElement, CommonClassNames.factoryModule)) {
                    if (executableElement.getReturnType().getKind() != TypeKind.DECLARED) {
                        throw new ProcessingErrorException("@FactoryModule method must return a class type", executableElement);
                    }
                    result.add(ComponentDeclaration.fromModule(ctx, module, executableElement));
                    var returnTypeElement = (TypeElement) ctx.types.asElement(executableElement.getReturnType());
                    var methodTag = TagUtils.parseTagValue(executableElement);
                    var methodModule = new ModuleDeclaration.FactoryModule(returnTypeElement, methodTag);
                    parseModuleTypeComponents(ctx, methodModule, returnTypeElement, result);
                } else {
                    result.add(ComponentDeclaration.fromModule(ctx, module, executableElement));
                }
            }
        }
        var finalResult = new ArrayList<>(result);
        for (var component : result) {
            var executableElement = (ExecutableElement) component.source();
            if (executableElement.getAnnotation(Override.class) != null) {
                var overridee = findOverridee(ctx.types, ctx.elements, executableElement);
                assert overridee.size() > 0;
                finalResult.removeIf(cd -> {
                    for (var element : overridee) {
                        if (cd.source().equals(element)) {
                            return true;
                        }
                    }
                    return false;
                });
            }
        }
        return finalResult;
    }

    static List<ComponentDeclaration> parseClassModuleComponents(ProcessingContext ctx, ModuleDeclaration.ClassModule classModule) {
        var result = new ArrayList<ComponentDeclaration>();
        parseModuleTypeComponents(ctx, classModule, classModule.element(), result);
        return result;
    }

    private static void parseModuleTypeComponents(ProcessingContext ctx, ModuleDeclaration moduleDecl, TypeElement typeElement, List<ComponentDeclaration> result) {
        var seen = new LinkedHashMap<String, ExecutableElement>();
        var current = typeElement;
        while (current != null && !current.getQualifiedName().contentEquals("java.lang.Object")) {
            for (var enclosed : current.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.METHOD) continue;
                var method = (ExecutableElement) enclosed;
                if (!method.getModifiers().contains(Modifier.PUBLIC)) continue;
                if (method.getModifiers().contains(Modifier.STATIC)) continue;
                if (method.getModifiers().contains(Modifier.ABSTRACT)) continue;
                if (method.getReturnType().getKind() != TypeKind.DECLARED) continue;
                seen.putIfAbsent(methodSignature(ctx, method), method);
            }
            var superclass = current.getSuperclass();
            current = superclass.getKind() == TypeKind.DECLARED
                ? (TypeElement) ctx.types.asElement(superclass)
                : null;
        }
        for (var method : seen.values()) {
            result.add(ComponentDeclaration.fromModule(ctx, moduleDecl, method));
        }
    }

    private static String methodSignature(ProcessingContext ctx, ExecutableElement method) {
        var params = method.getParameters().stream()
            .map(p -> ctx.types.erasure(p.asType()).toString())
            .collect(java.util.stream.Collectors.joining(","));
        return method.getSimpleName() + "(" + params + ")";
    }

    private static ArrayList<ExecutableElement> findOverridee(Types types, Elements elements, ExecutableElement executableElement) {
        var typeElement = (TypeElement) executableElement.getEnclosingElement();
        var interfaces = collectInterfaces(types, typeElement);
        var result = new ArrayList<ExecutableElement>();
        for (var supertype : interfaces) {
            if (supertype == typeElement) {
                continue;
            }
            for (var enclosedElement : supertype.getEnclosedElements()) {
                if (enclosedElement.getKind() != ElementKind.METHOD) {
                    continue;
                }
                if (enclosedElement.getModifiers().contains(Modifier.STATIC) || enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                    continue;
                }
                if (!enclosedElement.getSimpleName().contentEquals(executableElement.getSimpleName())) {
                    continue;
                }
                var method = (ExecutableElement) enclosedElement;
                if (!types.isSubsignature((ExecutableType) executableElement.asType(), (ExecutableType) method.asType())) {
                    continue;
                }
                result.add(method);
            }
        }
        return result;
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
            for (var directlyImplementedInterface : typeElement.getInterfaces()) {
                var interfaceElement = (TypeElement) types.asElement(directlyImplementedInterface);
                collectInterfaces(types, collectedElements, interfaceElement);
            }
        }
    }

    public static List<TypeElement> findKoraSubmoduleModules(Elements elements, Set<TypeElement> interfaces, TypeElement koraAppElement, ProcessingEnvironment processingEnv) {
        var result = new ArrayList<TypeElement>();
        for (var typeElement : interfaces) {

            if (AnnotationUtils.isAnnotationPresent(typeElement, CommonClassNames.koraSubmodule)) {
                var name = typeElement.getQualifiedName().toString() + "SubmoduleImpl";
                var module = elements.getTypeElement(name);
                if (module == null) {
                    throw new ProcessingErrorException("Submodule `" + name + "` was not generated yet", typeElement);
                } else {
                    result.add(module);
                }
            }

            if (AnnotationUtils.isAnnotationPresent(typeElement, CommonClassNames.koraApp) && !typeElement.equals(koraAppElement)) {
                var name = typeElement.getQualifiedName().toString() + "SubmoduleImpl";
                var module = elements.getTypeElement(name);
                if (module != null) {
                    result.add(module);
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, "Expected @KoraApp as SubModule, but Submodule implementation not found for: " + typeElement
                        + "\nCheck that @KoraApp was generated with compile annotation processor option: -Akora.app.submodule.enabled=true", typeElement);
                }
            }
        }
        return result;
    }
}
