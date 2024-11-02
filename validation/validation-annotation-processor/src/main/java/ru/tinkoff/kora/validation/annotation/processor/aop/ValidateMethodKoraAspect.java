package ru.tinkoff.kora.validation.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;
import ru.tinkoff.kora.validation.annotation.processor.ValidMeta;
import ru.tinkoff.kora.validation.annotation.processor.ValidTypes;
import ru.tinkoff.kora.validation.annotation.processor.ValidUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.squareup.javapoet.CodeBlock.joining;
import static ru.tinkoff.kora.validation.annotation.processor.ValidMeta.Validated;
import static ru.tinkoff.kora.validation.annotation.processor.ValidTypes.*;
import static ru.tinkoff.kora.validation.annotation.processor.ValidUtils.isNotNull;

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
        final boolean isFuture = MethodUtils.isFuture(method);
        final TypeMirror returnType = (isMono || isFlux || isFuture)
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

            if (isMono || isFlux || isFuture) {
                if (MethodUtils.getGenericType(method.getReturnType()).filter(CommonUtils::isVoid).isPresent()) {
                    throw new ProcessingErrorException("@Validate for Return Value can't be applied for types assignable from " + Void.class, method);
                }
            }
        }

        final CodeBlock body;
        if (isMono) {
            body = buildBodyMono(method, superCall, CommonClassNames.mono, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        } else if (isFlux) {
            body = buildBodyFlux(method, superCall, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        } else if (isFuture) {
            body = buildBodyFuture(method, superCall, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        } else {
            body = buildBodySync(method, superCall, validationReturnCode.orElse(null), validationArgumentCode.orElse(null));
        }

        return new ApplyResult.MethodBody(body);
    }

    private Optional<CodeBlock> buildValidationReturnCode(ExecutableElement method, TypeMirror returnType, AspectContext aspectContext) {
        final boolean isMono = MethodUtils.isMono(method);
        final boolean isFlux = MethodUtils.isFlux(method);
        final boolean isFuture = MethodUtils.isFuture(method);

        final List<ValidMeta.Constraint> constraints = ValidUtils.getValidatedByConstraints(env, returnType, method.getAnnotationMirrors());
        final List<Validated> validates = (method.getAnnotationMirrors().stream().anyMatch(a -> a.getAnnotationType().toString().equals(VALID_TYPE.canonicalName())))
            ? List.of(new ValidMeta.Validated(ValidMeta.Type.ofType(returnType)))
            : Collections.emptyList();

        var isPrimitive = returnType instanceof PrimitiveType;
        final boolean isNullable;
        if (isFuture) {
            isNullable = MethodUtils.getGenericType(method.getReturnType())
                             .map(CommonUtils::isNullable)
                             .orElseGet(() -> CommonUtils.isNullable(method)) || CommonUtils.isVoid(returnType);
        } else {
            isNullable = (CommonUtils.isNullable(method) && !isPrimitive && !isMono && !isFlux) || CommonUtils.isVoid(returnType);
        }

        final boolean isNotNullable = !isNullable && !isPrimitive;
        final boolean isNotNull = isNotNull(method);
        final boolean isJsonNullable = returnType instanceof DeclaredType dt && jsonNullable.canonicalName().equals(dt.asElement().toString());
        var haveValidators = !constraints.isEmpty() || !validates.isEmpty();
        if (CommonUtils.isVoid(returnType) && !haveValidators && !isJsonNullable && isNotNullable) {
            return Optional.empty();
        }

        if (!(haveValidators || ((isJsonNullable || isNotNullable) && !CommonUtils.isVoid(returnType)))) {
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
        if ((isJsonNullable && isNotNull) || isNotNullable) {
            if (isJsonNullable && isNotNull) {
                builder.beginControlFlow("if (_result == null || !_result.isDefined() || _result.isNull())");
            } else {
                builder.beginControlFlow("if (_result == null)");
            }
            builder.add(resultCtxBlock);
            if (MethodUtils.isFuture(method)) {
                builder.addStatement("throw new $T(_returnCtx.violates(\"Result must be non null, but was null\"))", ValidTypes.violationException);
            } else if (MethodUtils.isMono(method)) {
                builder.addStatement("_sink.error(new $T(_returnCtx.violates(\"Result must be non null, but was null\")))", ValidTypes.violationException);
                builder.addStatement("return");
            } else if (MethodUtils.isFlux(method)) {
                builder.addStatement("_sink.error(new $T(_returnCtx.violates(\"Result must be non null, but was null\")))", ValidTypes.violationException);
                builder.addStatement("return");
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
            var constraintFactory = aspectContext.fieldFactory().constructorParam(constraint.factory().type().asMirror(env), List.of());
            var constraintType = constraint.factory().validator().asMirror(env);

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
                if (MethodUtils.isFuture(method)) {
                    builder.addStatement("throw new $T($N)", EXCEPTION_TYPE, constraintResultField);
                } else if (MethodUtils.isMono(method)) {
                    builder.addStatement("_sink.error(new $T($N))", EXCEPTION_TYPE, constraintResultField);
                    builder.addStatement("return");
                } else if (MethodUtils.isFlux(method)) {
                    builder.addStatement("_sink.error(new $T($N))", EXCEPTION_TYPE, constraintResultField);
                    builder.addStatement("return");
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
            var validatorType = validated.validator().asMirror(env);
            var validatorField = aspectContext.fieldFactory().constructorParam(validatorType, List.of());
            var validatedResultField = "_returnValidatorResult_" + i;
            builder.addStatement("var $N = $N.validate($L, _returnCtx)", validatedResultField, validatorField, resultAccessor);
            if (isFailFast) {
                builder.beginControlFlow("if (!$N.isEmpty())", validatedResultField);
                if (MethodUtils.isFuture(method)) {
                    builder.addStatement("throw new $T($N)", EXCEPTION_TYPE, validatedResultField);
                } else if (MethodUtils.isMono(method)) {
                    builder.addStatement("_sink.error(new $T($N))", EXCEPTION_TYPE, validatedResultField);
                    builder.addStatement("return");
                } else if (MethodUtils.isFlux(method)) {
                    builder.addStatement("_sink.error(new $T($N))", EXCEPTION_TYPE, validatedResultField);
                    builder.addStatement("return");
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
            if (MethodUtils.isFuture(method)) {
                builder.addStatement("throw new $T(_returnViolations)", EXCEPTION_TYPE);
            } else if (MethodUtils.isMono(method)) {
                builder.addStatement("_sink.error(new $T(_returnViolations))", EXCEPTION_TYPE);
                builder.addStatement("return");
            } else if (MethodUtils.isFlux(method)) {
                builder.addStatement("_sink.error(new $T(_returnViolations))", EXCEPTION_TYPE);
                builder.addStatement("return");
            } else {
                builder.addStatement("throw new $T(_returnViolations)", EXCEPTION_TYPE);
            }
            builder.endControlFlow();
        }

        if ((isJsonNullable && isNotNull) || isNotNullable) {
            builder.endControlFlow();
        }
        if (isNullable) {
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
                        if (MethodUtils.isFuture(method)) {
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
                } else if(isNullable) {
                    builder.beginControlFlow("if($N != null)", paramName);
                    builder.addStatement("var $N = _argCtx.addPath($S)", argumentContext, paramName);
                } else {
                    builder.addStatement("var $N = _argCtx.addPath($S)", argumentContext, paramName);
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
                    var constraintResultField = "_argConstResult_" + parameter + "_" + i;

                    builder.addStatement("var $N = $N.validate($N, $N)",
                        constraintResultField, constraintField, paramAccessor, argumentContext);
                    if (isFailFast) {
                        builder.beginControlFlow("if (!$N.isEmpty())", constraintResultField);
                        if (MethodUtils.isFuture(method)) {
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
                    var validatorType = validated.validator().asMirror(env);

                    var validatorField = aspectContext.fieldFactory().constructorParam(validatorType, List.of());
                    var validatorResultField = "_argValidatorResult_" + parameter + "_" + i;

                    builder.addStatement("var $N = $N.validate($N, $N)",
                        validatorResultField, validatorField, paramAccessor, argumentContext);
                    if (isFailFast) {
                        builder.beginControlFlow("if (!$N.isEmpty())", validatorResultField);
                        if (MethodUtils.isFuture(method)) {
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
            if (MethodUtils.isFuture(method)) {
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

    private CodeBlock buildBodyFuture(ExecutableElement method,
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

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    String superCall,
                                    ClassName reactorName,
                                    @Nullable CodeBlock validationReturnCode,
                                    @Nullable CodeBlock validationArgumentCode) {
        final CodeBlock superMethod = buildMethodCall(method, superCall);

        var builder = CodeBlock.builder();
        if (validationArgumentCode != null) {
            builder.beginControlFlow("return $T.defer(() -> ", reactorName);
            builder.add(validationArgumentCode);
        }

        if (validationReturnCode != null) {
            builder.add("return $L.handle((_result, _sink) -> {\n", superMethod.toString());
            builder.indent().indent().indent().indent()
                .add(validationReturnCode)
                .add("_sink.next(_result);")
                .unindent().unindent().unindent().unindent()
                .add("\n\n});\n");
        } else {
            builder.addStatement("return $L", superMethod.toString());
        }

        if (validationArgumentCode != null) {
            builder.endControlFlow(")");
        }

        return builder.build();
    }

    private CodeBlock buildBodyFlux(ExecutableElement method,
                                    String superCall,
                                    @Nullable CodeBlock validationReturnCode,
                                    @Nullable CodeBlock validationArgumentCode) {
        return buildBodyMono(method, superCall, CommonClassNames.flux, validationReturnCode, validationArgumentCode);
    }

    private CodeBlock buildMethodCall(ExecutableElement method, String call) {
        return method.getParameters().stream().map(p -> CodeBlock.of("$L", p)).collect(joining(", ", call + "(", ")"));
    }
}
