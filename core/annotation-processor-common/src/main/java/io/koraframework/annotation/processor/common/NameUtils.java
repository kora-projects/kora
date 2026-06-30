package io.koraframework.annotation.processor.common;

import com.palantir.javapoet.ClassName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

public final class NameUtils {

    private NameUtils() { }

    public static String getOuterClassesAsPrefix(Element element) {
        var parent = element.getEnclosingElement();
        var prefixBuilder = (element.getSimpleName().toString().startsWith("$"))
            ? new StringBuilder()
            : new StringBuilder("$");
        while (parent.getKind() != ElementKind.PACKAGE) {
            prefixBuilder.insert(1, parent.getSimpleName().toString() + "_");
            parent = parent.getEnclosingElement();
        }
        return prefixBuilder.toString();
    }

    public static String generatedType(Element from, String postfix) {
        return NameUtils.getOuterClassesAsPrefix(from) + from.getSimpleName() + "_" + postfix;
    }

    public static String generatedType(Element from, ClassName postfix) {
        return NameUtils.getOuterClassesAsPrefix(from) + from.getSimpleName() + "_" + postfix.simpleName();
    }
}
