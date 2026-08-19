package io.koraframework.avro.annotation.processor;

import io.koraframework.annotation.processor.common.NameUtils;

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

    public static String writerName(Element typeElement) {
        return NameUtils.generatedType(typeElement, "AvroWriter");
    }

    public static String writerName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);
        return writerName(typeElement);
    }

    public static String readerName(TypeElement typeElement) {
        return NameUtils.generatedType(typeElement, "AvroReader");
    }

    public static String readerName(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);
        return readerName((TypeElement) typeElement);
    }
}
