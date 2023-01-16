package ru.tinkoff.kora.validation.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.KoraApp;

import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class ValidationAnnotationProcessor extends AbstractKoraProcessor {

    record ValidatorSpec(ValidatorMeta meta, TypeSpec spec, List<ParameterSpec> parameterSpecs) {}

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("ru.tinkoff.kora.validation.common.annotation.Validated", KoraApp.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            final List<TypeElement> validatedElements = getValidatedElements(processingEnv, roundEnv);
            final List<ValidatorMeta> validatorMetas = getValidatorMetas(validatedElements);
            final List<ValidatorSpec> validatorSpecs = getValidatorSpecs(validatorMetas);
            for (ValidatorSpec validator : validatorSpecs) {
                final PackageElement packageElement = elements.getPackageOf(validator.meta().sourceElement());
                final JavaFile javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), validator.spec()).build();
                try {
                    javaFile.writeTo(processingEnv.getFiler());
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generated Validator for: " + validator.meta().source(), validator.meta().sourceElement());
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error on writing file: " + e.getMessage(), validator.meta().sourceElement());
                }
            }
        } catch (ValidationElementException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.getElement());
            return true;
        }

        return false;
    }

    private List<ValidatorSpec> getValidatorSpecs(List<ValidatorMeta> metas) {
        final List<ValidatorSpec> specs = new ArrayList<>();
        for (ValidatorMeta meta : metas) {
            final List<ParameterSpec> parameterSpecs = new ArrayList<>();

            final TypeName typeName = meta.validator().contract().asPoetType(processingEnv);
            final TypeSpec.Builder validatorSpecBuilder = TypeSpec.classBuilder(meta.validator().implementation().simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(typeName)
                .addAnnotation(AnnotationSpec.builder(ClassName.get(Generated.class))
                    .addMember("value", "$S", this.getClass().getCanonicalName())
                    .build());

            final Map<ValidatorMeta.Constraint.Factory, String> constraintToFieldName = new HashMap<>();
            final Map<ValidatorMeta.Validated, String> validatedToFieldName = new HashMap<>();
            final List<CodeBlock> contextBuilder = new ArrayList<>();
            final List<CodeBlock> constraintBuilder = new ArrayList<>();
            for (int i = 0; i < meta.fields().size(); i++) {
                final ValidatorMeta.Field field = meta.fields().get(i);
                final String contextField = "_context" + i;
                contextBuilder.add(CodeBlock.of("var $L = context.addPath($S);", contextField, field.name()));

                if (field.isNotNull() && !field.isPrimitive()) {
                    contextBuilder.add(CodeBlock.of("""
                        if(value.$L == null) {
                            _violations.add($L.violates(\"Should be not null, but was null\"));
                            if(context.isFailFast()) {
                                return _violations;
                            }
                        }""", field.accessor(), contextField));
                }

                for (int j = 0; j < field.constraint().size(); j++) {
                    final ValidatorMeta.Constraint constraint = field.constraint().get(j);
                    final String suffix = i + "_" + j;
                    final String constraintField = constraintToFieldName.computeIfAbsent(constraint.factory(), (k) -> "_constraint" + suffix);

                    constraintBuilder.add(CodeBlock.of("""
                        _violations.addAll($L.validate(value.$L, $L));
                        if(context.isFailFast() && !_violations.isEmpty()) {
                            return _violations;
                        }""", constraintField, field.accessor(), contextField));
                }

                for (int j = 0; j < field.validates().size(); j++) {
                    final ValidatorMeta.Validated validated = field.validates().get(j);
                    final String suffix = i + "_" + j;
                    final String validatorField = validatedToFieldName.computeIfAbsent(validated, (k) -> "_validator" + suffix);

                    constraintBuilder.add(CodeBlock.of("""
                        if(value.$L != null) {
                            _violations.addAll($L.validate(value.$L, $L));
                            if(context.isFailFast()) {
                                return _violations;
                            }
                        }""", field.accessor(), validatorField, field.accessor(), contextField));
                }
            }

            final MethodSpec.Builder constructorSpecBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
            for (var factoryToField : constraintToFieldName.entrySet()) {
                var factory = factoryToField.getKey();
                final String fieldName = factoryToField.getValue();
                final String createParameters = factory.parameters().values().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));

                validatorSpecBuilder.addField(FieldSpec.builder(
                    factory.validator().asPoetType(processingEnv),
                    fieldName,
                    Modifier.PRIVATE, Modifier.FINAL).build());

                final ParameterSpec parameterSpec = ParameterSpec.builder(factory.type().asPoetType(processingEnv), fieldName).build();
                parameterSpecs.add(parameterSpec);
                constructorSpecBuilder
                    .addParameter(parameterSpec)
                    .addStatement("this.$L = $L.create($L)", fieldName, fieldName, createParameters);
            }

            for (var validatedToField : validatedToFieldName.entrySet()) {
                final String fieldName = validatedToField.getValue();
                final TypeName fieldType = validatedToField.getKey().validator().asPoetType(processingEnv);
                validatorSpecBuilder.addField(FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL).build());

                final ParameterSpec parameterSpec = ParameterSpec.builder(fieldType, fieldName).build();
                parameterSpecs.add(parameterSpec);
                constructorSpecBuilder
                    .addParameter(parameterSpec)
                    .addStatement("this.$L = $L", fieldName, fieldName);
            }

            final MethodSpec.Builder validateMethodSpecBuilder = MethodSpec.methodBuilder("validate")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ValidatorMeta.Type.ofClass(List.class, List.of(ValidatorMeta.Type.ofName("ru.tinkoff.kora.validation.common.Violation"))).asPoetType(processingEnv))
                .addParameter(ParameterSpec.builder(meta.source().asPoetType(processingEnv), "value").build())
                .addParameter(ParameterSpec.builder(ValidatorMeta.Type.ofName("ru.tinkoff.kora.validation.common.ValidationContext").asPoetType(processingEnv), "context").build())
                .addCode(CodeBlock.join(List.of(
                        CodeBlock.of("""
                                if(value == null) {
                                    return $T.of(context.violates(\"Input value is null\"));
                                }
                                                                
                                final $T<Violation> _violations = new $T<>();
                                """,
                            List.class, List.class, ArrayList.class),
                        CodeBlock.join(contextBuilder, "\n"),
                        CodeBlock.join(constraintBuilder, "\n"),
                        CodeBlock.of("return _violations;")),
                    "\n\n"));

            final TypeSpec validatorSpec = validatorSpecBuilder
                .addMethod(constructorSpecBuilder.build())
                .addMethod(validateMethodSpecBuilder.build())
                .build();

            specs.add(new ValidatorSpec(meta, validatorSpec, parameterSpecs));
        }

        return specs;
    }

    private List<ValidatorMeta> getValidatorMetas(List<TypeElement> typeElements) {
        final List<ValidatorMeta> validatorMetas = new ArrayList<>();
        for (TypeElement element : typeElements) {
            final List<VariableElement> elementFields = getFields(element);
            final List<ValidatorMeta.Field> fields = new ArrayList<>();
            for (VariableElement fieldElement : elementFields) {
                final List<ValidatorMeta.Constraint> constraints = getConstraints(processingEnv, fieldElement);
                final List<ValidatorMeta.Validated> validateds = getValidated(fieldElement);

                final boolean isNullable = CommonUtils.isNullable(fieldElement);
                if (!isNullable || !constraints.isEmpty() || !validateds.isEmpty()) {
                    final boolean isPrimitive = fieldElement.asType() instanceof PrimitiveType;
                    final boolean isRecord = element.getKind() == ElementKind.RECORD;
                    final TypeMirror fieldType = getBoxType(fieldElement.asType(), processingEnv);

                    final ValidatorMeta.Field fieldMeta = new ValidatorMeta.Field(
                        ValidatorMeta.Type.ofType(fieldType),
                        fieldElement.getSimpleName().toString(),
                        isRecord,
                        isNullable,
                        isPrimitive,
                        constraints,
                        validateds);

                    fields.add(fieldMeta);
                }
            }

            final ValidatorMeta meta = new ValidatorMeta(ValidatorMeta.Type.ofType(element.asType()), element, fields);
            validatorMetas.add(meta);
        }

        return validatorMetas;
    }

    private static List<ValidatorMeta.Constraint> getConstraints(ProcessingEnvironment env, VariableElement field) {
        return field.getAnnotationMirrors().stream()
            .flatMap(annotation -> annotation.getAnnotationType().asElement().getAnnotationMirrors().stream()
                .filter(innerAnnotation -> innerAnnotation.getAnnotationType().toString().equals("ru.tinkoff.kora.validation.common.annotation.ValidatedBy"))
                .flatMap(constainted -> constainted.getElementValues().entrySet().stream()
                    .filter(entry -> entry.getKey().getSimpleName().contentEquals("value"))
                    .map(en -> en.getValue().getValue())
                    .filter(v -> v instanceof DeclaredType)
                    .map(v -> {
                        final DeclaredType factoryRawType = (DeclaredType) v;
                        final Map<String, Object> parameters = annotation.getElementValues().entrySet().stream()
                            .collect(Collectors.toMap(
                                ae -> ae.getKey().getSimpleName().toString(),
                                ae -> castParameterValue(ae.getValue()),
                                (v1, v2) -> v2,
                                LinkedHashMap::new
                            ));

                        final TypeMirror fieldType = getBoxType(field.asType(), env);
                        final DeclaredType factoryDeclaredType = ((DeclaredType) factoryRawType.asElement().asType()).getTypeArguments().isEmpty()
                            ? env.getTypeUtils().getDeclaredType((TypeElement) factoryRawType.asElement())
                            : env.getTypeUtils().getDeclaredType((TypeElement) factoryRawType.asElement(), fieldType);

                        final ValidatorMeta.Type factoryGenericType = ValidatorMeta.Type.ofType(factoryDeclaredType);
                        final ValidatorMeta.Constraint.Factory constraintFactory = new ValidatorMeta.Constraint.Factory(factoryGenericType, parameters);

                        final ValidatorMeta.Type annotationType = ValidatorMeta.Type.ofType(annotation.getAnnotationType().asElement().asType());
                        return new ValidatorMeta.Constraint(annotationType, constraintFactory);
                    })))
            .sorted(Comparator.comparing(c -> c.factory().type().toString()))
            .collect(Collectors.toList());
    }

    private static List<VariableElement> getFields(TypeElement element) {
        return element.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(e -> e instanceof VariableElement)
            .map(e -> ((VariableElement) e))
            .toList();
    }

    private static List<ValidatorMeta.Validated> getValidated(VariableElement field) {
        if (field.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals("ru.tinkoff.kora.validation.common.annotation.Validated"))) {
            return List.of(new ValidatorMeta.Validated(ValidatorMeta.Type.ofType(field.asType())));
        }

        return Collections.emptyList();
    }

    private List<TypeElement> getValidatedElements(ProcessingEnvironment processEnv, RoundEnvironment roundEnv) {
        final TypeElement annotation = processEnv.getElementUtils().getTypeElement("ru.tinkoff.kora.validation.common.annotation.Validated");

        return roundEnv.getElementsAnnotatedWith(annotation).stream()
            .filter(a -> a instanceof TypeElement)
            .map(element -> {
                if (element.getKind() == ElementKind.ENUM || element.getKind() == ElementKind.INTERFACE) {
                    throw new ValidationElementException("Validation can't be generated for: " + element.getKind(), element);
                }
                return ((TypeElement) element);
            })
            .toList();
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

    private static TypeMirror getBoxType(TypeMirror mirror, ProcessingEnvironment env) {
        return (mirror instanceof PrimitiveType primitive)
            ? env.getTypeUtils().boxedClass(primitive).asType()
            : mirror;
    }
}
