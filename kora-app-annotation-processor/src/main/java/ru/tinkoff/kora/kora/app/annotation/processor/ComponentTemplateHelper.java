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
        var declarationErasure = ctx.types.erasure(declarationType);
        var requiredErasure = ctx.types.erasure(requiredType);
        if (!ctx.types.isAssignable(declarationErasure, requiredErasure)) {
            return TemplateMatch.None.INSTANCE;
        }
        var typeElement = (TypeElement) requiredType.asElement();
        var map = new IdentityHashMap<TypeVariable, TypeMirror>();
        for (int i = 0; i < typeElement.getTypeParameters().size(); i++) {
            var typeVariable = typeElement.getTypeParameters().get(i);
            var declarationTypeParameter = ctx.types.asMemberOf(declarationType, typeVariable);
            var requiredTypeParameter = ctx.types.asMemberOf(requiredType, typeVariable);
            if (!match(ctx, declarationTypeParameter, requiredTypeParameter, map)) {
                return TemplateMatch.None.INSTANCE;
            }
        }
        return new TemplateMatch.Some(map);
    }

    private static boolean match(ProcessingContext ctx, TypeMirror declarationTypeParameter, TypeMirror requiredTypeParameter, IdentityHashMap<TypeVariable, TypeMirror> map) {
        if (declarationTypeParameter.getKind() == TypeKind.TYPEVAR) {
            var dtv = (TypeVariable) declarationTypeParameter;
            if (ctx.types.isAssignable(requiredTypeParameter, ctx.types.erasure(dtv.getUpperBound()))) {
                map.put(dtv, requiredTypeParameter);
                return true;
            } else {
                return false;
            }
        }
        if (ctx.types.isAssignable(declarationTypeParameter, requiredTypeParameter)) {
            return true;
        }
        if (requiredTypeParameter.getKind() != TypeKind.DECLARED || declarationTypeParameter.getKind() != TypeKind.DECLARED) {
            return false;
        }
        var drt = (DeclaredType) requiredTypeParameter;
        var ddt = (DeclaredType) declarationTypeParameter;
        var match = match(ctx, ddt, drt);
        if (match instanceof TemplateMatch.None) {
            return false;
        }
        var some = (TemplateMatch.Some) match;
        map.putAll(some.map());
        return true;
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
