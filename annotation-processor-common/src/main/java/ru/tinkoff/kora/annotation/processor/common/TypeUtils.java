package ru.tinkoff.kora.annotation.processor.common;

import com.palantir.javapoet.ClassName;
import jakarta.annotation.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor14;
import javax.lang.model.util.Types;
import java.util.ArrayList;

public final class TypeUtils {

    @Nullable
    public static DeclaredType findSupertype(@Nullable DeclaredType type,
                                             ClassName expectedSupertype) {
        return findSupertype(null, type, expectedSupertype);
    }

    @Nullable
    public static DeclaredType findSupertype(@Nullable ProcessingEnvironment pe,
                                             @Nullable DeclaredType type,
                                             ClassName expectedSupertype) {
        return findSupertype(pe, type, type, expectedSupertype);
    }

    @Nullable
    private static DeclaredType findSupertype(@Nullable ProcessingEnvironment pe,
                                              @Nullable DeclaredType type,
                                              DeclaredType root,
                                              ClassName expectedSupertype) {
        if (type == null) {
            return null;
        }

        var typeElement = (TypeElement) type.asElement();
        if (typeElement.getQualifiedName().contentEquals(expectedSupertype.canonicalName())) {
            if (pe != null) {
                var enrichedType = type.accept(new EnrichGenericTypeArgumentVisitor(pe.getTypeUtils()), root);
                return (DeclaredType) enrichedType;
            } else {
                return type;
            }
        }

        var supertype = typeElement.getSuperclass();
        if (supertype instanceof DeclaredType dt) {
            var supertypeResult = findSupertype(pe, dt, root, expectedSupertype);
            if (supertypeResult != null) {
                return supertypeResult;
            }
        }

        for (var anInterface : typeElement.getInterfaces()) {
            if (anInterface instanceof DeclaredType dt) {
                var supertypeResult = findSupertype(pe, dt, root, expectedSupertype);
                if (supertypeResult != null) {
                    return supertypeResult;
                }
            }
        }

        return null;
    }

    private static class EnrichGenericTypeArgumentVisitor extends SimpleTypeVisitor14<TypeMirror, DeclaredType> {

        private final Types types;

        public EnrichGenericTypeArgumentVisitor(Types types) {
            this.types = types;
        }

        @Override
        public TypeMirror visitArray(ArrayType t, DeclaredType root) {
            var component = t.getComponentType();
            var fixedComponent = visit(component, root);
            if (component == fixedComponent) {
                return t;
            }
            return types.getArrayType(fixedComponent);
        }

        @Override
        public TypeMirror visitDeclared(DeclaredType t, DeclaredType declaredType) {
            if (t.getTypeArguments().isEmpty()) {
                return t;
            }
            var args = new ArrayList<TypeMirror>(t.getTypeArguments().size());
            var changed = false;
            for (var typeArgument : t.getTypeArguments()) {
                var fixed = typeArgument.accept(this, declaredType);
                args.add(fixed);
                if (fixed != typeArgument) {
                    changed = true;
                }
            }
            if (changed) {
                return types.getDeclaredType((TypeElement) t.asElement(), args.toArray(TypeMirror[]::new));
            }
            return t;
        }

        @Override
        public TypeMirror visitTypeVariable(TypeVariable t, DeclaredType declaredType) {
            var element = t.asElement();
            return types.asMemberOf(declaredType, element);
        }

        @Override
        public TypeMirror visitWildcard(WildcardType t, DeclaredType declaredType) {
            var extendsBound = t.getExtendsBound() == null ? null : t.getExtendsBound().accept(this, declaredType);
            var superBound = t.getSuperBound() == null ? null : t.getSuperBound().accept(this, declaredType);
            if (extendsBound == t.getExtendsBound() && superBound == t.getSuperBound()) {
                return t;
            }
            return types.getWildcardType(extendsBound, superBound);
        }
    }
}
