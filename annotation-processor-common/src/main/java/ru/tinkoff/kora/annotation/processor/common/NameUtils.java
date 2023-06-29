package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.ClassName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

public class NameUtils {
    public static String getOuterClassesAsPrefix(Element element) {
        var prefix = new StringBuilder("$");
        var parent = element.getEnclosingElement();
        while (parent.getKind() != ElementKind.PACKAGE) {
            prefix.insert(1, parent.getSimpleName().toString() + "_");
            parent = parent.getEnclosingElement();
        }
        return prefix.toString();
    }

    public static String generatedType(TypeElement from, String postfix) {
        return NameUtils.getOuterClassesAsPrefix(from) + from.getSimpleName() + "_" + postfix;
    }

    public static String generatedType(TypeElement from, ClassName postfix) {
        return NameUtils.getOuterClassesAsPrefix(from) + from.getSimpleName() + "_" + postfix.simpleName();
    }
}
