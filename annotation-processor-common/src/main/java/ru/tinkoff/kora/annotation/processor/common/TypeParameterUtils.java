package ru.tinkoff.kora.annotation.processor.common;

import javax.lang.model.type.*;

public class TypeParameterUtils {
    public static boolean hasTypeParameter(TypeMirror typeMirror) {
        return typeMirror.accept(new TypeVisitor<>() {
            @Override
            public Boolean visit(TypeMirror t, Object o) {
                return null;
            }

            @Override
            public Boolean visitPrimitive(PrimitiveType t, Object o) {
                return false;
            }

            @Override
            public Boolean visitNull(NullType t, Object o) {
                return false;
            }

            @Override
            public Boolean visitArray(ArrayType t, Object o) {
                return t.getComponentType().accept(this, null);
            }

            @Override
            public Boolean visitDeclared(DeclaredType t, Object o) {
                for (var typeArgument : t.getTypeArguments()) {
                    if (typeArgument.accept(this, null)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Boolean visitError(ErrorType t, Object o) {
                return false;
            }

            @Override
            public Boolean visitTypeVariable(TypeVariable t, Object o) {
                return true;
            }

            @Override
            public Boolean visitWildcard(WildcardType t, Object o) {
                if (t.getExtendsBound() != null) {
                    return t.getExtendsBound().accept(this, null);
                }
                if (t.getSuperBound() != null) {
                    return t.getSuperBound().accept(this, null);
                }
                return false;
            }

            @Override
            public Boolean visitExecutable(ExecutableType t, Object o) {
                return false;
            }

            @Override
            public Boolean visitNoType(NoType t, Object o) {
                return false;
            }

            @Override
            public Boolean visitUnknown(TypeMirror t, Object o) {
                return false;
            }

            @Override
            public Boolean visitUnion(UnionType t, Object o) {
                return t.getAlternatives().stream()
                    .map(tm -> tm.accept(this, null))
                    .reduce(false, (a, b) -> a || b);
            }

            @Override
            public Boolean visitIntersection(IntersectionType t, Object o) {
                return t.getBounds().stream()
                    .map(tm -> tm.accept(this, null))
                    .reduce(false, (a, b) -> a || b);
            }
        }, null);
    }
}
