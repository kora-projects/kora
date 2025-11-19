package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.ClassName;
import jakarta.annotation.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.util.ArrayList;
import java.util.List;

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
        return findSupertype(pe, type, List.of(), expectedSupertype);
    }

    @Nullable
    private static DeclaredType findSupertype(@Nullable ProcessingEnvironment pe,
                                              @Nullable DeclaredType type,
                                              List<ParameterMirror> superArgs,
                                              ClassName expectedSupertype) {
        if (type == null) {
            return null;
        }

        var typeElement = (TypeElement) type.asElement();
        if (typeElement.getQualifiedName().contentEquals(expectedSupertype.canonicalName())) {
            var enrichedType = enrich(pe, type, superArgs);
            return enrichedType;
        }

        var currentArgs = getTypeArguments(type);
        var resultArgs = combineParams(pe, superArgs, currentArgs);

        var supertype = typeElement.getSuperclass();
        if (supertype instanceof DeclaredType dt) {
            var supertypeResult = findSupertype(pe, dt, resultArgs, expectedSupertype);
            if (supertypeResult != null) {
                return supertypeResult;
            }
        }

        for (var anInterface : typeElement.getInterfaces()) {
            if (anInterface instanceof DeclaredType dt) {
                var supertypeResult = findSupertype(pe, dt, resultArgs, expectedSupertype);
                if (supertypeResult != null) {
                    return supertypeResult;
                }
            }
        }

        return null;
    }

    private static List<ParameterMirror> getTypeArguments(@Nullable DeclaredType type) {
        if (type == null) {
            return new ArrayList<>();
        }

        DeclaredType genericType = (DeclaredType) type.asElement().asType();

        List<ParameterMirror> genericArgs = new ArrayList<>();
        for (int i = 0; i < type.getTypeArguments().size(); i++) {
            var realArg = type.getTypeArguments().get(i);
            var genericArg = genericType.getTypeArguments().get(i);
            if (genericArg instanceof TypeVariable tv) {
                genericArgs.add(new ParameterMirror(realArg, tv));
            }
        }

        return genericArgs;
    }

    record ParameterMirror(TypeMirror realType, TypeVariable genericType) {}

    private static List<ParameterMirror> combineParams(@Nullable ProcessingEnvironment pe,
                                                       List<ParameterMirror> superTypes,
                                                       List<ParameterMirror> currentTypes) {
        var result = new ArrayList<ParameterMirror>();
        for (var currentType : currentTypes) {
            var initSize = result.size();
            for (var superType : superTypes) {
                if (pe != null && pe.getTypeUtils().isSameType(superType.genericType(), currentType.realType())) {
                    result.add(new ParameterMirror(superType.realType(), currentType.genericType()));
                    break;
                } else if (superType.genericType().toString().equals(currentType.realType().toString())) {
                    result.add(new ParameterMirror(superType.realType(), currentType.genericType()));
                    break;
                }
            }
            if (result.size() == initSize) {
                result.add(currentType);
            }
        }

        return result;
    }

    private static DeclaredType enrich(@Nullable ProcessingEnvironment pe,
                                       DeclaredType type,
                                       List<ParameterMirror> superParams) {
        if (pe == null) {
            return type;
        }

        if (!type.getTypeArguments().isEmpty()) {
            var argsForReplace = new ArrayList<TypeMirror>();
            var argsReplaced = new ArrayList<TypeMirror>();
            for (var currentArg : type.getTypeArguments()) {
                var argsInit = argsReplaced.size();
                if (currentArg instanceof DeclaredType dt) {
                    var enrichedCurArgType = enrich(pe, dt, superParams);
                    if (enrichedCurArgType != currentArg) {
                        argsForReplace.add(enrichedCurArgType);
                        argsReplaced.add(enrichedCurArgType);
                    }
                } else if (currentArg instanceof TypeVariable tv) {
                    for (var superParam : superParams) {
                        if (pe.getTypeUtils().isSameType(superParam.genericType(), tv)) {
                            argsForReplace.add(superParam.realType());
                            argsReplaced.add(superParam.realType());
                            break;
                        }
                    }
                }

                if (argsInit == argsReplaced.size()) {
                    argsForReplace.add(currentArg);
                }
            }

            if (!argsReplaced.isEmpty()) {
                return pe.getTypeUtils().getDeclaredType(((TypeElement) type.asElement()), argsForReplace.toArray(TypeMirror[]::new));
            }
        }

        return type;
    }
}
