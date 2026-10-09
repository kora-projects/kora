package io.koraframework.annotation.processor.common;

import com.palantir.javapoet.ClassName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

public final class NameUtils {

    private NameUtils() { }

    /**
     * @return prefix of the type generated for the element: <code>$</code> followed by the names of all the enclosing types.
     * Enclosing type can be a generated one, like a holder of nested generated types, its own <code>$</code> is not repeated,
     * so the result never has more than one leading <code>$</code>
     */
    public static String getOuterClassesAsPrefix(Element element) {
        var outerClasses = new StringBuilder();
        var parent = element.getEnclosingElement();
        while (parent.getKind() != ElementKind.PACKAGE) {
            outerClasses.insert(0, withoutGeneratedPrefix(parent.getSimpleName().toString()) + "_");
            parent = parent.getEnclosingElement();
        }
        if (outerClasses.isEmpty() && element.getSimpleName().toString().startsWith("$")) {
            return "";
        }
        return "$" + outerClasses;
    }

    private static String withoutGeneratedPrefix(String name) {
        return name.startsWith("$")
            ? name.substring(1)
            : name;
    }

    public static String generatedType(Element from, String postfix) {
        return NameUtils.getOuterClassesAsPrefix(from) + from.getSimpleName() + "_" + postfix;
    }

    public static String generatedType(Element from, ClassName postfix) {
        return NameUtils.getOuterClassesAsPrefix(from) + from.getSimpleName() + "_" + postfix.simpleName();
    }
}
