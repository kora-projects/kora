package ru.tinkoff.kora.annotation.processor.common;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import jakarta.annotation.Nullable;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;

public final class TagUtils {

    private TagUtils() {}

    @Nullable
    private static String parseTagValue0(AnnotatedConstruct element) {
        for (var annotationMirror : element.getAnnotationMirrors()) {
            var type = annotationMirror.getAnnotationType();
            if (type.toString().equals(CommonClassNames.tag.canonicalName())) {
                return AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(annotationMirror, "value").toString();
            }

            var annotationElement = type.asElement();
            for (var annotatedWith : annotationElement.getAnnotationMirrors()) {
                var annotationType = annotatedWith.getAnnotationType();
                if (annotationType.toString().equals(CommonClassNames.tag.canonicalName())) {
                    return Objects.requireNonNull(AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(annotatedWith, "value")).toString();
                }
            }
        }
        if (element instanceof ArrayType array) {
            return parseTagValue0(array.getComponentType());
        }
        return null;
    }

    @Nullable
    public static String parseTagValue(AnnotatedConstruct construct) {
        var tag = parseTagValue0(construct);
        if (tag != null) {
            return tag;
        }
        if (!(construct instanceof Element element)) {
            return tag;
        }
        if (element.getEnclosingElement().getKind() == ElementKind.RECORD) {
            if (element.getKind() == ElementKind.FIELD) {
                for (var enclosedElement : element.getEnclosingElement().getEnclosedElements()) {
                    if (enclosedElement.getKind() == ElementKind.RECORD_COMPONENT && enclosedElement.getSimpleName().contentEquals(element.getSimpleName())) {
                        var recordComponent = (RecordComponentElement) enclosedElement;
                        tag = parseTagValue0(recordComponent);
                        if (tag == null) {
                            return parseTagValue0(recordComponent.getAccessor());
                        } else {
                            return tag;
                        }
                    }
                }
            }
            if (element.getKind() == ElementKind.RECORD_COMPONENT) {
                var recordComponent = (RecordComponentElement) element;
                for (var enclosedElement : element.getEnclosingElement().getEnclosedElements()) {
                    if (enclosedElement.getKind() == ElementKind.FIELD && enclosedElement.getSimpleName().contentEquals(element.getSimpleName())) {
                        tag = parseTagValue0(enclosedElement);
                        if (tag != null) {
                            return tag;
                        }
                    }
                }
                return parseTagValue0(recordComponent.getAccessor());
            }
            if (element.getKind() == ElementKind.METHOD) {
                var method = (ExecutableElement) element;
                if (!method.getParameters().isEmpty()) {
                    return null;
                }
                for (var enclosedElement : element.getEnclosingElement().getEnclosedElements()) {
                    if (enclosedElement.getKind() == ElementKind.FIELD && enclosedElement.getSimpleName().contentEquals(element.getSimpleName())) {
                        tag = parseTagValue0(enclosedElement);
                        if (tag != null) {
                            return tag;
                        }
                    }
                    if (enclosedElement.getKind() == ElementKind.RECORD_COMPONENT && enclosedElement.getSimpleName().contentEquals(element.getSimpleName())) {
                        tag = parseTagValue0(enclosedElement);
                        if (tag != null) {
                            return tag;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public static AnnotationSpec makeAnnotationSpec(@Nullable String tag) {
        if (tag == null) {
            return null;
        }
        return AnnotationSpec.builder(CommonClassNames.tag).addMember("value", CodeBlock.of("$L.class", tag)).build();
    }

    @Nullable
    public static AnnotationSpec makeAnnotationSpec(@Nullable TypeMirror tag) {
        if (tag == null) {
            return null;
        }
        return AnnotationSpec.builder(CommonClassNames.tag).addMember("value", CodeBlock.of("$T.class", tag)).build();
    }

    @Nullable
    public static AnnotationSpec makeAnnotationSpec(@Nullable ClassName tag) {
        if (tag == null) {
            return null;
        }
        return AnnotationSpec.builder(CommonClassNames.tag).addMember("value", CodeBlock.of("$T.class", tag)).build();
    }

    @Nullable
    public static AnnotationSpec makeAnnotationSpec(@Nullable TypeElement tag) {
        if (tag == null) {
            return null;
        }
        return AnnotationSpec.builder(CommonClassNames.tag).addMember("value", CodeBlock.of("$T.class", ClassName.get(tag))).build();
    }

    @Nullable
    public static CodeBlock writeTagAnnotationValue(@Nullable TypeMirror tag) {
        if (tag == null) {
            return null;
        }
        return CodeBlock.of("$T.class", tag);
    }

    public static Boolean tagsMatch(@Nullable String requiredTag, @Nullable String providedTag) {
        if (requiredTag == null && providedTag == null) {
            return true;
        }
        if (requiredTag == null) {
            return false;
        }
        if (requiredTag.equals(CommonClassNames.tagAny.canonicalName())) {
            return true;
        }
        return requiredTag.equals(providedTag);
    }

}
