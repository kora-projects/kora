package ru.tinkoff.kora.annotation.processor.common;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.Types;
import java.util.Map;

public class GenericTypeResolver {
    public static TypeMirror resolve(Types types, Map<TypeVariable, TypeMirror> parameters, TypeMirror typeMirror) {
        return typeMirror.accept(new TypeVisitor<TypeMirror, Void>() {

            @Override
            public TypeMirror visit(TypeMirror t, Void unused) {
                return t;
            }

            @Override
            public TypeMirror visitPrimitive(PrimitiveType t, Void unused) {
                return t;
            }

            @Override
            public TypeMirror visitNull(NullType t, Void unused) {
                return t;
            }

            @Override
            public TypeMirror visitArray(ArrayType t, Void unused) {
                var componentType = t.getComponentType();
                return types.getArrayType(GenericTypeResolver.resolve(types, parameters, componentType));
            }

            @Override
            public TypeMirror visitDeclared(DeclaredType t, Void unused) {
                var typeArguments = t.getTypeArguments();
                var typeParams = typeArguments.stream()
                    .map(arg -> GenericTypeResolver.resolve(types, parameters, arg))
                    .toArray(TypeMirror[]::new);
                for (int i = 0; i < typeParams.length; i++) {
                    var original = typeArguments.get(i);
                    var enriched = typeParams[i];
                    if (original != enriched) {
                        return types.getDeclaredType((TypeElement) types.asElement(t), typeParams);
                    }
                }
                return t;
            }

            @Override
            public TypeMirror visitError(ErrorType t, Void unused) {
                return t;
            }

            @Override
            public TypeMirror visitTypeVariable(TypeVariable t, Void unused) {
                return parameters.get(t);
            }

            @Override
            public TypeMirror visitWildcard(WildcardType t, Void unused) {
                return t;
            }

            @Override
            public TypeMirror visitExecutable(ExecutableType t, Void unused) {
                var returnType = t.getReturnType();
                var parameterTypes = t.getParameterTypes();
                var receiverType = t.getReceiverType();
                var thrownTypes = t.getThrownTypes();
                var enrichedReturnType = returnType.accept(this, null);
                var enrichedParameterTypes = parameterTypes.stream().map(pt -> pt.accept(this, null)).toList();
                var enrichedReceiverType = receiverType.accept(this, null);
                var enrichedThrownTypes = thrownTypes.stream().map(pt -> pt.accept(this, null)).toList();
                var element = (ExecutableElement) types.asElement(t);
                if (returnType != enrichedReturnType) {
                    return new GenericMethod(element, enrichedReturnType, enrichedParameterTypes, enrichedReceiverType, enrichedThrownTypes);
                }
                for (int i = 0; i < parameterTypes.size(); i++) {
                    var original = parameterTypes.get(i);
                    var enriched = enrichedParameterTypes.get(i);
                    if (original != enriched) {
                        return new GenericMethod(element, enrichedReturnType, enrichedParameterTypes, enrichedReceiverType, enrichedThrownTypes);
                    }
                }
                if (receiverType != enrichedReceiverType) {
                    return new GenericMethod(element, enrichedReturnType, enrichedParameterTypes, enrichedReceiverType, enrichedThrownTypes);
                }
                for (int i = 0; i < enrichedThrownTypes.size(); i++) {
                    var original = enrichedThrownTypes.get(i);
                    var enriched = enrichedThrownTypes.get(i);
                    if (original != enriched) {
                        return new GenericMethod(element, enrichedReturnType, enrichedParameterTypes, enrichedReceiverType, enrichedThrownTypes);
                    }
                }
                return t;
            }

            @Override
            public TypeMirror visitNoType(NoType t, Void unused) {
                return t;
            }

            @Override
            public TypeMirror visitUnknown(TypeMirror t, Void unused) {
                return t;
            }

            @Override
            public TypeMirror visitUnion(UnionType t, Void unused) {
                return t;
            }

            @Override
            public TypeMirror visitIntersection(IntersectionType t, Void unused) {
                return t;
            }
        }, null);
    }

}
