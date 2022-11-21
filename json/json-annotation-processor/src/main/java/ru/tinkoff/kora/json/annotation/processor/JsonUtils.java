package ru.tinkoff.kora.json.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorField;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;


public class JsonUtils {
    public static String jsonClassPackage(Elements elements, Element typeElement) {
        return elements.getPackageOf(typeElement).getQualifiedName().toString();
    }

    public static String jsonWriterName(Element typeElement) {
        return CommonUtils.getOuterClassesAsPrefix(typeElement) + typeElement.getSimpleName() +  "JsonWriter";
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
    public static String discriminator(Types types, TypeElement element) {
        var discriminatorElement = findSealedSupertype(types, element);

        if (discriminatorElement == null) return null;

        var discriminatorFieldNameAnnotation = discriminatorElement.getAnnotation(JsonDiscriminatorField.class);
        return discriminatorFieldNameAnnotation == null ? null : discriminatorFieldNameAnnotation.value();
    }

    @Nullable
    public static TypeElement findSealedSupertype(Types types, TypeElement element) {
        if (element.getModifiers().contains(Modifier.SEALED)) {
            return element;
        }
        var superClass = types.asElement(element.getSuperclass());
        if (superClass instanceof TypeElement && superClass.getModifiers().contains(Modifier.SEALED)) return (TypeElement) superClass;

        return types.directSupertypes(element.asType())
            .stream()
            .map(types::asElement)
            .filter(e -> e instanceof TypeElement)
            .map(e -> (TypeElement) e)
            .filter(t -> t.getModifiers().contains(Modifier.SEALED))
            .findFirst()
            .orElse(null);
    }
}
