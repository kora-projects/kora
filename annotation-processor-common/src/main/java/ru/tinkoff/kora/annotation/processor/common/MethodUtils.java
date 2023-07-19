package ru.tinkoff.kora.annotation.processor.common;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;

public final class MethodUtils {

    private MethodUtils() {}

    public static boolean isMono(ExecutableElement method) {
        return CommonUtils.isMono(method.getReturnType());
    }

    public static boolean isFuture(ExecutableElement method) {
        return CommonUtils.isFuture(method.getReturnType());
    }

    public static boolean isFlux(ExecutableElement method) {
        return CommonUtils.isFlux(method.getReturnType());
    }

    public static boolean isVoid(ExecutableElement method) {
        return CommonUtils.isVoid(method.getReturnType());
    }

    public static boolean isVoidGeneric(TypeMirror returnType) {
        if(returnType instanceof DeclaredType dt) {
            return CommonUtils.isVoid(dt.getTypeArguments().get(0));
        }

        return false;
    }

    public static Optional<TypeMirror> getGenericType(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType) {
            return ((DeclaredType) typeMirror).getTypeArguments().stream()
                .map(v -> ((TypeMirror) v))
                .findFirst();
        } else {
            return Optional.empty();
        }
    }
}
