package ru.tinkoff.kora.kora.app.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.KoraSubmodule;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ModuleDeclaration;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

public class KoraAppUtils {
    static List<ComponentDeclaration> parseComponents(Types types, Elements elements, Collection<? extends ModuleDeclaration> modules) {
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
                result.add(ComponentDeclaration.fromModule(module, executableElement));
            }
        }
        var finalResult = new ArrayList<>(result);
        for (var component : result) {
            var executableElement = (ExecutableElement) component.source();
            if (executableElement.getAnnotation(Override.class) != null) {
                var overridee = findOverridee(types, elements, executableElement);
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

    public static List<TypeElement> findKoraSubmoduleModules(Elements elements, Set<TypeElement> interfaces) {
        var result = new ArrayList<TypeElement>();
        for (var typeElement : interfaces) {
            if (typeElement.getAnnotation(KoraSubmodule.class) != null) {
                var name = typeElement.getQualifiedName().toString() + "SubmoduleImpl";
                var module = elements.getTypeElement(name);
                if (module == null) {
                    throw new ProcessingErrorException("Submodule `" + name + "` was not generated yet", typeElement);
                } else {
                    result.add(module);
                }
            }
        }
        return result;
    }
}
