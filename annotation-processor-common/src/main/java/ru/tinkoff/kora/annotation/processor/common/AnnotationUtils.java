package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.ClassName;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class AnnotationUtils {
    @Nullable
    public static AnnotationMirror findAnnotation(Element element, Predicate<String> namePredicate) {
        for (var am : element.getAnnotationMirrors()) {
            var name = ((TypeElement) am.getAnnotationType().asElement()).getQualifiedName().toString();
            if (namePredicate.test(name)) {
                return am;
            }
        }
        return null;
    }

    @Nullable
    public static AnnotationMirror findAnnotation(Element element, ClassName name) {
        var list = findAnnotations(element, name, null);
        if (list.isEmpty()) {
            return null;
        } else {
            return Objects.requireNonNull(list.get(0));
        }
    }
    @Nullable
    public static AnnotationMirror findAnnotation(Elements elements, Element element, ClassName name) {
        var list = findAnnotations(elements, element, name, null);
        if (list.isEmpty()) {
            return null;
        } else {
            return Objects.requireNonNull(list.get(0));
        }
    }

    public static boolean isAnnotationPresent(Element element, ClassName name) {
        return !findAnnotations(element, name, null).isEmpty();
    }

    public static List<AnnotationMirror> findAnnotations(Element element, ClassName name, @Nullable ClassName containerName) {
        var result = new ArrayList<AnnotationMirror>();
        for (var annotationMirror : element.getAnnotationMirrors()) {
            var annotationType = (TypeElement) annotationMirror.getAnnotationType().asElement();
            if (annotationType.getQualifiedName().contentEquals(name.canonicalName())) {
                result.add(annotationMirror);
            }
            if (containerName != null && annotationType.getQualifiedName().contentEquals(containerName.canonicalName())) {
                var value = AnnotationUtils.<List<AnnotationValue>>parseAnnotationValueWithoutDefault(annotationMirror, "value");
                for (var annotationValue : value) {
                    var am = (AnnotationMirror) annotationValue.getValue();
                    result.add(am);
                }
            }
        }
        return result;
    }
    public static List<AnnotationMirror> findAnnotations(Elements elements, Element element, ClassName name, @Nullable ClassName containerName) {
        var result = new ArrayList<AnnotationMirror>();
        for (var annotationMirror : elements.getAllAnnotationMirrors(element)) {
            var annotationType = (TypeElement) annotationMirror.getAnnotationType().asElement();
            if (annotationType.getQualifiedName().contentEquals(name.canonicalName())) {
                result.add(annotationMirror);
            }
            if (containerName != null && annotationType.getQualifiedName().contentEquals(containerName.canonicalName())) {
                var value = AnnotationUtils.<List<AnnotationValue>>parseAnnotationValueWithoutDefault(annotationMirror, "value");
                for (var annotationValue : value) {
                    var am = (AnnotationMirror) annotationValue.getValue();
                    result.add(am);
                }
            }
        }
        return result;
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
