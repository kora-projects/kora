package ru.tinkoff.kora.mapstruct.java.extension;

import com.palantir.javapoet.ClassName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
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
                throw new ProcessingErrorException("Class %s was expected to be generated from element by annotation processor but was not".formatted(expectedName), element);
            }
            var constructor = CommonUtils.findConstructors(implementation, m -> m.contains(Modifier.PUBLIC));
            if (constructor.size() != 1) {
                throw new ProcessingErrorException("Generated mapstruct class has unexpected number of constructors", implementation);
            }
            return ExtensionResult.fromExecutable(constructor.get(0));
        };
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
