package ru.tinkoff.kora.validation.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class ValidAnnotationProcessor extends AbstractKoraProcessor {

    record ValidatorSpec(ValidMeta meta, TypeSpec spec, List<ParameterSpec> parameterSpecs) {}

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ValidMeta.VALID_TYPE.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final List<TypeElement> validatedElements = getValidatedTypeElements(processingEnv, roundEnv);
        for (var validatedElement : validatedElements) {
            try {
                var validMeta = getValidatorMetas(validatedElement);
                var validator = getValidatorSpecs(validMeta);
                final PackageElement packageElement = elements.getPackageOf(validator.meta().sourceElement());
                final JavaFile javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), validator.spec()).build();
                try {
                    javaFile.writeTo(processingEnv.getFiler());
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generated Validator for: " + validator.meta().source());
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error on writing file: " + e.getMessage(), validator.meta().sourceElement());
                }
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            }
        }


        return false;
    }

    private ValidatorSpec getValidatorSpecs(ValidMeta meta) {
        final List<ParameterSpec> parameterSpecs = new ArrayList<>();

        final TypeName typeName = meta.validator().contract().asPoetType(processingEnv);
        final TypeSpec.Builder validatorSpecBuilder = TypeSpec.classBuilder(meta.validator().implementation().simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(typeName)
            .addAnnotation(AnnotationSpec.builder(ClassName.get(Generated.class))
                .addMember("value", "$S", this.getClass().getCanonicalName())
                .build());

        final Map<ValidMeta.Constraint.Factory, String> constraintToFieldName = new HashMap<>();
        final Map<ValidMeta.Validated, String> validatedToFieldName = new HashMap<>();
        final List<CodeBlock> fieldConstraintBuilder = new ArrayList<>();
        for (int i = 0; i < meta.fields().size(); i++) {
            final ValidMeta.Field field = meta.fields().get(i);
            final String contextField = "_context" + i;
            fieldConstraintBuilder.add(CodeBlock.of("\nvar $L = context.addPath($S);", contextField, field.name()));

            if (field.isNotNull() && !field.isPrimitive()) {
                fieldConstraintBuilder.add(CodeBlock.of("""
                    if(value.$L == null) {
                        _violations.add($L.violates(\"Should be not null, but was null\"));
                        if(context.isFailFast()) {
                            return _violations;
                        }
                    }""", field.accessor(), contextField));
            }

            if (!field.isPrimitive()) {
                fieldConstraintBuilder.add(CodeBlock.of("if(value.$L != null) {$>", field.accessor()));
            }

            for (int j = 0; j < field.constraint().size(); j++) {
                final ValidMeta.Constraint constraint = field.constraint().get(j);
                final String suffix = i + "_" + j;
                final String constraintField = constraintToFieldName.computeIfAbsent(constraint.factory(), (k) -> "_constraint" + suffix);

                fieldConstraintBuilder.add(CodeBlock.of("""
                    _violations.addAll($L.validate(value.$L, $L));
                    if(context.isFailFast() && !_violations.isEmpty()) {
                        return _violations;
                    }""", constraintField, field.accessor(), contextField));
            }

            for (int j = 0; j < field.validates().size(); j++) {
                final ValidMeta.Validated validated = field.validates().get(j);
                final String suffix = i + "_" + j;
                final String validatorField = validatedToFieldName.computeIfAbsent(validated, (k) -> "_validator" + suffix);

                fieldConstraintBuilder.add(CodeBlock.of("""
                    _violations.addAll($L.validate(value.$L, $L));
                    if(context.isFailFast() && !_violations.isEmpty()) {
                        return _violations;
                    }""", validatorField, field.accessor(), contextField));
            }

            if (!field.isPrimitive()) {
                fieldConstraintBuilder.add(CodeBlock.of("$<}"));
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
            .returns(ValidMeta.Type.ofClass(List.class, List.of(ValidMeta.Type.ofName(ValidMeta.VIOLATION_TYPE.canonicalName()))).asPoetType(processingEnv))
            .addParameter(ParameterSpec.builder(meta.source().asPoetType(processingEnv), "value").build())
            .addParameter(ParameterSpec.builder(ValidMeta.Type.ofName(ValidMeta.CONTEXT_TYPE.canonicalName()).asPoetType(processingEnv), "context").build())
            .addCode(CodeBlock.join(List.of(
                    CodeBlock.of("""
                            if(value == null) {
                                return $T.of(context.violates(\"$L input value should be not null, but was null\"));
                            }
                                                            
                            final $T<Violation> _violations = new $T<>();""",
                        List.class, meta.source().simpleName(), List.class, ArrayList.class),
                    CodeBlock.join(fieldConstraintBuilder, "\n"),
                    CodeBlock.of("return _violations;")),
                "\n\n"));

        final TypeSpec validatorSpec = validatorSpecBuilder
            .addMethod(constructorSpecBuilder.build())
            .addMethod(validateMethodSpecBuilder.build())
            .build();

        return new ValidatorSpec(meta, validatorSpec, parameterSpecs);
    }

    private ValidMeta getValidatorMetas(TypeElement element) {
        final List<VariableElement> elementFields = getFields(element);
        final List<ValidMeta.Field> fields = new ArrayList<>();
        for (VariableElement fieldElement : elementFields) {
            final List<ValidMeta.Constraint> constraints = getValidatedByConstraints(processingEnv, fieldElement);
            final List<ValidMeta.Validated> validateds = getValidated(fieldElement);

            final boolean isNullable = CommonUtils.isNullable(fieldElement);
            if (!isNullable || !constraints.isEmpty() || !validateds.isEmpty()) {
                final boolean isPrimitive = fieldElement.asType() instanceof PrimitiveType;
                final boolean isRecord = element.getKind() == ElementKind.RECORD;
                final TypeMirror fieldType = ValidUtils.getBoxType(fieldElement.asType(), processingEnv);

                final ValidMeta.Field fieldMeta = new ValidMeta.Field(
                    ValidMeta.Type.ofType(fieldType),
                    fieldElement.getSimpleName().toString(),
                    isRecord,
                    isNullable,
                    isPrimitive,
                    constraints,
                    validateds);

                fields.add(fieldMeta);
            }
        }
        return new ValidMeta(ValidMeta.Type.ofType(element.asType()), element, fields);
    }

    private static List<ValidMeta.Constraint> getValidatedByConstraints(ProcessingEnvironment env, VariableElement field) {
        if (field.asType().getKind() == TypeKind.ERROR) {
            throw new ProcessingErrorException("Type is error in this round", field);
        }
        return ValidUtils.getValidatedByConstraints(env, field.asType(), field.getAnnotationMirrors());
    }

    private static List<VariableElement> getFields(TypeElement element) {
        return element.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(e -> e instanceof VariableElement)
            .map(e -> ((VariableElement) e))
            .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
            .toList();
    }

    private static List<ValidMeta.Validated> getValidated(VariableElement field) {
        if (field.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals(ValidMeta.VALID_TYPE.canonicalName()))) {
            return List.of(new ValidMeta.Validated(ValidMeta.Type.ofType(field.asType())));
        }

        return Collections.emptyList();
    }

    private List<TypeElement> getValidatedTypeElements(ProcessingEnvironment processEnv, RoundEnvironment roundEnv) {
        final TypeElement annotation = processEnv.getElementUtils().getTypeElement(ValidMeta.VALID_TYPE.canonicalName());

        return roundEnv.getElementsAnnotatedWith(annotation).stream()
            .filter(a -> a instanceof TypeElement)
            .map(element -> {
                if (element.getKind() == ElementKind.ENUM || element.getKind() == ElementKind.INTERFACE) {
                    throw new ProcessingErrorException("Validation can't be generated for: " + element.getKind(), element);
                }
                return ((TypeElement) element);
            })
            .toList();
    }
}
