package ru.tinkoff.kora.validation.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.validation.annotation.processor.ValidMeta;
import ru.tinkoff.kora.validation.annotation.processor.ValidUtils;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.concurrent.Future;

import static com.squareup.javapoet.CodeBlock.joining;
import static ru.tinkoff.kora.validation.annotation.processor.ValidMeta.*;

public class ValidateMethodKoraAspect implements KoraAspect {

    private static final ClassName VALIDATE_TYPE = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Validate");

    private final ProcessingEnvironment env;

    public ValidateMethodKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(VALIDATE_TYPE.canonicalName());
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        final boolean isMono = MethodUtils.isMono(method);
        final boolean isFlux = MethodUtils.isFlux(method);
        final TypeMirror returnType = (isMono || isFlux)
            ? MethodUtils.getGenericType(method.getReturnType()).orElseThrow()
            : method.getReturnType();

        var validationReturnCode = buildValidationReturnCode(method, returnType, aspectContext);
        var validationArgumentCode = buildValidationArgumentCode(method, aspectContext);
        if (validationReturnCode.isEmpty() && validationArgumentCode.isEmpty()) {
            return ApplyResult.Noop.INSTANCE;
        }

        if (validationReturnCode.isPresent()) {
            if (MethodUtils.isFuture(method)) {
                throw new ProcessingErrorException("@Validate for Return Value can't be applied for types assignable from " + Future.class, method);
            }

            if (MethodUtils.isVoid(method)) {
                throw new ProcessingErrorException("@Validate for Return Value can't be applied for types assignable from " + Void.class, method);
            } else if (isMono || isFlux) {
                if (MethodUtils.getGenericType(method.getReturnType()).filter(CommonUtils::isVoid).isPresent()) {
                    throw new ProcessingErrorException("@Validate for Return Value can't be applied for types assignable from " + Void.class, method);
                }
            }
        }

        final CodeBlock body;
        if (isMono) {
            body = buildBodyMono(method, superCall, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        } else if (isFlux) {
            body = buildBodyFlux(method, superCall, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        } else {
            body = buildBodySync(method, superCall, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        }

        return new ApplyResult.MethodBody(body);
    }

    private Optional<CodeBlock> buildValidationReturnCode(ExecutableElement method, TypeMirror returnType, AspectContext aspectContext) {
        final boolean isMono = MethodUtils.isMono(method);
        final boolean isFlux = MethodUtils.isFlux(method);

        final List<ValidMeta.Constraint> constraints = ValidUtils.getValidatedByConstraints(env, returnType, method.getAnnotationMirrors());
        final List<Validated> validates = (method.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals(VALID_TYPE.canonicalName())))
            ? List.of(new ValidMeta.Validated(ValidMeta.Type.ofType(returnType)))
            : Collections.emptyList();

        if (constraints.isEmpty() && validates.isEmpty()) {
            return Optional.empty();
        }

        var isNullable = CommonUtils.isNullable(method);
        var isPrimitive = returnType instanceof PrimitiveType;

        var builder = CodeBlock.builder();
        if (isNullable && !isPrimitive && !isMono && !isFlux) {
            builder.beginControlFlow("if(_result != null) ");
        }

        final boolean isFailFast = method.getAnnotationMirrors().stream()
            .filter(a -> a.getAnnotationType().toString().equals(VALIDATE_TYPE.canonicalName()))
            .flatMap(a -> env.getElementUtils().getElementValuesWithDefaults(a).entrySet().stream()
                .filter(e -> "failFast".equals(e.getKey().getSimpleName().toString()))
                .map(e -> Boolean.parseBoolean(e.getValue().getValue().toString())))
            .findFirst()
            .orElse(false);

        builder.addStatement("var _returnValueContext = $T.builder().failFast($L).build()", CONTEXT_TYPE, isFailFast);
        if (!isFailFast) {
            builder.addStatement("var _returnValueViolations = new $T<$T>()", ArrayList.class, VIOLATION_TYPE);
        }

        for (int i = 1; i <= constraints.size(); i++) {
            var constraint = constraints.get(i - 1);
            var constraintFactory = aspectContext.fieldFactory().constructorParam(constraint.factory().type().asMirror(env), List.of());
            var constraintType = constraint.factory().validator().asMirror(env);

            final CodeBlock createExec = CodeBlock.builder()
                .add("$N.create", constraintFactory)
                .add(constraint.factory().parameters().values().stream()
                    .map(fp -> CodeBlock.of("$L", fp))
                    .collect(joining(", ", "(", ")")))
                .build();

            var constraintField = aspectContext.fieldFactory().constructorInitialized(constraintType, createExec);
            var constraintResultField = "_returnConstraintResult_" + i;
            builder.addStatement("var $N = $N.validate(_result, _returnValueContext)", constraintResultField, constraintField);
            if (isFailFast) {
                builder.beginControlFlow("if (!$N.isEmpty())", constraintResultField)
                    .addStatement("throw new $T($N)", EXCEPTION_TYPE, constraintResultField)
                    .endControlFlow();
            } else {
                builder.beginControlFlow("if (!$N.isEmpty())", constraintResultField)
                    .addStatement("_returnValueViolations.addAll($N)", constraintResultField)
                    .endControlFlow();
            }
        }

        for (int i = 1; i <= validates.size(); i++) {
            var validated = validates.get(i - 1);
            var validatorType = validated.validator().asMirror(env);
            var validatorField = aspectContext.fieldFactory().constructorParam(validatorType, List.of());
            var validatedResultField = "_returnValidatorResult_" + i;
            builder.addStatement("var $N = $N.validate(_result, _returnValueContext)", validatedResultField, validatorField);
            if (isFailFast) {
                builder.beginControlFlow("if (!$N.isEmpty())", validatedResultField)
                    .addStatement("throw new $T($N)", EXCEPTION_TYPE, validatedResultField)
                    .endControlFlow();
            } else {
                builder.beginControlFlow("if (!$N.isEmpty())", validatedResultField)
                    .addStatement("_returnValueViolations.addAll($N)", validatedResultField)
                    .endControlFlow();
            }
        }

        if (!isFailFast) {
            builder.beginControlFlow("if (!_returnValueViolations.isEmpty())")
                .addStatement("throw new $T(_returnValueViolations)", EXCEPTION_TYPE)
                .endControlFlow();
        }

        if (isNullable && !isPrimitive && !isMono && !isFlux) {
            builder.endControlFlow();
        }

        return Optional.of(builder.build());
    }

    private Optional<CodeBlock> buildValidationArgumentCode(ExecutableElement method, AspectContext aspectContext) {
        final boolean isAnyParameterValidated = method.getParameters().stream().anyMatch(this::isParameterValidatable);
        if (!isAnyParameterValidated) {
            return Optional.empty();
        }

        final boolean isFailFast = method.getAnnotationMirrors().stream()
            .filter(a -> a.getAnnotationType().toString().equals(VALIDATE_TYPE.canonicalName()))
            .flatMap(a -> env.getElementUtils().getElementValuesWithDefaults(a).entrySet().stream()
                .filter(e -> "failFast".equals(e.getKey().getSimpleName().toString()))
                .map(e -> Boolean.parseBoolean(e.getValue().getValue().toString())))
            .findFirst()
            .orElse(false);

        var builder = CodeBlock.builder()
            .addStatement("var _argumentsContext = $T.builder().failFast($L).build()", CONTEXT_TYPE, isFailFast);

        if (!isFailFast) {
            builder.addStatement("var _argumentsViolations = new $T<$T>()", ArrayList.class, VIOLATION_TYPE);
        }

        for (var parameter : method.getParameters()) {
            var isNullable = CommonUtils.isNullable(parameter);
            var isPrimitive = parameter.asType() instanceof PrimitiveType;
            if (isParameterValidatable(parameter)) {
                var constraints = ValidUtils.getValidatedByConstraints(env, parameter.asType(), parameter.getAnnotationMirrors());
                var validates = getValidForArguments(parameter);

                if (isNullable && !isPrimitive) {
                    builder.beginControlFlow("if($N != null)", parameter.getSimpleName());
                }

                var argumentsContext = "_argumentContext_" + parameter;
                builder.addStatement("var $N = _argumentsContext.addPath($S)", argumentsContext, parameter.getSimpleName());
                for (int i = 1; i <= constraints.size(); i++) {
                    var constraint = constraints.get(i - 1);
                    var constraintFactory = aspectContext.fieldFactory().constructorParam(constraint.factory().type().asMirror(env), List.of());
                    var constraintType = constraint.factory().validator().asMirror(env);

                    final CodeBlock createExec = CodeBlock.builder()
                        .add("$N.create", constraintFactory)
                        .add(constraint.factory().parameters().values().stream()
                            .map(fp -> CodeBlock.of("$L", fp))
                            .collect(joining(", ", "(", ")")))
                        .build();

                    var constraintField = aspectContext.fieldFactory().constructorInitialized(constraintType, createExec);
                    var constraintResultField = "_argumentConstraintResult_" + parameter + "_" + i;

                    builder.addStatement("var $N = $N.validate($N, $N)",
                        constraintResultField, constraintField, parameter.getSimpleName(), argumentsContext);
                    if (isFailFast) {
                        builder
                            .beginControlFlow("if (!$N.isEmpty())", constraintResultField)
                            .addStatement("throw new $T($N)", EXCEPTION_TYPE, constraintResultField)
                            .endControlFlow();
                    } else {
                        builder
                            .beginControlFlow("if (!$N.isEmpty())", constraintResultField)
                            .addStatement("_argumentsViolations.addAll($N)", constraintResultField)
                            .endControlFlow();
                    }
                }

                for (int i = 1; i <= validates.size(); i++) {
                    var validated = validates.get(i - 1);
                    var validatorType = validated.validator().asMirror(env);

                    var validatorField = aspectContext.fieldFactory().constructorParam(validatorType, List.of());
                    var validatorResultField = "_argumentValidatorResult_" + parameter + "_" + i;

                    builder.addStatement("var $N = $N.validate($N, $N)",
                        validatorResultField, validatorField, parameter.getSimpleName(), argumentsContext);
                    if (isFailFast) {
                        builder
                            .beginControlFlow("if (!$N.isEmpty())", validatorResultField)
                            .addStatement("throw new $T($N)", EXCEPTION_TYPE, validatorResultField)
                            .endControlFlow();
                    } else {
                        builder
                            .beginControlFlow("if (!$N.isEmpty())", validatorResultField)
                            .addStatement("_argumentsViolations.addAll($N)", validatorResultField)
                            .endControlFlow();
                    }
                }

                if (isNullable && !isPrimitive) {
                    builder.endControlFlow();
                }
            }
        }

        if (!isFailFast) {
            builder.beginControlFlow("if (!_argumentsViolations.isEmpty())")
                .addStatement("throw new $T(_argumentsViolations)", EXCEPTION_TYPE)
                .endControlFlow();
        }

        return Optional.of(builder.build());
    }

    private boolean isParameterValidatable(VariableElement parameter) {
        for (var annotation : parameter.getAnnotationMirrors()) {
            var annotationType = annotation.getAnnotationType();
            if (annotationType.toString().equals(VALID_TYPE.canonicalName())) {
                return true;
            }

            for (var innerAnnotation : annotationType.asElement().getAnnotationMirrors()) {
                if (innerAnnotation.getAnnotationType().toString().equals(VALIDATED_BY_TYPE.canonicalName())) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<ValidMeta.Validated> getValidForArguments(VariableElement parameter) {
        if (parameter.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals(VALID_TYPE.canonicalName()))) {
            return List.of(new ValidMeta.Validated(ValidMeta.Type.ofType(parameter.asType())));
        }

        return Collections.emptyList();
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    String superCall,
                                    @Nullable CodeBlock validationReturnCode,
                                    @Nullable CodeBlock validationArgumentCode) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        var builder = CodeBlock.builder();
        if (MethodUtils.isVoid(method)) {
            if (validationArgumentCode != null) {
                builder.add(validationArgumentCode);
            }

            return builder.add("$L;\n", superMethod.toString()).build();
        } else {
            if (validationArgumentCode != null) {
                builder.add(validationArgumentCode).add("\n");
            }

            builder.add("var _result = $L;\n", superMethod.toString());

            if (validationReturnCode != null) {
                builder.add(validationReturnCode);
            }

            return builder
                .add("return _result;")
                .build();
        }
    }

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    String superCall,
                                    @Nullable CodeBlock validationReturnCode,
                                    @Nullable CodeBlock validationArgumentCode) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);
        var builder = CodeBlock.builder();
        if (validationReturnCode != null) {
            builder.add("""
                    return $L
                        .map(_result -> {
                        """, superMethod.toString())
                .indent().indent().indent().indent()
                .add(validationReturnCode)
                .add("return _result;")
                .unindent().unindent().unindent().unindent()
                .add("""
                                    
                    })""");
        } else {
            builder.add("return $L", superMethod.toString());
        }

        if (validationArgumentCode != null) {
            builder.add("""
                    .doFirst(() -> {
                    """)
                .indent().indent()
                .add(validationArgumentCode)
                .unindent().unindent()
                .add("})");
        }

        return builder.add(";").build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method,
                                    String superCall,
                                    @Nullable CodeBlock validationReturnCode,
                                    @Nullable CodeBlock validationArgumentCode) {
        return buildBodyMono(method, superCall, validationReturnCode, validationArgumentCode);
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
