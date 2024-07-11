package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.Element;
import javax.lang.model.type.ArrayType;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class TagUtils {

    private TagUtils() { }

    private static Set<String> parseTagValue0(AnnotatedConstruct element) {
        for (var annotationMirror : element.getAnnotationMirrors()) {
            var type = annotationMirror.getAnnotationType();
            if (type.toString().equals(CommonClassNames.tag.canonicalName())) {
                return Objects.requireNonNull(AnnotationUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(annotationMirror, "value"))
                    .stream()
                    .map(TypeMirror::toString)
                    .collect(Collectors.toSet());
            }

            var annotationElement = type.asElement();
            for (var annotatedWith : annotationElement.getAnnotationMirrors()) {
                var annotationType = annotatedWith.getAnnotationType();
                if (annotationType.toString().equals(CommonClassNames.tag.canonicalName())) {
                    return Objects.requireNonNull(AnnotationUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(annotatedWith, "value"))
                        .stream()
                        .map(TypeMirror::toString)
                        .collect(Collectors.toSet());
                }
            }
        }
        if (element instanceof ArrayType array) {
            return parseTagValue0(array.getComponentType());
        }
        return Set.of();
    }

    public static Set<String> parseTagValue(AnnotatedConstruct construct) {
        var tag = parseTagValue0(construct);
        if (!tag.isEmpty()) {
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
                        if (tag.isEmpty()) {
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
                        if (!tag.isEmpty()) {
                            return tag;
                        }
                    }
                }
                return parseTagValue0(recordComponent.getAccessor());
            }
            if (element.getKind() == ElementKind.METHOD) {
                var method = (ExecutableElement) element;
                if (!method.getParameters().isEmpty()) {
                    return Set.of();
                }
                for (var enclosedElement : element.getEnclosingElement().getEnclosedElements()) {
                    if (enclosedElement.getKind() == ElementKind.FIELD && enclosedElement.getSimpleName().contentEquals(element.getSimpleName())) {
                        tag = parseTagValue0(enclosedElement);
                        if (!tag.isEmpty()) {
                            return tag;
                        }
                    }
                    if (enclosedElement.getKind() == ElementKind.RECORD_COMPONENT && enclosedElement.getSimpleName().contentEquals(element.getSimpleName())) {
                        tag = parseTagValue0(enclosedElement);
                        if (!tag.isEmpty()) {
                            return tag;
                        }
                    }
                }
            }
        }
        return Set.of();
    }

    public static AnnotationSpec makeAnnotationSpecForTypes(TypeName ... tags) {
        var annotation = AnnotationSpec.builder(CommonClassNames.tag);
        var value = CodeBlock.builder();
        value.add("{");
        var tagsList = List.of(tags);
        for (int i = 0; i < tagsList.size(); i++) {
            if (i > 0) {
                value.add(", ");
            }
            value.add("$T.class", tagsList.get(i));
        }
        value.add("}");
        return annotation.addMember("value", value.build()).build();
    }

    public static AnnotationSpec makeAnnotationSpec(Set<String> tags) {
        var annotation = AnnotationSpec.builder(CommonClassNames.tag);
        var value = CodeBlock.builder();
        value.add("{");
        var tagsList = new ArrayList<>(tags);
        for (int i = 0; i < tagsList.size(); i++) {
            if (i > 0) {
                value.add(", ");
            }
            value.add("$L.class", tagsList.get(i));
        }
        value.add("}");
        return annotation.addMember("value", value.build()).build();
    }

    public static CodeBlock writeTagAnnotationValue(List<TypeMirror> tag) {
        var b = CodeBlock.builder()
            .add("{");
        for (var typeMirrorValue : tag) {
            b.add("$T.class, ", typeMirrorValue);
        }
        return b.add("}").build();
    }

    public static Boolean tagsMatch(Collection<String> requiredTags, Collection<String> providedTags) {
        if (requiredTags.isEmpty() && providedTags.isEmpty()) {
            return true;
        }
        if (requiredTags.isEmpty()) {
            return false;
        }
        if (requiredTags.contains(CommonClassNames.tagAny.canonicalName())) {
            return true;
        }
        for (var tag : requiredTags) {
            if (!providedTags.contains(tag)) {
                return false;
            }
        }
        return true;
    }
}
