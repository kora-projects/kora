package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.ClassName;
import jakarta.annotation.Nullable;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

public final class TypeUtils {
    @Nullable
    public static DeclaredType findSupertype(@Nullable DeclaredType type, ClassName expectedSupertype) {
        if (type == null) {
            return null;
        }
        var typeElement = (TypeElement) type.asElement();
        if (typeElement.getQualifiedName().contentEquals(expectedSupertype.canonicalName())) {
            return type;
        }
        for (var anInterface : typeElement.getInterfaces()) {
            var supertype = findSupertype((DeclaredType) anInterface, expectedSupertype);
            if (supertype != null) {
                return supertype;
            }
        }
        var supertype = typeElement.getSuperclass();
        if (supertype.getKind() == TypeKind.DECLARED) {
            var dt = (DeclaredType) supertype;
            var supertypeResult = findSupertype(dt, expectedSupertype);
            if (supertypeResult != null) {
                return dt;
            }
        }
        return null;
    }
}
