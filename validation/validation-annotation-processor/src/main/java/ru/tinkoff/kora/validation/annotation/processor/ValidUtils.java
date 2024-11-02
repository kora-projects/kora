package ru.tinkoff.kora.validation.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.validation.annotation.processor.ValidTypes.VALIDATED_BY_TYPE;
import static ru.tinkoff.kora.validation.annotation.processor.ValidTypes.jsonNullable;

public final class ValidUtils {

    public static List<ValidMeta.Constraint> getValidatedByConstraints(ProcessingEnvironment env, TypeMirror parameterType, List<? extends AnnotationMirror> annotations) {
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

                        final ValidMeta.Type factoryGenericType = ValidMeta.Type.ofType(factoryDeclaredType);
                        final ValidMeta.Constraint.Factory constraintFactory = new ValidMeta.Constraint.Factory(factoryGenericType, parameters);

                        final ValidMeta.Type annotationType = ValidMeta.Type.ofType(annotation.getAnnotationType().asElement().asType());
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

                    final ValidMeta.Type factoryGenericType = ValidMeta.Type.ofType(factoryDeclaredType);
                    final ValidMeta.Constraint.Factory constraintFactory = new ValidMeta.Constraint.Factory(factoryGenericType, Collections.emptyMap());

                    final ValidMeta.Type annotationType = ValidMeta.Type.ofType(validatedBy.getAnnotationType().asElement().asType());
                    return new ValidMeta.Constraint(annotationType, constraintFactory);
                }))
            .toList();

        var constraints = new ArrayList<>(innerAnnotationConstraints);
        constraints.addAll(selfAnnotationConstraints);
        constraints.sort(Comparator.comparing(c -> c.factory().type().toString()));
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

        if (value.getValue() instanceof List<?>) {
            return ((List<?>) value).stream()
                .map(v -> v instanceof AnnotationValue
                    ? castParameterValue((AnnotationValue) v)
                    : v.toString())
                .toList();
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
            .anyMatch(a -> a.getAnnotationType().toString().endsWith(".Nonnull") || a.getAnnotationType().toString().endsWith(".NotNull"));

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
