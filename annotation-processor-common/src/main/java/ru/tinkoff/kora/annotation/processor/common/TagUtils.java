package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TagUtils {

    public static Set<String> parseTagValue(Element element) {
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
        return Set.of();
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
            value.add(tagsList.get(i)).add(".class");
        }
        value.add("}");
        return annotation.addMember("value", value.build()).build();
    }
}
