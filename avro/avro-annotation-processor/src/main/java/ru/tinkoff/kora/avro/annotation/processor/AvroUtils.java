package ru.tinkoff.kora.avro.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.NameUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;


public final class AvroUtils {

    private AvroUtils() {}

    public static String classPackage(Elements elements, Element typeElement) {
        return elements.getPackageOf(typeElement).getQualifiedName().toString();
    }

    public static String writerBinaryName(Element typeElement) {
        return NameUtils.generatedType(typeElement, "AvroBinaryWriter");
    }

    public static String writerBinaryName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);
        return writerBinaryName(typeElement);
    }

    public static String writerJsonName(Element typeElement) {
        return NameUtils.generatedType(typeElement, "AvroJsonWriter");
    }

    public static String writerJsonName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);
        return writerJsonName(typeElement);
    }

    public static String readerBinaryName(TypeElement typeElement) {
        return NameUtils.generatedType(typeElement, "AvroBinaryReader");
    }

    public static String readerBinaryName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);
        return readerBinaryName((TypeElement) typeElement);
    }

    public static String readerJsonName(TypeElement typeElement) {
        return NameUtils.generatedType(typeElement, "AvroJsonReader");
    }

    public static String readerJsonName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);
        return readerJsonName((TypeElement) typeElement);
    }
}
