package io.koraframework.mapstruct.java.extension;

import com.palantir.javapoet.ClassName;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.annotation.processor.common.TagUtils;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionResult;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public final class MapstructKoraExtension implements KoraExtension {
    static final ClassName MAPPER_ANNOTATION = ClassName.get("org.mapstruct", "Mapper");
    private static final String IMPLEMENTATION_SUFFIX = "Impl";
    private final ProcessingEnvironment env;

    public MapstructKoraExtension(ProcessingEnvironment env) {
        this.env = env;
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, @Nullable String tag) {
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var dtm = (DeclaredType) typeMirror;
        var element = dtm.asElement();
        if (element.getKind() != ElementKind.INTERFACE && element.getKind() != ElementKind.CLASS) {
            return null;
        }
        var annotation = AnnotationUtils.findAnnotation(element, MAPPER_ANNOTATION);
        if (annotation == null) {
            return null;
        }
        var elementTag = TagUtils.parseTagValue(dtm);
        if (!Objects.equals(tag, elementTag)) {
            return null;
        }
        return () -> {
            var packageName = env.getElementUtils().getPackageOf(element).getQualifiedName().toString();
            var expectedName = getMapstructMapperName(element);
            var implementation = env.getElementUtils().getTypeElement(packageName + "." + expectedName);
            if (implementation == null) {
                throw new ProcessingErrorException(missingMapstructImplementationError(element, packageName, expectedName), element);
            }
            var constructor = CommonUtils.findConstructors(implementation, m -> m.contains(Modifier.PUBLIC));
            if (constructor.size() != 1) {
                throw new ProcessingErrorException(invalidMapstructConstructorError(element, implementation, constructor.size()), implementation);
            }
            return ExtensionResult.fromExecutable(constructor.get(0));
        };
    }

    private static String missingMapstructImplementationError(Element mapper, String packageName, String expectedName) {
        return """
            MapStruct mapper implementation was not generated for `%s`.

            Kora expected generated class `%s.%s`, but it is not available in this annotation-processing round.

            Fix: make sure the MapStruct annotation processor is configured for this module and that the mapper compiles without MapStruct errors.
            """.formatted(mapper, packageName, expectedName);
    }

    private static String invalidMapstructConstructorError(Element mapper, TypeElement implementation, int constructorCount) {
        return """
            Invalid MapStruct mapper implementation for `%s`.

            Generated class `%s` must have exactly one public constructor so Kora can use it as a dependency.
            Actual public constructor count: %d

            Fix: check the generated MapStruct implementation and mapper configuration, or provide the mapper as a regular Kora component manually.
            """.formatted(mapper, implementation.getQualifiedName(), constructorCount);
    }

    private String getMapstructMapperName(Element element) {
        var parts = new ArrayList<String>();
        parts.add(element.getSimpleName().toString());
        var parent = element.getEnclosingElement();
        while (parent.getKind() != ElementKind.PACKAGE) {
            parts.add(parent.getSimpleName().toString());
            parent = parent.getEnclosingElement();
        }
        Collections.reverse(parts);
        return String.join("$", parts) + IMPLEMENTATION_SUFFIX;
    }
}
