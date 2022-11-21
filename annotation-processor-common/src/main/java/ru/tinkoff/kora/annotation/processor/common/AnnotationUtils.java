package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.ClassName;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class AnnotationUtils {
    @Nullable
    public static AnnotationMirror findAnnotation(Element element, Predicate<String> namePredicate) {
        return element.getAnnotationMirrors().stream().filter(am -> namePredicate.test(((TypeElement) am.getAnnotationType().asElement()).getQualifiedName().toString())).findFirst().orElse(null);
    }
    @Nullable
    public static AnnotationMirror findAnnotation(Element element, ClassName name) {
        return findAnnotations(element, name, null).stream().findFirst().orElse(null);
    }

    public static List<AnnotationMirror> findAnnotations(Element element, ClassName name, @Nullable ClassName containerName) {
        for (var annotationMirror : element.getAnnotationMirrors()) {
            var annotationType = (TypeElement) annotationMirror.getAnnotationType().asElement();
            if (annotationType.getQualifiedName().contentEquals(name.canonicalName())) {
                return List.of(annotationMirror);
            }
            if (containerName != null && annotationType.getQualifiedName().contentEquals(containerName.canonicalName())) {
                return annotationMirror.getElementValues().entrySet().stream()
                    .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                    .map(Map.Entry::getValue)
                    .map(AnnotationValue::getValue)
                    .map(value -> (List<? extends AnnotationValue>) value)
                    .flatMap(Collection::stream)
                    .map(AnnotationValue::getValue)
                    .map(AnnotationMirror.class::cast)
                    .toList();
            }
        }
        return List.of();
    }

    @Nullable
    public static <T> T parseAnnotationValue(Elements elements, @Nullable AnnotationMirror annotationMirror, String name) {
        if (annotationMirror == null) {
            return null;
        }
        var annotationValues = elements.getElementValuesWithDefaults(annotationMirror);
        for (var entry : annotationValues.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                var value = entry.getValue();
                if (value == null) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                var finalValue = (T) value.getValue();
                return finalValue;
            }
        }
        return null;
    }

    @Nullable
    public static <T> T parseAnnotationValueWithoutDefault(@Nullable AnnotationMirror annotationMirror, String name) {
        if (annotationMirror == null) {
            return null;
        }
        var annotationValues = annotationMirror.getElementValues();
        for (var entry : annotationValues.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                var value = entry.getValue();
                if (value == null) {
                    return null;
                }
                var annotationValue = value.getValue();
                if (annotationValue instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    var finalValue = (T) list.stream()
                        .map(AnnotationValue.class::cast)
                        .map(AnnotationValue::getValue)
                        .toList();
                    return finalValue;
                } else {
                    @SuppressWarnings("unchecked")
                    var finalValue = (T) value.getValue();
                    return finalValue;
                }
            }
        }
        return null;
    }
}
