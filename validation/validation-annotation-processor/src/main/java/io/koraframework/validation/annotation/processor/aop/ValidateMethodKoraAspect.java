package io.koraframework.validation.annotation.processor.aop;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.aop.annotation.processor.KoraAspect;
import io.koraframework.validation.annotation.processor.ValidMeta;
import io.koraframework.validation.annotation.processor.ValidTypes;
import io.koraframework.validation.annotation.processor.ValidUtils;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.palantir.javapoet.CodeBlock.joining;
import static io.koraframework.validation.annotation.processor.ValidMeta.Validated;
import static io.koraframework.validation.annotation.processor.ValidTypes.*;
import static io.koraframework.validation.annotation.processor.ValidUtils.isNotNull;

public class ValidateMethodKoraAspect implements KoraAspect {

    private static final ClassName VALIDATE_TYPE = ClassName.get("io.koraframework.validation.common.annotation", "Validate");

    private final ProcessingEnvironment env;

    public ValidateMethodKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(VALIDATE_TYPE);
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@Validate can't be applied for type " + CommonClassNames.publisher, method);
        } else if (MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@Validate can't be applied for type " + method.getReturnType().toString(), method);
        }

        final boolean isCompletableStage = MethodUtils.isCompletionStage(method);
        final TypeMirror returnType = isCompletableStage
            ? MethodUtils.getGenericType(method.getReturnType()).orElseThrow()
            : method.getReturnType();

        var validationReturnCode = buildValidationReturnCode(method, returnType, aspectContext);
        var validationArgumentCode = buildValidationArgumentCode(method, aspectContext);
        if (validationReturnCode.isEmpty() && validationArgumentCode.isEmpty()) {
            return ApplyResult.Noop.INSTANCE;
        }

        if (validationReturnCode.isPresent()) {
            if (MethodUtils.isVoid(method)) {
                throw new ProcessingErrorException("@Validate for Return Value can't be applied for types assignable from " + Void.class, method);
            }

            if (isCompletableStage) {
                if (MethodUtils.getGenericType(method.getReturnType()).filter(CommonUtils::isVoid).isPresent()) {
                    throw new ProcessingErrorException("@Validate for Return Value can't be applied for types assignable from " + Void.class, method);
                }
            }
        }

        final CodeBlock body;
        if (isCompletableStage) {
            body = buildBodyCompletionStage(method, superCall, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        } else {
            body = buildBodySync(method, superCall, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        }

        return new ApplyResult.MethodBody(body);
    }

    private Optional<CodeBlock> buildValidationReturnCode(ExecutableElement method, TypeMirror returnType, AspectContext aspectContext) {
        if (CommonUtils.isVoid(returnType)) {
            return Optional.empty();
        }

        final boolean isCompletableStage = MethodUtils.isCompletionStage(method);
        final boolean isValid = method.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals(VALID_TYPE.canonicalName()));

        final List<ValidMeta.Constraint> constraints = ValidUtils.getValidatedByConstraints(env, returnType, method.getAnnotationMirrors());
        final List<Validated> validates = (isValid)
            ? List.of(new ValidMeta.Validated(ValidMeta.Type.ofElement(env.getTypeUtils().asElement(returnType), returnType)))
            : Collections.emptyList();

        var isPrimitive = returnType instanceof PrimitiveType;
        final boolean isNullable;
        if (isCompletableStage) {
            isNullable = MethodUtils.getGenericType(method.getReturnType())
                             .map(CommonUtils::isNullable)
                             .orElse(false)
                         || CommonUtils.isNullable(method);
        } else {
            isNullable = CommonUtils.isNullable(method) && !isPrimitive;
        }

        final boolean isNotNull;
        if (isCompletableStage) {
            isNotNull = MethodUtils.getGenericType(method.getReturnType())
                            .map(ValidUtils::isNotNull)
                            .orElse(false) || isNotNull(method);
        } else {
            isNotNull = isNotNull(method);
        }

        final boolean isNotNullable = (isValid || isNotNull) && !isNullable && !isPrimitive;
        final boolean isJsonNullable = returnType instanceof DeclaredType dt && jsonNullable.canonicalName().equals(dt.asElement().toString());
        var haveValidators = !constraints.isEmpty() || !validates.isEmpty();
        if (!haveValidators && !isNotNullable) {
            return Optional.empty();
        }

        var builder = CodeBlock.builder();
        final boolean isFailFast = method.getAnnotationMirrors().stream()
            .filter(a -> a.getAnnotationType().toString().equals(VALIDATE_TYPE.canonicalName()))
            .flatMap(a -> env.getElementUtils().getElementValuesWithDefaults(a).entrySet().stream()
                .filter(e -> "failFast".equals(e.getKey().getSimpleName().toString()))
                .map(e -> Boolean.parseBoolean(e.getValue().getValue().toString())))
            .findFirst()
            .orElse(false);

        final CodeBlock resultCtxBlock = (isFailFast)
            ? CodeBlock.of("var _returnCtx = $T.failFast();\n", CONTEXT_TYPE)
            : CodeBlock.of("var _returnCtx = $T.full();\n", CONTEXT_TYPE);

        final String resultAccessor = (isJsonNullable) ? "_result.value()" : "_result";
        if ((isJsonNullable && isNotNull && !isNullable) || isNotNullable) {
            if (isJsonNullable && isNotNull) {
                builder.beginControlFlow("if (_result == null || !_result.isDefined() || _result.isNull())");
            } else {
                builder.beginControlFlow("if (_result == null)");
            }
            builder.add(resultCtxBlock);
            if (MethodUtils.isCompletionStage(method)) {
                builder.addStatement("throw new $T(_returnCtx.violates(\"Result must be non null, but was null\"))", ValidTypes.violationException);
            } else {
                builder.addStatement("throw new $T(_returnCtx.violates(\"Result must be non null, but was null\"))", ValidTypes.violationException);
            }

            if (haveValidators) {
                if (isJsonNullable) {
                    builder.nextControlFlow("else if(_result.isDefined())");
                } else {
                    builder.nextControlFlow("else");
                }
                builder.add(resultCtxBlock);
                if (!isFailFast) {
                    builder.addStatement("var _returnViolations = new $T<$T>()", ArrayList.class, VIOLATION_TYPE);
                }
            }
        } else if (isJsonNullable) {
            builder.beginControlFlow("if(_result != null && _result.isDefined())");
            builder.add(resultCtxBlock);
            if (!isFailFast) {
                builder.addStatement("var _returnViolations = new $T<$T>()", ArrayList.class, VIOLATION_TYPE);
            }
        } else {
            builder.beginControlFlow("if(_result != null)");
            builder.add(resultCtxBlock);
            if (!isFailFast) {
                builder.addStatement("var _returnViolations = new $T<$T>()", ArrayList.class, VIOLATION_TYPE);
            }
        }

        for (int i = 1; i <= constraints.size(); i++) {
            var constraint = constraints.get(i - 1);
            var constraintFactory = aspectContext.fieldFactory().constructorParam(constraint.factory().type().typeMirror(), List.of());
            var constraintType = constraint.factory().validator().typeMirror();

            final CodeBlock createExec = CodeBlock.builder()
                .add("$N.create", constraintFactory)
                .add(constraint.factory().parameters().values().stream()
                    .map(fp -> CodeBlock.of("$L", fp))
                    .collect(joining(", ", "(", ")")))
                .build();

            var constraintField = aspectContext.fieldFactory().constructorInitialized(constraintType, createExec);
            var constraintResultField = "_returnConstResult_" + i;
            builder.addStatement("var $N = $N.validate($L, _returnCtx)", constraintResultField, constraintField, resultAccessor);
            if (isFailFast) {
                builder.beginControlFlow("if (!$N.isEmpty())", constraintResultField);
                if (MethodUtils.isCompletionStage(method)) {
                    builder.addStatement("throw new $T($N)", EXCEPTION_TYPE, constraintResultField);
                } else {
                    builder.addStatement("throw new $T($N)", EXCEPTION_TYPE, constraintResultField);
                }
                builder.endControlFlow();
            } else {
                builder.beginControlFlow("if (!$N.isEmpty())", constraintResultField)
                    .addStatement("_returnViolations.addAll($N)", constraintResultField)
                    .endControlFlow();
            }
            if (i + 1 < constraints.size()) {
                builder.add("\n");
            }
        }

        for (int i = 1; i <= validates.size(); i++) {
            var validated = validates.get(i - 1);
            var validatorType = validated.validator(env).typeMirror();
            var validatorField = aspectContext.fieldFactory().constructorParam(validatorType, List.of());
            var validatedResultField = "_returnValidatorResult_" + i;
            builder.addStatement("var $N = $N.validate($L, _returnCtx)", validatedResultField, validatorField, resultAccessor);
            if (isFailFast) {
                builder.beginControlFlow("if (!$N.isEmpty())", validatedResultField);
                if (MethodUtils.isCompletionStage(method)) {
                    builder.addStatement("throw new $T($N)", EXCEPTION_TYPE, validatedResultField);
                } else {
                    builder.addStatement("throw new $T($N)", EXCEPTION_TYPE, validatedResultField);
                }
                builder.endControlFlow();
            } else {
                builder.beginControlFlow("if (!$N.isEmpty())", validatedResultField)
                    .addStatement("_returnViolations.addAll($N)", validatedResultField)
                    .endControlFlow();
            }
            if (i + 1 < validates.size()) {
                builder.add("\n");
            }
        }

        if (haveValidators && !isFailFast) {
            builder.add("\n");
            builder.beginControlFlow("if (!_returnViolations.isEmpty())");
            if (MethodUtils.isCompletionStage(method)) {
                builder.addStatement("throw new $T(_returnViolations)", EXCEPTION_TYPE);
            } else {
                builder.addStatement("throw new $T(_returnViolations)", EXCEPTION_TYPE);
            }
            builder.endControlFlow();
        }

        if ((isJsonNullable && isNotNull && !isNullable) || isNotNullable) {
            builder.endControlFlow();
        }

        if (!isNotNullable) {
            builder.endControlFlow();
        }

        return Optional.of(builder.build());
    }

    private Optional<CodeBlock> buildValidationArgumentCode(ExecutableElement method, AspectContext aspectContext) {
        final boolean haveNoValidatable = method.getParameters().stream().noneMatch(this::isParameterValidatable);
        final boolean allParamsNullable = method.getParameters().stream().allMatch(CommonUtils::isNullable);
        if (allParamsNullable && haveNoValidatable) {
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
            .addStatement("var _argCtx = $T.builder().failFast($L).build()", CONTEXT_TYPE, isFailFast);

        if (!isFailFast) {
            builder.addStatement("var _argViolations = new $T<$T>()", ArrayList.class, VIOLATION_TYPE);
            builder.add("\n");
        }

        for (var parameter : method.getParameters()) {
            var isPrimitive = parameter.asType() instanceof PrimitiveType;
            var isNullable = CommonUtils.isNullable(parameter) && !isPrimitive;
            final boolean isNotNullable = !CommonUtils.isNullable(parameter) && !isPrimitive;

            final boolean isNotNull = isNotNull(parameter);
            final boolean isJsonNullable = parameter.asType() instanceof DeclaredType dt && jsonNullable.canonicalName().equals(dt.asElement().toString());

            var constraints = ValidUtils.getValidatedByConstraints(env, parameter.asType(), parameter.getAnnotationMirrors());
            var validates = getValidForArguments(parameter);
            var haveValidators = !constraints.isEmpty() || !validates.isEmpty();
            if (haveValidators || isJsonNullable || isNotNullable) {
                final String paramName = parameter.getSimpleName().toString();
                final String paramAccessor = (isJsonNullable) ? paramName + ".value()" : paramName;

                if (isJsonNullable && isNotNull) {
                    builder.beginControlFlow("if ($L == null || !$L.isDefined() || $L.isNull())",
                        paramName, paramName, paramName);
                } else if (isNotNullable) {
                    builder.beginControlFlow("if ($L == null)", paramName);
                }

                var argumentContext = "_argCtx_" + parameter;

                if ((isJsonNullable && isNotNull) || isNotNullable) {
                    builder.addStatement("var $N = _argCtx.addPath($S)", argumentContext, paramName);
                    if (isFailFast) {
                        if (MethodUtils.isCompletionStage(method)) {
                            builder.addStatement("return $T.failedFuture(new $T($L.violates(\"Parameter '$L' must be non null, but was null\")))", CompletableFuture.class, ValidTypes.violationException, argumentContext, paramName);
                        } else {
                            builder.addStatement("throw new $T($L.violates(\"Parameter '$L' must be non null, but was null\"))", ValidTypes.violationException, argumentContext, paramName);
                        }
                    } else {
                        builder.addStatement("_argViolations.add($L.violates(\"Parameter '$L' must be non null, but was null\"))", argumentContext, paramName);
                    }

                    if (haveValidators) {
                        if (isJsonNullable) {
                            builder.nextControlFlow("else if($N.isDefined())", paramName);
                            builder.addStatement("var $N = _argCtx.addPath($S)", argumentContext, paramName);
                        } else {
                            builder.nextControlFlow("else");
                            builder.addStatement("var $N = _argCtx.addPath($S)", argumentContext, paramName);
                        }
                    }
                } else if (isJsonNullable) {
                    builder.beginControlFlow("if($N != null && $N.isDefined())", paramName);
                    builder.addStatement("var $N = _argCtx.addPath($S)", argumentContext, paramName);
                } else if (isNullable) {
                    builder.beginControlFlow("if($N != null)", paramName);
                    builder.addStatement("var $N = _argCtx.addPath($S)", argumentContext, paramName);
                } else {
                    builder.addStatement("var $N = _argCtx.addPath($S)", argumentContext, paramName);
                }

                for (int i = 1; i <= constraints.size(); i++) {
                    var constraint = constraints.get(i - 1);
                    var constraintFactory = aspectContext.fieldFactory().constructorParam(constraint.factory().type().typeMirror(), List.of());
                    var constraintType = constraint.factory().validator().typeMirror();

                    final CodeBlock createExec = CodeBlock.builder()
                        .add("$N.create", constraintFactory)
                        .add(constraint.factory().parameters().values().stream()
                            .map(fp -> CodeBlock.of("$L", fp))
                            .collect(joining(", ", "(", ")")))
                        .build();

                    var constraintField = aspectContext.fieldFactory().constructorInitialized(constraintType, createExec);
                    var constraintResultField = "_argConstResult_" + parameter + "_" + i;

                    builder.addStatement("var $N = $N.validate($N, $N)",
                        constraintResultField, constraintField, paramAccessor, argumentContext);
                    if (isFailFast) {
                        builder.beginControlFlow("if (!$N.isEmpty())", constraintResultField);
                        if (MethodUtils.isCompletionStage(method)) {
                            builder.addStatement("return $T.failedFuture(new $T($N))", CompletableFuture.class, EXCEPTION_TYPE, constraintResultField);
                        } else {
                            builder.addStatement("throw new $T($N)", EXCEPTION_TYPE, constraintResultField);
                        }
                        builder.endControlFlow();
                    } else {
                        builder
                            .beginControlFlow("if (!$N.isEmpty())", constraintResultField)
                            .addStatement("_argViolations.addAll($N)", constraintResultField)
                            .endControlFlow();
                    }

                    if (i + 1 < constraints.size()) {
                        builder.add("\n");
                    }
                }

                for (int i = 1; i <= validates.size(); i++) {
                    var validated = validates.get(i - 1);
                    var validatorType = validated.validator(env).typeMirror();

                    var validatorField = aspectContext.fieldFactory().constructorParam(validatorType, List.of());
                    var validatorResultField = "_argValidatorResult_" + parameter + "_" + i;

                    builder.addStatement("var $N = $N.validate($N, $N)",
                        validatorResultField, validatorField, paramAccessor, argumentContext);
                    if (isFailFast) {
                        builder.beginControlFlow("if (!$N.isEmpty())", validatorResultField);
                        if (MethodUtils.isCompletionStage(method)) {
                            builder.addStatement("return $T.failedFuture(new $T($N))", CompletableFuture.class, EXCEPTION_TYPE, validatorResultField);
                        } else {
                            builder.addStatement("throw new $T($N)", EXCEPTION_TYPE, validatorResultField);
                        }
                        builder.endControlFlow();
                    } else {
                        builder
                            .beginControlFlow("if (!$N.isEmpty())", validatorResultField)
                            .addStatement("_argViolations.addAll($N)", validatorResultField)
                            .endControlFlow();
                    }

                    if (i + 1 < validates.size()) {
                        builder.add("\n");
                    }
                }

                if ((isJsonNullable && isNotNull) || isNotNullable) {
                    builder.endControlFlow();
                }
                if (isNullable) {
                    builder.endControlFlow();
                }
            }
        }

        if (!isFailFast) {
            builder.add("\n");
            builder.beginControlFlow("if (!_argViolations.isEmpty())");
            if (MethodUtils.isCompletionStage(method)) {
                builder.addStatement("return $T.failedFuture(new $T(_argViolations))", CompletableFuture.class, EXCEPTION_TYPE);
            } else {
                builder.addStatement("throw new $T(_argViolations)", EXCEPTION_TYPE);
            }
            builder.endControlFlow();
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
            return List.of(new ValidMeta.Validated(ValidMeta.Type.ofElement(parameter, parameter.asType())));
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

    private CodeBlock buildBodyCompletionStage(ExecutableElement method,
                                               String superCall,
                                               @Nullable CodeBlock validationReturnCode,
                                               @Nullable CodeBlock validationArgumentCode) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        var builder = CodeBlock.builder();
        if (validationArgumentCode != null) {
            builder.add(validationArgumentCode).add("\n");
        }

        if (validationReturnCode != null) {
            builder.beginControlFlow("return $L.thenApply(_result -> ", superMethod.toString());
            builder.add(validationReturnCode);
            builder.addStatement("return _result");
            builder.endControlFlow(")");
        } else {
            builder.addStatement("return $L", superMethod.toString());
        }

        return builder.build();
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
