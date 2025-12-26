package ru.tinkoff.kora.database.annotation.processor.extension;

import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static ru.tinkoff.kora.annotation.processor.common.TagUtils.parseTagValue;
import static ru.tinkoff.kora.annotation.processor.common.TagUtils.tagsMatch;

public class RepositoryKoraExtension implements KoraExtension {
    private final Elements elements;
    private final Types types;

    public RepositoryKoraExtension(ProcessingEnvironment processingEnvironment) {
        this.elements = processingEnvironment.getElementUtils();
        this.types = processingEnvironment.getTypeUtils();
    }

    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, String tag) {
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var element = this.types.asElement(typeMirror);
        if (element.getKind() != ElementKind.INTERFACE && (element.getKind() != ElementKind.CLASS || !element.getModifiers().contains(Modifier.ABSTRACT))) {
            return null;
        }
        if (AnnotationUtils.findAnnotation(element, DbUtils.REPOSITORY_ANNOTATION) == null) {
            return null;
        }
        if (!tagsMatch(tag, parseTagValue(element))) {
            return null;
        }
        var typeElement = (TypeElement) this.types.asElement(typeMirror);
        var repositoryName = NameUtils.generatedType(typeElement, "Impl");
        return KoraExtensionDependencyGenerator.generatedFromWithName(this.elements, typeElement, repositoryName);
    }
}
