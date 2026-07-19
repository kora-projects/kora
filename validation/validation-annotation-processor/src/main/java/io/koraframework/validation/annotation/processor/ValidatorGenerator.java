package io.koraframework.validation.annotation.processor;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.*;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static io.koraframework.validation.annotation.processor.ValidTypes.*;
import static io.koraframework.validation.annotation.processor.ValidUtils.isNotNull;

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
        if (validatedElement.getKind().isInterface() && validatedElement.getModifiers().contains(Modifier.SEALED)) {
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
        var validatorType = ParameterizedTypeName.get(VALIDATOR_TYPE, validatedTypeName);

        var validatorSpecBuilder = TypeSpec.classBuilder(NameUtils.generatedType(validatedElement, VALIDATOR_TYPE))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(validatorType)
            .addAnnotation(AnnotationUtils.generated(ValidatorGenerator.class))
            .addOriginatingElement(validatedElement);
        for (var typeParameter : validatedElement.getTypeParameters()) {
            validatorSpecBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var method = MethodSpec.methodBuilder("validate")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override.class)
            .returns(ParameterizedTypeName.get(CommonClassNames.list, VIOLATION_TYPE))
            .addParameter(ParameterSpec.builder(validatedTypeName, "value").addAnnotation(Nullable.class).build())
            .addParameter(CONTEXT_TYPE, "context");

        var subclasses = SealedTypeUtils.collectFinalPermittedSubtypes(types, elements, validatedElement);
        for (int i = 0; i < subclasses.size(); i++) { // TODO recursive subclasses
            var permittedSubclass = subclasses.get(i);
            var name = "_validator" + (i + 1);
            var subclassTypeName = TypeName.get(permittedSubclass.asType());
            var fieldValidator = ParameterizedTypeName.get(VALIDATOR_TYPE, subclassTypeName);
            validatorSpecBuilder.addField(fieldValidator, name, Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(fieldValidator, name);
            constructor.addStatement("this.$N = $N", name, name);
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

        final TypeName typeName = meta.validator(processingEnv).asPoetType();
        final TypeSpec.Builder validatorSpecBuilder = TypeSpec.classBuilder(meta.validatorImplementationName())
            .addOriginatingElement(meta.sourceElement())
            .addAnnotation(AnnotationUtils.generated(ValidatorGenerator.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(typeName);

        for (TypeParameterElement typeParameter : meta.sourceElement().getTypeParameters()) {
            validatorSpecBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        final Map<ValidMeta.Constraint.Factory, String> constraintToFieldName = new LinkedHashMap<>();
        final Map<ValidMeta.Validated, String> validatedToFieldName = new LinkedHashMap<>();
        final List<CodeBlock> fieldConstraintBuilder = new ArrayList<>();
        for (int i = 1; i <= meta.fields().size(); i++) {
            final ValidMeta.Field field = meta.fields().get(i - 1);
            final String contextField = "_context" + i;

            final boolean isNotNullable = !field.isNullable() && !field.isPrimitive();
            var checkBuilder = CodeBlock.builder();
            if (field.isJsonNullable() && field.isNotNull()) {
                checkBuilder.beginControlFlow("if (value.$L == null || !value.$L.isDefined() || value.$L.isNull())",
                    field.accessor(), field.accessor(), field.accessor());
            } else if (isNotNullable) {
                checkBuilder.beginControlFlow("if (value.$L == null)", field.accessor());
            }

            if ((field.isJsonNullable() && field.isNotNull()) || isNotNullable) {
                checkBuilder.addStatement("var $L = context.addPath($S)", contextField, field.name());
                checkBuilder.beginControlFlow("if (context.isFailFast())");
                checkBuilder.addStatement("return $T.of($L.violates(\"Must be non null, but was null\"))", List.class, contextField);
                checkBuilder.nextControlFlow("else");
                checkBuilder.addStatement("_violations.add($L.violates(\"Must be non null, but was null\"))", contextField);
                checkBuilder.endControlFlow();
            }

            if (!field.constraint().isEmpty() || !field.validates().isEmpty()) {
                if (!field.isPrimitive()) {
                    if (field.isJsonNullable() && field.isNotNull()) {
                        checkBuilder.nextControlFlow("else");
                    } else if (isNotNullable && field.isJsonNullable()) {
                        checkBuilder.nextControlFlow("else if (value.$L.isDefined())", field.accessor());
                    } else if (isNotNullable) {
                        checkBuilder.nextControlFlow("else");
                    } else if (field.isJsonNullable()) {
                        checkBuilder.beginControlFlow("if (value.$L != null && value.$L.isDefined())", field.accessor(), field.accessor());
                    } else {
                        checkBuilder.beginControlFlow("if (value.$L != null)", field.accessor());
                    }
                }

                checkBuilder.addStatement("var $L = context.addPath($S)", contextField, field.name());
                for (int j = 1; j <= field.constraint().size(); j++) {
                    final ValidMeta.Constraint constraint = field.constraint().get(j - 1);
                    final String suffix = i + "_" + j;
                    final String constraintField = constraintToFieldName.computeIfAbsent(constraint.factory(), (k) -> "_constraint" + suffix);
                    final String constraintResultField = "_constraintResult_" + suffix;
                    checkBuilder.add("""
                        var $N = $L.validate(value.$L, $L);
                        if (!$N.isEmpty() && context.isFailFast()) {
                            return $N;
                        } else {
                            _violations.addAll($N);
                        }
                        """, constraintResultField, constraintField, field.valueAccessor(), contextField, constraintResultField, constraintResultField, constraintResultField);
                }

                for (int j = 1; j <= field.validates().size(); j++) {
                    final ValidMeta.Validated validated = field.validates().get(j - 1);
                    final String suffix = i + "_" + j;
                    final String validatorField = validatedToFieldName.computeIfAbsent(validated, (k) -> "_validator" + suffix);
                    final String validatorResultField = "_validatorResult_" + suffix;

                    checkBuilder.add("""
                        var $N = $L.validate(value.$L, $L);
                        if (!$N.isEmpty() && context.isFailFast()) {
                            return $N;
                        } else {
                            _violations.addAll($N);
                        }
                        """, validatorResultField, validatorField, field.valueAccessor(), contextField, validatorResultField, validatorResultField, validatorResultField);
                }

                if (!field.isPrimitive()) {
                    checkBuilder.endControlFlow();
                }
            } else if ((field.isJsonNullable() && field.isNotNull()) || isNotNullable) {
                checkBuilder.endControlFlow();
            }

            fieldConstraintBuilder.add(checkBuilder.build());
        }

        final MethodSpec.Builder constructorSpecBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        for (var factoryToField : constraintToFieldName.entrySet()) {
            var factory = factoryToField.getKey();
            final String fieldName = factoryToField.getValue();
            final String createParameters = factory.parameters().values().stream()
                .map(ValidatorGenerator::createParameter)
                .collect(Collectors.joining(", "));

            validatorSpecBuilder.addField(FieldSpec.builder(
                factory.validator().asPoetType(),
                fieldName,
                Modifier.PRIVATE, Modifier.FINAL).build());

            final ParameterSpec parameterSpec = ParameterSpec.builder(factory.type().asPoetType(), fieldName).build();
            parameterSpecs.add(parameterSpec);
            constructorSpecBuilder
                .addParameter(parameterSpec)
                .addStatement("this.$L = $L.create($L)", fieldName, fieldName, createParameters);
        }

        for (var validatedToField : validatedToFieldName.entrySet()) {
            final String fieldName = validatedToField.getValue();
            final TypeName fieldType = validatedToField.getKey().validator(processingEnv).asPoetType();
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
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), VIOLATION_TYPE))
            .addParameter(ParameterSpec.builder(meta.source().asPoetType(), "value").build())
            .addParameter(ParameterSpec.builder(CONTEXT_TYPE, "context").build());

        if (fieldConstraintBuilder.isEmpty()) {
            validateMethodSpecBuilder
                .addCode(CodeBlock.of("""
                           if (value == null) {
                               return $T.of(context.violates("$L input must be non null, but was null"));
                           }
                           return $T.of();""",
                    List.class, meta.sourceElement().getSimpleName(), List.class));
        } else {
            validateMethodSpecBuilder
                .addCode(CodeBlock.join(List.of(
                        CodeBlock.of("""
                                if (value == null) {
                                    return $T.of(context.violates("$L input must be non null, but was null"));
                                }
                                
                                final $T<$T> _violations = new $T<>();""",
                            List.class, meta.sourceElement().getSimpleName(), List.class, ValidTypes.violation, ArrayList.class),
                        CodeBlock.join(fieldConstraintBuilder, "\n"),
                        CodeBlock.of("return _violations;")),
                    "\n\n"));
        }

        final TypeSpec validatorSpec = validatorSpecBuilder
            .addMethod(constructorSpecBuilder.build())
            .addMethod(validateMethodSpecBuilder.build())
            .build();

        return new ValidAnnotationProcessor.ValidatorSpec(meta, validatorSpec, parameterSpecs);
    }

    private static String createParameter(Object parameter) {
        if (parameter instanceof List<?> list) {
            return list.stream()
                .map(ValidatorGenerator::createParameter)
                .collect(Collectors.joining(", ", "new String[] {", "}"));
        }

        return parameter.toString();
    }

    private ValidMeta getValidatorMetas(TypeElement element) {
        if (element.getKind().isInterface()) {
            return getInterfaceValidatorMetas(element);
        }
        final List<VariableElement> elementFields = getFields(element);
        final List<ValidMeta.Field> fields = new ArrayList<>();
        for (VariableElement fieldElement : elementFields) {
            final List<ValidMeta.Constraint> constraints = getValidatedByConstraints(processingEnv, fieldElement);
            final List<ValidMeta.Validated> validateds = getValidated(fieldElement);

            final boolean isNotNull = isNotNull(fieldElement);
            final boolean isJsonNullable;
            final TypeMirror targetType;
            if (fieldElement.asType() instanceof DeclaredType dt && jsonNullable.canonicalName().equals(dt.asElement().toString())) {
                targetType = dt.getTypeArguments().get(0);
                isJsonNullable = true;
            } else {
                targetType = fieldElement.asType();
                isJsonNullable = false;
            }

            if (!constraints.isEmpty() || !validateds.isEmpty() || (isJsonNullable && isNotNull)) {
                final boolean isNullable = CommonUtils.isNullable(element) || CommonUtils.isNullable(fieldElement);
                final boolean isPrimitive = fieldElement.asType() instanceof PrimitiveType;
                final boolean isRecord = element.getKind() == ElementKind.RECORD;

                final TypeMirror fieldType = ValidUtils.getBoxType(targetType, processingEnv);
                final ValidMeta.Field fieldMeta = new ValidMeta.Field(
                    ValidMeta.Type.ofElement(processingEnv.getTypeUtils().asElement(fieldType), fieldType),
                    fieldElement.getSimpleName().toString(),
                    isRecord,
                    isNullable,
                    isNotNull,
                    isJsonNullable,
                    isPrimitive,
                    constraints,
                    validateds);

                fields.add(fieldMeta);
            }
        }
        return new ValidMeta(element, fields);
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

    private ValidMeta getInterfaceValidatorMetas(TypeElement element) {
        final Map<String, ExecutableElement> accessors = new LinkedHashMap<>();
        collectInterfaceAccessors(element, accessors, new HashSet<>());
        final List<ValidMeta.Field> fields = new ArrayList<>();
        for (var method : accessors.values()) {
            final TypeMirror methodType = ((javax.lang.model.type.ExecutableType) types.asMemberOf((DeclaredType) element.asType(), method)).getReturnType();
            final List<ValidMeta.Constraint> constraints = ValidUtils.getValidatedByConstraints(processingEnv, methodType, method.getAnnotationMirrors());
            final List<ValidMeta.Validated> validateds = getValidated(method, methodType);
            final boolean isNotNull = isNotNull(method);
            final boolean isJsonNullable;
            final TypeMirror targetType;
            if (methodType instanceof DeclaredType dt && jsonNullable.canonicalName().equals(dt.asElement().toString())) {
                targetType = dt.getTypeArguments().get(0);
                isJsonNullable = true;
            } else {
                targetType = methodType;
                isJsonNullable = false;
            }

            if (!constraints.isEmpty() || !validateds.isEmpty() || (isJsonNullable && isNotNull)) {
                final boolean isNullable = CommonUtils.isNullable(element) || CommonUtils.isNullable(method);
                final boolean isPrimitive = methodType.getKind().isPrimitive();
                final TypeMirror fieldType = ValidUtils.getBoxType(targetType, processingEnv);
                fields.add(new ValidMeta.Field(
                    ValidMeta.Type.ofElement(processingEnv.getTypeUtils().asElement(fieldType), fieldType),
                    method.getSimpleName().toString(),
                    true,
                    isNullable,
                    isNotNull,
                    isJsonNullable,
                    isPrimitive,
                    constraints,
                    validateds));
            }
        }
        return new ValidMeta(element, fields);
    }

    private void collectInterfaceAccessors(TypeElement element, Map<String, ExecutableElement> accessors, Set<TypeElement> seen) {
        if (!seen.add(element)) {
            return;
        }
        for (var enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD || enclosed.getModifiers().contains(Modifier.STATIC) || enclosed.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            var method = (ExecutableElement) enclosed;
            if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID && method.getTypeParameters().isEmpty()) {
                accessors.putIfAbsent(method.getSimpleName().toString(), method);
            }
        }
        for (var superinterface : element.getInterfaces()) {
            collectInterfaceAccessors((TypeElement) types.asElement(superinterface), accessors, seen);
        }
    }

    private static List<ValidMeta.Validated> getValidated(VariableElement field) {
        if (field.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals(VALID_TYPE.canonicalName()))) {
            return List.of(new ValidMeta.Validated(ValidMeta.Type.ofElement(field, field.asType())));
        }

        return Collections.emptyList();
    }

    private static List<ValidMeta.Validated> getValidated(ExecutableElement method, TypeMirror methodType) {
        if (method.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals(VALID_TYPE.canonicalName()))) {
            return List.of(new ValidMeta.Validated(ValidMeta.Type.ofElement(method, methodType)));
        }

        return Collections.emptyList();
    }
}
