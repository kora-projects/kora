package ru.tinkoff.kora.annotation.processor.common;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SealedTypeUtils {
    public static List<TypeElement> collectFinalPermittedSubtypes(Types types, Elements elements, TypeElement jsonElement) {
        var result = new ArrayList<TypeElement>();
        var seen = new HashSet<String>();
        var o = new Object() {
            void visit(TypeElement element) {
                if (element.getModifiers().contains(Modifier.SEALED)) {
                    for (var permittedSubclass : element.getPermittedSubclasses()) {
                        visit((TypeElement) types.asElement(permittedSubclass));
                    }
                } else {
                    var packageElement = elements.getPackageOf(element).getQualifiedName().toString();
                    var name = element.getQualifiedName().toString();
                    var fullName = packageElement + "." + name;
                    if (seen.add(fullName)) {
                        result.add(element);
                    }
                }
            }
        };
        o.visit(jsonElement);
        return result;
    }

}
