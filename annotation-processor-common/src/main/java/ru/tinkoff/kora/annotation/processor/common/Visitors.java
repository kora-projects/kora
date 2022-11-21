package ru.tinkoff.kora.annotation.processor.common;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.TypeKindVisitor14;
import javax.lang.model.util.TypeKindVisitor8;
import java.util.function.Function;

public class Visitors {
    public static <T> TypeVisitor<T, ?> declaredTypeVisitor(Function<DeclaredType, T> function) {
        return new TypeKindVisitor14<>() {
            @Override
            public T visitDeclared(DeclaredType t, Object o) {
                return function.apply(t);
            }
        };
    }

    public static <T> T visitDeclaredType(TypeMirror tm, Function<DeclaredType, T> f) {
        return tm.accept(declaredTypeVisitor(f), null);
    }

    public static <T> TypeVisitor<T, ?> primitiveVisitor(Function<PrimitiveType, T> function) {
        return new TypeKindVisitor14<>() {
            @Override
            public T visitPrimitive(PrimitiveType t, Object o) {
                return function.apply(t);
            }
        };
    }


    private static class AbstractVisitor<R, P> extends TypeKindVisitor8<R, P> {
        @Override
        protected R defaultAction(TypeMirror e, P p) {
            return super.visitUnknown(e, p);
        }
    }
}
