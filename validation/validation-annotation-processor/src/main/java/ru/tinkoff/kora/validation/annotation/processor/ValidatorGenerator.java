package ru.tinkoff.kora.validation.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.annotation.processor.common.SealedTypeUtils;

import javax.annotation.Nullable;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ValidatorGenerator {
    private final Types types;
    private final Elements elements;
    private final Filer filer;
    private final ProcessingEnvironment processingEnv;

    public ValidatorGenerator(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.filer = env.getFiler();
        this.processingEnv = env;
    }

    public void generateFor(TypeElement validatedElement) {
        if (validatedElement.getKind().isInterface()) {
            this.generateForSealed(validatedElement);
            return;
        }
        var validMeta = getValidatorMetas(validatedElement);
        var validator = getValidatorSpecs(validMeta);
        final PackageElement packageElement = elements.getPackageOf(validator.meta().sourceElement());
        final JavaFile javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), validator.spec()).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void generateForSealed(TypeElement validatedElement) {
        assert validatedElement.getModifiers().contains(Modifier.SEALED);
        var validatedTypeName = TypeName.get(validatedElement.asType());
        var validatorType = ParameterizedTypeName.get(ValidMeta.VALIDATOR_TYPE, validatedTypeName);

        var validatorSpecBuilder = TypeSpec.classBuilder(CommonUtils.getOuterClassesAsPrefix(validatedElement) + validatedElement.getSimpleName() + "_Validator")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(validatorType)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", "$S", this.getClass().getCanonicalName())
                .build());
        for (var typeParameter : validatedElement.getTypeParameters()) {
            validatorSpecBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var method = MethodSpec.methodBuilder("validate")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override.class)
            .returns(ParameterizedTypeName.get(CommonClassNames.list, ValidMeta.VIOLATION_TYPE))
            .addParameter(ParameterSpec.builder(validatedTypeName, "value").addAnnotation(Nullable.class).build())
            .addParameter(ValidMeta.CONTEXT_TYPE, "context");

        var subclasses = SealedTypeUtils.collectFinalPermittedSubtypes(types, elements, validatedElement);
        for (int i = 0; i < subclasses.size(); i++) { // TODO recursive subclasses
            var permittedSubclass = subclasses.get(i);
            var name = "_validator" + (i + 1);
            var subclassTypeName = TypeName.get(permittedSubclass.asType());
            var fieldValidator = ParameterizedTypeName.get(ValidMeta.VALIDATOR_TYPE, subclassTypeName);
            validatorSpecBuilder.addField(fieldValidator, name, Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(fieldValidator, name);
            constructor.addStatement("this.$N = $N;", name, name);
            if (i > 0) {
                method.nextControlFlow("else if (value instanceof $T casted)", subclassTypeName);
            } else {
                method.beginControlFlow("if (value instanceof $T casted)", subclassTypeName);
            }
            method.addStatement("return $N.validate(casted, context)", name);
        }
        validatorSpecBuilder.addMethod(method.endControlFlow().addStatement("throw new $T()", IllegalStateException.class).build());
        validatorSpecBuilder.addMethod(constructor.build());
        var javaFile = JavaFile.builder(elements.getPackageOf(validatedElement).getQualifiedName().toString(), validatorSpecBuilder.build()).build();
        try {
            javaFile.writeTo(this.filer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ValidAnnotationProcessor.ValidatorSpec getValidatorSpecs(ValidMeta meta) {
        final List<ParameterSpec> parameterSpecs = new ArrayList<>();

        final TypeName typeName = meta.validator().contract().asPoetType(processingEnv);
        final TypeSpec.Builder validatorSpecBuilder = TypeSpec.classBuilder(meta.validator().implementation().simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(typeName)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
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

        return new ValidAnnotationProcessor.ValidatorSpec(meta, validatorSpec, parameterSpecs);
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
}
