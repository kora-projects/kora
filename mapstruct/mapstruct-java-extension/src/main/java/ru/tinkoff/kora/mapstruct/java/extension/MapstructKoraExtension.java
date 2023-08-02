package ru.tinkoff.kora.mapstruct.java.extension;

import com.squareup.javapoet.ClassName;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Set;

public final class MapstructKoraExtension implements KoraExtension {
    static final ClassName MAPPER_ANNOTATION = ClassName.get("org.mapstruct", "Mapper");
    private static final String IMPLEMENTATION_SUFFIX = "Impl";
    private final ProcessingEnvironment env;

    public MapstructKoraExtension(ProcessingEnvironment env) {
        this.env = env;
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
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
        var tag = TagUtils.parseTagValue(dtm);
        if (!tag.equals(tags)) {
            return null;
        }
        return () -> {
            var packageName = env.getElementUtils().getPackageOf(element).getQualifiedName().toString();
            var expectedName = element.getSimpleName() + IMPLEMENTATION_SUFFIX;
            var implementation = env.getElementUtils().getTypeElement(packageName + "." + expectedName);
            if (implementation == null) {
                return ExtensionResult.nextRound();
            }
            var constructor = CommonUtils.findConstructors(implementation, m -> m.contains(Modifier.PUBLIC));
            if (constructor.size() != 1) {
                throw new ProcessingErrorException("Generated mapstruct class has unexpected number of constructors", implementation);
            }
            return ExtensionResult.fromExecutable(constructor.get(0));
        };
    }
}
