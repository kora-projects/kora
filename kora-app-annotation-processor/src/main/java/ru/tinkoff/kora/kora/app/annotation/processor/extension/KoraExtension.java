package ru.tinkoff.kora.kora.app.annotation.processor.extension;

import com.squareup.javapoet.ClassName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.Set;

public interface KoraExtension {
    @Nullable
    KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags);

    interface KoraExtensionDependencyGenerator {
        ExtensionResult generateDependency() throws IOException;

        static KoraExtensionDependencyGenerator generatedFrom(Elements elements, Element element, ClassName postfix) {
            return generatedFrom(elements, element, postfix.simpleName());
        }

        static KoraExtensionDependencyGenerator generatedFrom(Elements elements, Element element, String postfix) {
            var generatedName = NameUtils.generatedType(element, postfix);
            return generatedFromWithName(elements, element, generatedName);
        }

        static KoraExtensionDependencyGenerator generatedFromWithName(Elements elements, Element element, String name) {
            var packageElement = elements.getPackageOf(element);

            return () -> {
                var maybeGenerated = elements.getTypeElement(packageElement.getQualifiedName() + "." + name);
                if (maybeGenerated == null) {
                    throw new ProcessingErrorException("Class %s was expected to be generated from element by annotation processor but was not".formatted(packageElement.getQualifiedName() + "." + name), element);
                }
                if (!CommonUtils.hasAopAnnotations(maybeGenerated)) {
                    var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                    if (constructors.size() != 1) throw new IllegalStateException();
                    return ExtensionResult.fromExecutable(constructors.get(0));
                }
                var aopProxy = NameUtils.generatedType(maybeGenerated, "_AopProxy");
                var aopProxyElement = elements.getTypeElement(packageElement.getQualifiedName() + "." + aopProxy);
                if (aopProxyElement == null) {
                    // aop annotation processor will handle it
                    throw new ProcessingErrorException("Class %s was expected to be generated from element by aop annotation processor but was not".formatted(packageElement.getQualifiedName() + "." + name), maybeGenerated);
                }
                var constructors = CommonUtils.findConstructors(aopProxyElement, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            };
        }
    }
}
