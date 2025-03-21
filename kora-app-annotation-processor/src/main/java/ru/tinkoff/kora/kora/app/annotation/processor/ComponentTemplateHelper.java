package ru.tinkoff.kora.kora.app.annotation.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.IdentityHashMap;

public class ComponentTemplateHelper {
    public sealed interface TemplateMatch {
        enum None implements TemplateMatch {INSTANCE}

        record Some(IdentityHashMap<TypeVariable, TypeMirror> map) implements TemplateMatch {}
    }

    public static TemplateMatch match(ProcessingContext ctx, DeclaredType declarationType, DeclaredType requiredType) {
        return match0(ctx, declarationType, requiredType, true);
    }

    private static TemplateMatch match0(ProcessingContext ctx, DeclaredType declarationType, DeclaredType requiredType, boolean isRoot) {
        var declarationUnwrapped = ctx.serviceTypeHelper.tryUnwrap(declarationType);
        var requiredUnwrapped = ctx.serviceTypeHelper.tryUnwrap(requiredType);
        var declarationErasure = ctx.types.erasure(declarationUnwrapped);
        var requiredErasure = ctx.types.erasure(requiredUnwrapped);
        if (isRoot) {
            if (!ctx.types.isAssignable(declarationErasure, requiredErasure)) {
                return TemplateMatch.None.INSTANCE;
            }
        } else {
            if (!ctx.types.isSameType(declarationErasure, requiredErasure)) {
                return TemplateMatch.None.INSTANCE;
            }
        }
        var typeElement = (TypeElement) requiredUnwrapped.asElement();
        var map = new IdentityHashMap<TypeVariable, TypeMirror>();
        for (int i = 0; i < typeElement.getTypeParameters().size(); i++) {
            var typeVariable = typeElement.getTypeParameters().get(i);
            var declarationTypeParameter = ctx.types.asMemberOf(declarationUnwrapped, typeVariable);
            var requiredTypeParameter = ctx.types.asMemberOf(requiredUnwrapped, typeVariable);
            if (!match(ctx, declarationTypeParameter, requiredTypeParameter, map)) {
                return TemplateMatch.None.INSTANCE;
            }
        }
        return new TemplateMatch.Some(map);
    }

    private static boolean match(ProcessingContext ctx, TypeMirror declarationTypeParameter, TypeMirror requiredTypeParameter, IdentityHashMap<TypeVariable, TypeMirror> map) {
        if (declarationTypeParameter.getKind() == TypeKind.TYPEVAR) {
            var dtv = (TypeVariable) declarationTypeParameter;
            TypeMirror upperBound = dtv.getUpperBound();
            if(upperBound instanceof IntersectionType it) {
                for (TypeMirror sectionBound : it.getBounds()) {
                    TypeMirror sectionBoundErasure = ctx.types.erasure(sectionBound);
                    if (!ctx.types.isAssignable(requiredTypeParameter, sectionBoundErasure)) {
                        return false;
                    }
                }

                map.put(dtv, requiredTypeParameter);
                return true;
            } else {
                TypeMirror upperBoundErasure = ctx.types.erasure(upperBound);
                if (ctx.types.isAssignable(requiredTypeParameter, upperBoundErasure)) {
                    map.put(dtv, requiredTypeParameter);
                    return true;
                } else {
                    return false;
                }
            }
        }
        if (ctx.types.isAssignable(declarationTypeParameter, requiredTypeParameter)) {
            return true;
        }
        if (requiredTypeParameter.getKind() == TypeKind.DECLARED && declarationTypeParameter.getKind() == TypeKind.DECLARED) {
            var drt = (DeclaredType) requiredTypeParameter;
            var ddt = (DeclaredType) declarationTypeParameter;
            var match = match0(ctx, ddt, drt, false);
            if (match instanceof TemplateMatch.None) {
                return false;
            }
            var some = (TemplateMatch.Some) match;
            map.putAll(some.map());
            return true;
        } else if (requiredTypeParameter.getKind() == TypeKind.ARRAY && declarationTypeParameter.getKind() == TypeKind.ARRAY) {
            var ddt = (ArrayType) declarationTypeParameter;
            var drt = (ArrayType) requiredTypeParameter;
            return match(ctx, ddt.getComponentType(), drt.getComponentType(), map);
        } else {
            return false;
        }
    }

    public static TypeMirror replace(Types types, TypeMirror declaredType, IdentityHashMap<? extends TypeMirror, TypeMirror> getFrom) {
        return declaredType.accept(new TypeVisitor<>() {
            @Override
            public TypeMirror visit(TypeMirror t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitPrimitive(PrimitiveType t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitNull(NullType t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitArray(ArrayType t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitDeclared(DeclaredType t, Object o) {
                if (t.getTypeArguments().isEmpty()) {
                    return t;
                }
                var maybeDefined = getFrom.get(t);
                if (maybeDefined != null) {
                    return maybeDefined;
                }
                var realParams = new ArrayList<TypeMirror>(t.getTypeArguments().size());
                var changed = false;
                for (var typeArgument : t.getTypeArguments()) {
                    var replaced = replace(types, typeArgument, getFrom);
                    if (typeArgument != replaced) {
                        changed = true;
                    }
                    realParams.add(replaced);
                }
                if (!changed) {
                    return t;
                }
                return types.getDeclaredType((TypeElement) t.asElement(), realParams.toArray(new TypeMirror[0]));
            }

            @Override
            public TypeMirror visitError(ErrorType t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitTypeVariable(TypeVariable t, Object o) {
                return getFrom.getOrDefault(t, t);
            }

            @Override
            public TypeMirror visitWildcard(WildcardType t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitExecutable(ExecutableType t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitNoType(NoType t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitUnknown(TypeMirror t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitUnion(UnionType t, Object o) {
                return t;
            }

            @Override
            public TypeMirror visitIntersection(IntersectionType t, Object o) {
                return t;
            }
        }, null);
    }
}
