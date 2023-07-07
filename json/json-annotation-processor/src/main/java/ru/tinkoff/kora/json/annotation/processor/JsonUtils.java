package ru.tinkoff.kora.json.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;


public class JsonUtils {
    public static String jsonClassPackage(Elements elements, Element typeElement) {
        return elements.getPackageOf(typeElement).getQualifiedName().toString();
    }

    public static String jsonWriterName(Element typeElement) {
        return CommonUtils.getOuterClassesAsPrefix(typeElement) + typeElement.getSimpleName() + "JsonWriter";
    }

    public static String jsonWriterName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);

        return jsonWriterName(typeElement);
    }

    public static String jsonReaderName(TypeElement typeElement) {
        return CommonUtils.getOuterClassesAsPrefix(typeElement) + typeElement.getSimpleName() + "JsonReader";
    }

    public static String jsonReaderName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);

        return jsonReaderName((TypeElement) typeElement);
    }


    @Nullable
    public static String discriminatorField(Types types, TypeElement element) {
        if (element.getModifiers().contains(Modifier.SEALED)) {
            var annotation = AnnotationUtils.findAnnotation(element, JsonTypes.jsonDiscriminatorField);
            if (annotation != null) {
                return AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "value");
            }
        }
        var superClass = types.asElement(element.getSuperclass());
        if (superClass instanceof TypeElement && superClass.getModifiers().contains(Modifier.SEALED)) {
            var annotation = AnnotationUtils.findAnnotation(superClass, JsonTypes.jsonDiscriminatorField);
            if (annotation != null) {
                return AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "value");
            }
        }
        for (var directSupertype : types.directSupertypes(element.asType())) {
            if (directSupertype.toString().equals("java.lang.Object")) {
                continue;
            }
            var superelement = types.asElement(directSupertype);
            var discriminator = discriminatorField(types, (TypeElement) superelement);
            if (discriminator != null) {
                return discriminator;
            }
        }
        return null;
    }

    public static List<String> discriminatorValue(TypeElement element) {
        var annotation = AnnotationUtils.findAnnotation(element, JsonTypes.jsonDiscriminatorValue);
        if (annotation != null) {
            var value = AnnotationUtils.<List<String>>parseAnnotationValueWithoutDefault(annotation, "value");
            if (value != null) {
                if (value.isEmpty()) {
                    throw new ProcessingErrorException("Discriminator value can't be empty array", element, annotation);
                }
                return value;
            }
        }
        return List.of(element.getSimpleName().toString());
    }
}
