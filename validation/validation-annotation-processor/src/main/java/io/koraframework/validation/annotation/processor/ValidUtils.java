package io.koraframework.validation.annotation.processor;

import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import java.util.*;
import java.util.stream.Collectors;

import static io.koraframework.validation.annotation.processor.ValidTypes.*;

public final class ValidUtils {

    public static List<ValidMeta.Constraint> getValidatedByConstraints(ProcessingEnvironment env, TypeMirror parameterType, List<? extends AnnotationMirror> annotations) {
        var constraints = getDirectConstraints(env, parameterType, annotations);
        var typeUse = new TypeUseValidation(new ArrayList<>(), new ArrayList<>());
        collectTypeUseValidation(env, parameterType, typeUse);
        constraints.addAll(typeUse.constraints());
        return constraints;
    }

    /**
     * @return {@code @Valid} put on type arguments of a container, like {@code List<@Valid Item>} or {@code Map<String, @Valid Item>}
     */
    public static List<ValidMeta.Validated> getTypeUseValidated(ProcessingEnvironment env, TypeMirror parameterType) {
        var typeUse = new TypeUseValidation(new ArrayList<>(), new ArrayList<>());
        collectTypeUseValidation(env, parameterType, typeUse);
        return typeUse.validated();
    }

    private record TypeUseValidation(List<ValidMeta.Constraint> constraints, List<ValidMeta.Validated> validated) {}

    private static void collectTypeUseValidation(ProcessingEnvironment env, TypeMirror parameterType, TypeUseValidation result) {
        final TypeMirror targetType = parameterType instanceof DeclaredType dt && jsonNullable.canonicalName().equals(dt.asElement().toString())
            ? dt.getTypeArguments().get(0)
            : parameterType;
        var rootType = withoutAnnotations(env, getBoxType(targetType, env));
        var root = ValidMeta.Type.ofElement(env.getTypeUtils().asElement(rootType), rootType);
        collectTypeUseValidation(env, root, targetType, List.of(), result);
    }

    private static void collectTypeUseValidation(ProcessingEnvironment env, ValidMeta.Type root, TypeMirror type, List<ValidMeta.Container> containers, TypeUseValidation result) {
        if (!(type instanceof DeclaredType declaredType)) {
            return;
        }
        var typeArguments = declaredType.getTypeArguments();
        final List<ValidMeta.Container> argumentContainers;
        if (typeArguments.size() == 1 && isSubtypeOf(env, declaredType, Iterable.class)) {
            argumentContainers = List.of(ValidMeta.Container.ITERABLE);
        } else if (typeArguments.size() == 2 && isSubtypeOf(env, declaredType, Map.class)) {
            argumentContainers = List.of(ValidMeta.Container.MAP_KEYS, ValidMeta.Container.MAP_VALUES);
        } else {
            return;
        }

        final TypeElement validatorElement = env.getElementUtils().getTypeElement(VALIDATOR_TYPE.canonicalName());
        final DeclaredType rootValidatorType = env.getTypeUtils().getDeclaredType(validatorElement, root.typeMirror());
        var rootValidator = ValidMeta.Type.ofElement(rootValidatorType.asElement(), rootValidatorType);
        for (int i = 0; i < typeArguments.size(); i++) {
            var typeArgument = typeArguments.get(i) instanceof WildcardType wildcard && wildcard.getExtendsBound() != null
                ? wildcard.getExtendsBound()
                : typeArguments.get(i);
            var argumentPath = new ArrayList<>(containers);
            argumentPath.add(argumentContainers.get(i));

            var annotations = typeArgument.getAnnotationMirrors();
            var elementType = withoutAnnotations(env, typeArgument);
            for (var constraint : getDirectConstraints(env, elementType, annotations)) {
                var factory = constraint.factory();
                result.constraints().add(new ValidMeta.Constraint(constraint.annotation(),
                    new ValidMeta.Constraint.Factory(factory.type(), rootValidator, factory.parameters(), List.copyOf(argumentPath))));
            }
            if (annotations.stream().anyMatch(a -> a.getAnnotationType().toString().equals(VALID_TYPE.canonicalName()))) {
                var target = ValidMeta.Type.ofElement(env.getTypeUtils().asElement(elementType), elementType);
                result.validated().add(new ValidMeta.Validated(target, root, List.copyOf(argumentPath)));
            }
            collectTypeUseValidation(env, root, typeArgument, argumentPath, result);
        }
    }

    private static boolean isSubtypeOf(ProcessingEnvironment env, DeclaredType type, Class<?> container) {
        var containerElement = env.getElementUtils().getTypeElement(container.getCanonicalName());
        return env.getTypeUtils().isAssignable(env.getTypeUtils().erasure(type), env.getTypeUtils().erasure(containerElement.asType()));
    }

    /**
     * Type annotations are not part of the validator type that is requested from the application graph
     */
    private static TypeMirror withoutAnnotations(ProcessingEnvironment env, TypeMirror type) {
        if (type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement typeElement) {
            var typeArguments = declaredType.getTypeArguments().stream()
                .map(argument -> withoutAnnotations(env, argument))
                .toArray(TypeMirror[]::new);
            return env.getTypeUtils().getDeclaredType(typeElement, typeArguments);
        }
        if (type instanceof WildcardType wildcard) {
            return env.getTypeUtils().getWildcardType(
                wildcard.getExtendsBound() == null ? null : withoutAnnotations(env, wildcard.getExtendsBound()),
                wildcard.getSuperBound() == null ? null : withoutAnnotations(env, wildcard.getSuperBound()));
        }
        return type;
    }

    private static List<ValidMeta.Constraint> getDirectConstraints(ProcessingEnvironment env, TypeMirror parameterType, List<? extends AnnotationMirror> annotations) {
        var innerAnnotationConstraints = annotations.stream()
            .flatMap(annotation -> annotation.getAnnotationType().asElement().getAnnotationMirrors().stream()
                .filter(validatedBy -> validatedBy.getAnnotationType().toString().equals(VALIDATED_BY_TYPE.canonicalName()))
                .flatMap(validatedBy -> validatedBy.getElementValues().entrySet().stream()
                    .filter(entry -> entry.getKey().getSimpleName().contentEquals("value"))
                    .map(en -> en.getValue().getValue())
                    .filter(ft -> ft instanceof DeclaredType)
                    .map(factoryType -> {
                        final DeclaredType factoryRawType = (DeclaredType) factoryType;
                        final Map<String, Object> parametersWithDefaults = env.getElementUtils().getElementValuesWithDefaults(annotation).entrySet().stream()
                            .collect(Collectors.toMap(
                                ae -> ae.getKey().getSimpleName().toString(),
                                ae -> castParameterValue(ae.getValue()),
                                (v1, v2) -> v2,
                                LinkedHashMap::new
                            ));

                        final Map<String, Object> parameters = new LinkedHashMap<>();
                        for (Element parameter : annotation.getAnnotationType().asElement().getEnclosedElements()) {
                            if (parameter instanceof ExecutableElement ep) {
                                final String parameterName = ep.getSimpleName().toString();
                                final Object parameterValue = parametersWithDefaults.get(parameterName);
                                parameters.put(parameterName, parameterValue);
                            }
                        }

                        if (parameters.size() > 0) {
                            factoryRawType.asElement().getEnclosedElements()
                                .stream()
                                .filter(e -> e.getKind() == ElementKind.METHOD)
                                .map(ExecutableElement.class::cast)
                                .filter(e -> e.getSimpleName().contentEquals("create"))
                                .filter(e -> e.getParameters().size() == parameters.size())
                                .findFirst()
                                .orElseThrow(() -> new ProcessingErrorException("Expected " + factoryRawType.asElement().getSimpleName()
                                                                                + "#create() method with " + parameters.size() + " parameters, but was didn't find such", factoryRawType.asElement(), annotation));
                        }

                        final TypeMirror targetType;
                        if (parameterType instanceof DeclaredType dt && jsonNullable.canonicalName().equals(dt.asElement().toString())) {
                            targetType = dt.getTypeArguments().get(0);
                        } else {
                            targetType = parameterType;
                        }

                        final TypeMirror fieldType = getBoxType(targetType, env);
                        final DeclaredType factoryDeclaredType = ((DeclaredType) factoryRawType.asElement().asType()).getTypeArguments().isEmpty()
                            ? env.getTypeUtils().getDeclaredType((TypeElement) factoryRawType.asElement())
                            : env.getTypeUtils().getDeclaredType((TypeElement) factoryRawType.asElement(), fieldType);

                        final TypeElement validatorElement = env.getElementUtils().getTypeElement(VALIDATOR_TYPE.canonicalName());
                        final DeclaredType validatorType = env.getTypeUtils().getDeclaredType(validatorElement, fieldType);

                        final ValidMeta.Constraint.Factory constraintFactory = new ValidMeta.Constraint.Factory(
                            ValidMeta.Type.ofElement(factoryDeclaredType.asElement(), factoryDeclaredType),
                            ValidMeta.Type.ofElement(validatorType.asElement(), validatorType), parameters);

                        final ValidMeta.Type annotationType = ValidMeta.Type.ofElement(annotation.getAnnotationType().asElement(), annotation.getAnnotationType());
                        return new ValidMeta.Constraint(annotationType, constraintFactory);
                    })))
            .toList();

        var selfAnnotationConstraints = annotations.stream()
            .filter(validatedBy -> validatedBy.getAnnotationType().toString().equals(VALIDATED_BY_TYPE.canonicalName()))
            .flatMap(validatedBy -> validatedBy.getElementValues().entrySet().stream()
                .filter(entry -> entry.getKey().getSimpleName().contentEquals("value"))
                .map(en -> en.getValue().getValue())
                .filter(ft -> ft instanceof DeclaredType)
                .map(factoryType -> {
                    final DeclaredType factoryRawType = (DeclaredType) factoryType;

                    final TypeMirror fieldType = getBoxType(parameterType, env);
                    final DeclaredType factoryDeclaredType = ((DeclaredType) factoryRawType.asElement().asType()).getTypeArguments().isEmpty()
                        ? env.getTypeUtils().getDeclaredType((TypeElement) factoryRawType.asElement())
                        : env.getTypeUtils().getDeclaredType((TypeElement) factoryRawType.asElement(), fieldType);

                    final TypeElement validatorElement = env.getElementUtils().getTypeElement(VALIDATOR_TYPE.canonicalName());
                    final DeclaredType validatorType = env.getTypeUtils().getDeclaredType(validatorElement, fieldType);

                    final ValidMeta.Constraint.Factory constraintFactory = new ValidMeta.Constraint.Factory(
                        ValidMeta.Type.ofElement(factoryDeclaredType.asElement(), factoryDeclaredType),
                        ValidMeta.Type.ofElement(validatorType.asElement(), validatorType), Collections.emptyMap());

                    final ValidMeta.Type annotationType = ValidMeta.Type.ofElement(validatedBy.getAnnotationType().asElement(), validatedBy.getAnnotationType());
                    return new ValidMeta.Constraint(annotationType, constraintFactory);
                }))
            .toList();

        var constraints = new ArrayList<>(innerAnnotationConstraints);
        constraints.addAll(selfAnnotationConstraints);
        return constraints;
    }

    private static Object castParameterValue(AnnotationValue value) {
        if (value.getValue() instanceof String) {
            return value.toString();
        }

        if (value.getValue() instanceof Number) {
            return value.toString();
        }

        if (value.getValue() instanceof VariableElement ve) {
            return ve.asType().toString() + "." + value.getValue();
        }

        if (value.getValue() instanceof List<?> list) {
            return list.stream()
                .map(v -> v instanceof AnnotationValue
                    ? castParameterValue((AnnotationValue) v)
                    : v.toString())
                .toList();
        }

        if (value.toString().startsWith("{") && value.toString().endsWith("}")) {
            return "new String[] " + value;
        }

        return value.toString();
    }

    public static TypeMirror getBoxType(TypeMirror mirror, ProcessingEnvironment env) {
        return (mirror instanceof PrimitiveType primitive)
            ? env.getTypeUtils().boxedClass(primitive).asType()
            : mirror;
    }

    public static boolean isNotNull(AnnotatedConstruct element) {
        var isNotNull = element.getAnnotationMirrors()
            .stream()
            .map(a -> a.getAnnotationType().toString())
            .anyMatch(a -> a.endsWith(".Nonnull") || a.endsWith(".NotNull") || a.endsWith(".NonNull"));

        if (isNotNull) {
            return true;
        }

        if (element instanceof ExecutableElement method) {
            if (method.getReturnType().getKind().isPrimitive()) {
                return false;
            }
            return isNotNull(method.getReturnType());
        }

        if (element instanceof VariableElement ve) {
            var type = ve.asType();
            if (type.getKind().isPrimitive()) {
                return false;
            }
            return isNotNull(type);
        }

        if (element instanceof RecordComponentElement rce) {
            return rce.getEnclosingElement().getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .filter(e -> e.getSimpleName().contentEquals(rce.getSimpleName()))
                .anyMatch(CommonUtils::isNullable);
        }
        return false;
    }
}
