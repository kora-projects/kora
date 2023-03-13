package ru.tinkoff.kora.validation.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

public final class ValidUtils {

    public static List<ValidMeta.Constraint> getValidatedByConstraints(ProcessingEnvironment env, TypeMirror parameterType, List<? extends AnnotationMirror> annotations) {
        var innerAnnotationConstraints = annotations.stream()
            .flatMap(annotation -> annotation.getAnnotationType().asElement().getAnnotationMirrors().stream()
                .filter(validatedBy -> validatedBy.getAnnotationType().toString().equals(ValidMeta.VALIDATED_BY_TYPE.canonicalName()))
                .flatMap(validatedBy -> validatedBy.getElementValues().entrySet().stream()
                    .filter(entry -> entry.getKey().getSimpleName().contentEquals("value"))
                    .map(en -> en.getValue().getValue())
                    .filter(ft -> ft instanceof DeclaredType)
                    .map(factoryType -> {
                        final DeclaredType factoryRawType = (DeclaredType) factoryType;
                        final Map<String, Object> parameters = env.getElementUtils().getElementValuesWithDefaults(annotation).entrySet().stream()
                            .collect(Collectors.toMap(
                                ae -> ae.getKey().getSimpleName().toString(),
                                ae -> castParameterValue(ae.getValue()),
                                (v1, v2) -> v2,
                                LinkedHashMap::new
                            ));

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

                        final TypeMirror fieldType = getBoxType(parameterType, env);
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
            .filter(validatedBy -> validatedBy.getAnnotationType().toString().equals(ValidMeta.VALIDATED_BY_TYPE.canonicalName()))
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
}
