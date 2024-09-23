package ru.tinkoff.kora.http.server.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.tinkoff.kora.http.server.annotation.processor.HttpServerClassNames.*;
import static ru.tinkoff.kora.http.server.annotation.processor.RequestHandlerGenerator.ParameterType.*;

public class RequestHandlerGenerator {

    private final Elements elements;
    private final Types types;
    private final ProcessingEnvironment processingEnvironment;
    @Nullable
    private final DeclaredType publisherTypeErasure;
    private final DeclaredType jdkPublisherTypeErasure;
    private final DeclaredType completionStageTypeErasure;

    public RequestHandlerGenerator(Elements elements, Types types, ProcessingEnvironment processingEnvironment) {
        this.elements = elements;
        this.types = types;
        this.processingEnvironment = processingEnvironment;
        this.publisherTypeErasure = dtFromString(elements, types, "org.reactivestreams.Publisher");
        this.jdkPublisherTypeErasure = Objects.requireNonNull(dtFromString(elements, types, "java.util.concurrent.Flow.Publisher"));
        this.completionStageTypeErasure = Objects.requireNonNull(dtFromString(elements, types, "java.util.concurrent.CompletionStage"));
    }

    @Nullable
    private static DeclaredType dtFromString(Elements elements, Types types, String name) {
        var te = elements.getTypeElement(name);
        if (te == null) {
            return null;
        }
        return types.getDeclaredType(te, types.getWildcardType(null, null));
    }


    @Nullable
    public MethodSpec generate(TypeElement controller, RequestMappingData requestMappingData) {
        var methodName = this.methodName(requestMappingData);

        var methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(httpServerRequestHandler)
            .addParameter(TypeName.get(controller.asType()), "_controller");

        var parameters = parseParameters(requestMappingData);
        if (parameters == null) {
            return null;
        }

        this.addParameterMappers(methodBuilder, requestMappingData, parameters);
        var responseMapper = this.detectResponseMapper(requestMappingData, requestMappingData.executableElement());
        if (responseMapper != null) {
            methodBuilder.addParameter(responseMapper);
        }

        var isBlocking = isBlocking(requestMappingData);
        if (isBlocking) {
            methodBuilder.addParameter(blockingRequestExecutor, "_executor");
        }

        var handlerCode = this.buildRequestHandler(controller, requestMappingData, parameters, methodBuilder);

        methodBuilder.addCode("return $T.of($S, $S, (_ctx, _request) -> {$>\n$L\n$<});",
            HttpServerClassNames.httpServerRequestHandlerImpl,
            requestMappingData.httpMethod().toUpperCase(),
            requestMappingData.route(),
            handlerCode
        );

        return methodBuilder.build();
    }

    private CodeBlock buildRequestHandler(TypeElement controller, RequestMappingData requestMappingData, List<Parameter> parameters, MethodSpec.Builder methodBuilder) {
        var handler = CodeBlock.builder();
        var returnType = requestMappingData.executableType().getReturnType();

        var hasNonBodyParams = false;

        var interceptors = Stream.concat(
                AnnotationUtils.findAnnotations(controller, interceptWithClassName, interceptWithContainerClassName).stream().map(HttpServerUtils::parseInterceptor),
                AnnotationUtils.findAnnotations(requestMappingData.executableElement(), interceptWithClassName, interceptWithContainerClassName).stream().map(HttpServerUtils::parseInterceptor)
            )
            .distinct()
            .toList();

        var requestMappingBlock = CodeBlock.builder();
        var requestName = "_request";
        for (int i = 0; i < interceptors.size(); i++) {
            var interceptor = interceptors.get(i);
            var interceptorName = "_interceptor" + (i + 1);
            var newRequestName = "_request" + (i + 1);
            var ctxName = "_ctx_" + (i + 1);
            requestMappingBlock.beginControlFlow("try").add("return ");
            requestMappingBlock.add("$L.intercept(_ctx, $L, ($N, $N) -> $>{\n", interceptorName, requestName, ctxName, newRequestName);
            requestName = newRequestName;
            var builder = ParameterSpec.builder(interceptor.type(), interceptorName);
            if (interceptor.tag() != null) {
                builder.addAnnotation(interceptor.tag());
            }
            methodBuilder.addParameter(builder.build());
        }
        handler.add(requestMappingBlock.build());

        for (var parameter : parameters) {
            switch (parameter.parameterType) {
                case PATH, QUERY, HEADER, COOKIE -> {
                    handler.addStatement("final $T $N", parameter.type, parameter.variableElement.getSimpleName());
                    hasNonBodyParams = true;
                }
                case REQUEST -> handler.add("var $N = _request;\n", parameter.name());
                case CONTEXT -> handler.add("var $N = _ctx;\n", parameter.name());
                default -> {}
            }
        }

        if (hasNonBodyParams) {
            handler.beginControlFlow("try");
        }

        for (var parameter : parameters) {
            var codeBlock = switch (parameter.parameterType) {
                case PATH -> this.definePathParameter(parameter, methodBuilder);
                case QUERY -> this.defineQueryParameter(parameter, methodBuilder);
                case HEADER -> this.defineHeaderParameter(parameter, methodBuilder);
                case COOKIE -> this.defineCookieParameter(parameter, methodBuilder);
                case MAPPED_HTTP_REQUEST, REQUEST, CONTEXT -> CodeBlock.of("");
            };
            handler.add(codeBlock);
            handler.add("\n");
        }

        if (hasNonBodyParams) {
            handler.nextControlFlow("catch (Exception _e)");
            handler.beginControlFlow("if (_e instanceof $T)", httpServerResponse);
            handler.addStatement("return $T.failedFuture(_e)", CompletableFuture.class);
            handler.nextControlFlow("else");
            handler.addStatement("return $T.failedFuture($T.of(400, _e))", CompletableFuture.class, httpServerResponseException);
            handler.endControlFlow();
            handler.endControlFlow();
            handler.add("\n");
        }

        final CodeBlock controllerCall;
        if (CommonUtils.isMono(returnType)) {
            controllerCall = this.generateMonoCall(requestMappingData, parameters, requestName);
        } else if (CommonUtils.isFuture(returnType)) {
            controllerCall = this.generateFutureCall(requestMappingData, parameters, requestName);
        } else {
            controllerCall = this.generateBlockingCall(requestMappingData, parameters, requestName);
        }

        handler.add(controllerCall);

        for (int i = 0; i < interceptors.size(); i++) {
            handler.addStatement("$<})");
            handler.nextControlFlow("catch (Exception _e)")
                .addStatement("return $T.failedFuture(_e)", CompletableFuture.class)
                .endControlFlow();
        }
        return handler.build();
    }

    private CodeBlock generateMonoCall(RequestMappingData requestMappingData, List<Parameter> parameters, String requestName) {
        var executeParameters = parameters.stream()
            .map(_p -> _p.variableElement.getSimpleName())
            .collect(Collectors.joining(", "));
        var mappedParameters = parameters.stream().filter(p -> p.parameterType == MAPPED_HTTP_REQUEST).toList();
        var b = CodeBlock.builder();
        for (var mappedParameter : mappedParameters) {
            b.addStatement("final $T $N", ParameterizedTypeName.get(ClassName.get(CompletableFuture.class), TypeName.get(mappedParameter.type)), "_future_" + mappedParameter.name);
            b.beginControlFlow("try");
            b.addStatement("$N = $LHttpRequestMapper.apply($L).toCompletableFuture()", "_future_" + mappedParameter.name, mappedParameter.name, requestName);
            b.nextControlFlow("catch ($T _e)", CompletionException.class);
            b.addStatement("if (_e.getCause() instanceof $T && _e.getCause() instanceof $T) throw ($T) _e.getCause()", httpServerResponse, RuntimeException.class, RuntimeException.class);
            b.addStatement("throw $T.of(400, _e.getCause())", httpServerResponseException);
            b.nextControlFlow("catch (Exception _e)");
            b.addStatement("if (_e instanceof $T) return $T.failedFuture(_e)", httpServerResponse, CompletableFuture.class);
            b.addStatement("return $T.failedFuture($T.of(400, _e))", CompletableFuture.class, httpServerResponseException);
            b.endControlFlow();
        }
        if (!mappedParameters.isEmpty()) {
            b.add("return $T.allOf(", CompletableFuture.class);
            for (int i = 0; i < mappedParameters.size(); i++) {
                if (i > 0) b.add(", ");
                b.add("$N", "_future_" + mappedParameters.get(i).name);
            }
            b.add(").thenCompose(_unused_ -> {$>\n");
            for (var mappedParameter : mappedParameters) {
                b.addStatement("final $T $N", mappedParameter.type, mappedParameter.name);
                b.beginControlFlow("try");
                b.addStatement("$N = $N.getNow(null)", mappedParameter.name, "_future_" + mappedParameter.name);
                b.nextControlFlow("catch ($T _e)", CompletionException.class);
                b.addStatement("if (_e.getCause() instanceof $T) return $T.failedFuture(_e)", httpServerResponse, CompletableFuture.class);
                b.addStatement("return $T.failedFuture($T.of(400, _e.getCause()))", CompletableFuture.class, httpServerResponseException);
                b.endControlFlow();
            }
        }
        b.addStatement("var oldCtx = $T.current()", CommonClassNames.context);
        b.addStatement("_ctx.inject()");
        b.beginControlFlow("try");
        var returnType = (DeclaredType) requestMappingData.executableType().getReturnType();
        if (returnType.getTypeArguments().get(0).toString().equals("java.lang.Void")) {
            b.add("return _controller.$N($L).contextWrite(_rctx -> $T.inject(_rctx, _ctx)).toFuture()\n", requestMappingData.executableElement().getSimpleName(), executeParameters, CommonClassNames.context.nestedClass("Reactor"));
            b.add("  .thenApply(__unused_ -> $T.of(200));\n", HttpServerClassNames.httpServerResponse);
        } else if (returnType.getTypeArguments().get(0).toString().equals(httpServerResponse.canonicalName())) {
            b.add("return _controller.$N($L).contextWrite(_rctx -> $T.inject(_rctx, _ctx)).toFuture();\n", requestMappingData.executableElement().getSimpleName(), executeParameters, CommonClassNames.context.nestedClass("Reactor"));
        } else {
            b.add("return _controller.$N($L).contextWrite(_rctx -> $T.inject(_rctx, _ctx)).toFuture()$>\n", requestMappingData.executableElement().getSimpleName(), executeParameters, CommonClassNames.context.nestedClass("Reactor"));
            b.add(".thenApply(_result -> {$>\n");
            b.add("try {\n");
            b.add("  return _responseMapper.apply(_ctx, _request, _result);\n");
            b.add("} catch (Exception e) {\n");
            b.add("  throw new $T(e);\n", CompletionException.class);
            b.add("}");
            b.add("$<$<\n});\n");
        }
        b.nextControlFlow("catch (Exception e)");
        b.addStatement("return $T.failedFuture(e)", CompletableFuture.class);
        b.nextControlFlow("finally");
        b.addStatement("oldCtx.inject()");
        b.endControlFlow();

        if (!mappedParameters.isEmpty()) {
            b.add("$<\n});\n");
        }
        return b.build();
    }

    private CodeBlock generateFutureCall(RequestMappingData requestMappingData, List<Parameter> parameters, String requestName) {
        var executeParameters = parameters.stream()
            .map(_p -> _p.variableElement.getSimpleName())
            .collect(Collectors.joining(", "));
        var mappedParameters = parameters.stream().filter(p -> p.parameterType == MAPPED_HTTP_REQUEST).toList();
        var b = CodeBlock.builder();
        for (var mappedParameter : mappedParameters) {
            b.addStatement("final $T $N", ParameterizedTypeName.get(ClassName.get(CompletableFuture.class), TypeName.get(mappedParameter.type)), "_future_" + mappedParameter.name);
            b.beginControlFlow("try");
            b.addStatement("$N = $LHttpRequestMapper.apply($L).toCompletableFuture()", "_future_" + mappedParameter.name, mappedParameter.name, requestName);
            b.nextControlFlow("catch ($T _e)", CompletionException.class);
            b.addStatement("if (_e.getCause() instanceof $T && _e.getCause() instanceof $T) throw ($T) _e.getCause()", httpServerResponse, RuntimeException.class, RuntimeException.class);
            b.addStatement("throw $T.of(400, _e.getCause())", httpServerResponseException);
            b.nextControlFlow("catch (Exception _e)");
            b.addStatement("if (_e instanceof $T) return $T.failedFuture(_e)", httpServerResponse, CompletableFuture.class);
            b.addStatement("return $T.failedFuture($T.of(400, _e))", CompletableFuture.class, httpServerResponseException);
            b.endControlFlow();
        }
        if (!mappedParameters.isEmpty()) {
            b.add("return $T.allOf(", CompletableFuture.class);
            for (int i = 0; i < mappedParameters.size(); i++) {
                if (i > 0) b.add(", ");
                b.add("$N", "_future_" + mappedParameters.get(i).name);
            }
            b.add(").thenCompose(_unused_ -> {$>\n");
            for (var mappedParameter : mappedParameters) {
                b.addStatement("final $T $N", mappedParameter.type, mappedParameter.name);
                b.beginControlFlow("try");
                b.addStatement("$N = $N.getNow(null)", mappedParameter.name, "_future_" + mappedParameter.name);
                b.nextControlFlow("catch ($T _e)", CompletionException.class);
                b.addStatement("if (_e.getCause() instanceof $T) return $T.failedFuture(_e)", httpServerResponse, CompletableFuture.class);
                b.addStatement("return $T.failedFuture($T.of(400, _e.getCause()))", CompletableFuture.class, httpServerResponseException);
                b.endControlFlow();
            }
        }
        b.addStatement("var oldCtx = $T.current()", CommonClassNames.context);
        b.addStatement("_ctx.inject()");
        b.beginControlFlow("try");
        var returnType = (DeclaredType) requestMappingData.executableType().getReturnType();
        if (returnType.getTypeArguments().get(0).toString().equals("java.lang.Void")) {
            b.add("return _controller.$N($L)\n", requestMappingData.executableElement().getSimpleName(), executeParameters);
            b.add("  .thenApply(__unused_ -> $T.of(200));\n", HttpServerClassNames.httpServerResponse);
        } else if (returnType.getTypeArguments().get(0).toString().equals(httpServerResponse.canonicalName())) {
            b.add("return _controller.$N($L);\n", requestMappingData.executableElement().getSimpleName(), executeParameters);
        } else {
            b.add("return _controller.$N($L)$>\n", requestMappingData.executableElement().getSimpleName(), executeParameters);
            b.add(".thenApply(_result -> {$>\n");
            b.add("try {\n");
            b.add("  return _responseMapper.apply(_ctx, _request, _result);\n");
            b.add("} catch (Exception e) {\n");
            b.add("  throw new $T(e);\n", CompletionException.class);
            b.add("}");
            b.add("$<$<\n});\n");
        }
        b.nextControlFlow("catch (Exception e)");
        b.addStatement("return $T.failedFuture(e)", CompletableFuture.class);
        b.nextControlFlow("finally");
        b.addStatement("oldCtx.inject()");
        b.endControlFlow();

        if (!mappedParameters.isEmpty()) {
            b.add("$<\n});\n");
        }
        return b.build();
    }

    private CodeBlock generateBlockingCall(RequestMappingData requestMappingData, List<Parameter> parameters, String requestName) {
        var executeParameters = parameters.stream()
            .map(_p -> _p.variableElement.getSimpleName())
            .collect(Collectors.joining(", "));
        var mappedParameters = parameters.stream().filter(p -> p.parameterType == MAPPED_HTTP_REQUEST).toList();
        var b = CodeBlock.builder();
        b.add("return _executor.execute(_ctx, () -> {$>\n");
        for (var mappedParameter : mappedParameters) {
            b.addStatement("final $T $N", TypeName.get(mappedParameter.type), mappedParameter.name);
            b.beginControlFlow("try");
            b.addStatement("$N = $LHttpRequestMapper.apply($L)", mappedParameter.name, mappedParameter.name, requestName);
            b.nextControlFlow("catch ($T _e)", CompletionException.class);
            b.addStatement("if (_e.getCause() instanceof $T && _e.getCause() instanceof $T) throw ($T) _e.getCause()", httpServerResponse, RuntimeException.class, RuntimeException.class);
            b.addStatement("throw $T.of(400, _e.getCause())", httpServerResponseException);
            b.nextControlFlow("catch (Exception _e)");
            b.addStatement("if (_e instanceof $T) throw _e", httpServerResponse);
            b.addStatement("throw $T.of(400, _e)", httpServerResponseException);
            b.endControlFlow();
        }
        if (CommonUtils.isVoid(requestMappingData.executableType().getReturnType())) {
            b.addStatement("_controller.$N($L)", requestMappingData.executableElement().getSimpleName(), executeParameters);
            b.add("return $T.of(200);", HttpServerClassNames.httpServerResponse);
        } else if (HttpServerClassNames.httpServerResponse.canonicalName().equals(requestMappingData.executableElement().getReturnType().toString())) {
            b.add("return _controller.$N($L);", requestMappingData.executableElement().getSimpleName(), executeParameters);
        } else {
            b.addStatement("var _result = _controller.$N($L)", requestMappingData.executableElement().getSimpleName(), executeParameters);
            b.add("return _responseMapper.apply(_ctx, _request, _result);");
        }
        b.add("$<\n});");
        return b.build();
    }

    private CodeBlock definePathParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.lang.Integer", "int" -> code.add("$L = $T.parseIntegerPathParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.lang.Long", "long" -> code.add("$L = $T.parseLongPathParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.lang.Double", "double" -> code.add("$L = $T.parseDoublePathParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.lang.String" -> code.add("$L = $T.parseStringPathParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.UUID" -> code.add("$L = $T.parseUUIDPathParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            default -> {
                var parameterReaderType = ParameterizedTypeName.get(
                    HttpServerClassNames.stringParameterReader,
                    TypeName.get(parameter.type)
                );
                var parameterReaderName = "_" + parameter.variableElement.getSimpleName().toString() + "Reader";
                methodBuilder.addParameter(parameterReaderType, parameterReaderName);
                code.add("$L = $L.read($T.parseStringPathParameter(_request, $S));", parameter.variableElement, parameterReaderName, requestHandlerUtils, parameter.name);
                return code.build();
            }
        }
        return code.build();
    }

    private CodeBlock defineHeaderParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalStringHeaderParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseStringHeaderParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Optional<java.lang.String>" ->
                code.add("$L = $T.ofNullable($T.parseOptionalStringHeaderParameter(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.util.List<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalStringListHeaderParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseStringListHeaderParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "java.util.Optional<java.lang.Integer>" ->
                code.add("$L = $T.ofNullable($T.parseOptionalIntegerHeaderParameter(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Integer" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalIntegerHeaderParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseIntegerHeaderParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalIntegerListHeaderParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseIntegerListHeaderParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            default -> {
                if (CommonUtils.isOptional(parameter.type)) {
                    var optionalParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(optionalParameter)
                    );

                    var parameterReaderName = "_" + parameter.variableElement.getSimpleName().toString() + "Reader";

                    methodBuilder.addParameter(parameterReaderType, parameterReaderName);
                    code.add("$L = $T.ofNullable($T.parseOptionalStringHeaderParameter(_request, $S)).map($L::read);", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name, parameterReaderName);
                    return code.build();
                }

                if (CommonUtils.isList(parameter.type)) {
                    var listParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(listParameter)
                    );

                    final String parameterReaderName = "_" + parameter.name + "Reader";
                    methodBuilder.addParameter(parameterReaderType, parameterReaderName);

                    if (isNullable(parameter)) {
                        code.add("$L = $T.ofNullable($T.parseOptionalStringListHeaderParameter(_request, $S)).map(_var_$L -> _var_$L.stream().map($L::read).toList()).orElse(null);",
                            parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name, parameter.variableElement, parameter.variableElement, parameterReaderName);
                    } else {
                        code.add("$L = $T.parseStringListHeaderParameter(_request, $S).stream().map($L::read).toList();", parameter.variableElement, requestHandlerUtils, parameter.name, parameterReaderName);
                    }

                    return code.build();
                }

                var parameterReaderType = ParameterizedTypeName.get(
                    HttpServerClassNames.stringParameterReader,
                    TypeName.get(parameter.type)
                );
                var parameterReaderName = "_" + parameter.variableElement.getSimpleName() + "Reader";
                methodBuilder.addParameter(parameterReaderType, parameterReaderName);

                if (isNullable(parameter)) {
                    var transitParameterName = "_" + parameter.variableElement.getSimpleName() + "RawValue";
                    code.add("var $N = $T.parseOptionalStringHeaderParameter(_request, $S);\n", transitParameterName, requestHandlerUtils, parameter.name);
                    code.add("$L = $L == null ? null : $L.read($L);", parameter.variableElement, transitParameterName, parameterReaderName, transitParameterName);
                } else {
                    code.add("$L = $L.read($T.parseStringHeaderParameter(_request, $S));", parameter.variableElement, parameterReaderName, requestHandlerUtils, parameter.name);
                }
                return code.build();
            }
        }
        return code.build();
    }

    private CodeBlock defineCookieParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalCookieString(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseCookieString(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "ru.tinkoff.kora.http.common.cookie.Cookie" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalCookie(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseCookie(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Optional<java.lang.String>" -> {
                code.add("$L = $T.ofNullable($T.parseOptionalCookieString(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            }
            case "java.util.Optional<ru.tinkoff.kora.http.common.cookie.Cookie>" -> {
                code.add("$L = $T.ofNullable($T.parseOptionalCookie(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            }

            default -> {
                if (CommonUtils.isOptional(parameter.type)) {
                    var optionalParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(optionalParameter)
                    );

                    var parameterReaderName = "_" + parameter.variableElement.getSimpleName().toString() + "Reader";

                    methodBuilder.addParameter(parameterReaderType, parameterReaderName);
                    code.add("var $L_cookie = $T.parseOptionalCookieString(_request, $S);\n", parameter.variableElement, requestHandlerUtils, parameter.name);
                    code.add("$L = $T.ofNullable($L_cookie).map($L::read);", parameter.variableElement, Optional.class, parameter.variableElement, parameterReaderName);
                    return code.build();
                }

                var parameterReaderType = ParameterizedTypeName.get(
                    HttpServerClassNames.stringParameterReader,
                    TypeName.get(parameter.type).box()
                );
                var parameterReaderName = "_" + parameter.variableElement.getSimpleName() + "Reader";
                methodBuilder.addParameter(parameterReaderType, parameterReaderName);

                if (isNullable(parameter)) {
                    var transitParameterName = "_" + parameter.variableElement.getSimpleName() + "RawValue";
                    code.add("var $N = $T.parseOptionalCookieString(_request, $S);\n", transitParameterName, requestHandlerUtils, parameter.name);
                    code.add("$L = $L == null ? null : $L.read($L);", parameter.variableElement, transitParameterName, parameterReaderName, transitParameterName);
                } else {
                    code.add("$L = $L.read($T.parseCookieString(_request, $S));", parameter.variableElement, parameterReaderName, requestHandlerUtils, parameter.name);
                }
                return code.build();
            }
        }
        return code.build();
    }

    private CodeBlock defineQueryParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.util.UUID" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalUuidQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseUuidQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Optional<java.util.UUID>" ->
                code.add("$L = $T.ofNullable($T.parseOptionalUuidQueryParameter(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.util.List<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalUuidListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseUuidListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "int" -> code.add("$L = $T.parseIntegerQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Integer>" ->
                code.add("$L = $T.ofNullable($T.parseOptionalIntegerQueryParameter(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Integer" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalIntegerQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseIntegerQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalIntegerListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseIntegerListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "long" -> code.add("$L = $T.parseLongQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Long>" ->
                code.add("$L = $T.ofNullable($T.parseOptionalLongQueryParameter(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Long" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalLongQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseLongQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalLongListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseLongListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "double" -> code.add("$L = $T.parseDoubleQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Double>" ->
                code.add("$L = $T.ofNullable($T.parseOptionalDoubleQueryParameter(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Double" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalDoubleQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseDoubleQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalDoubleListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseDoubleListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "java.util.Optional<java.lang.String>" ->
                code.add("$L = $T.ofNullable($T.parseOptionalStringQueryParameter(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalStringQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseStringQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalStringListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseStringListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "boolean" -> code.add("$L = $T.parseBooleanQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Boolean>" ->
                code.add("$L = $T.ofNullable($T.parseOptionalBooleanQueryParameter(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Boolean" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalBooleanQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseBooleanQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Boolean>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseOptionalBooleanListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseBooleanListQueryParameter(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            default -> {
                final String readerParameterName = "_" + parameter.name + "Reader";

                if (CommonUtils.isOptional(parameter.type)) {
                    var optionalParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(optionalParameter)
                    );

                    methodBuilder.addParameter(parameterReaderType, readerParameterName);
                    code.add("$L = $T.ofNullable($T.parseOptionalStringQueryParameter(_request, $S)).map($L::read);", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name, readerParameterName);
                    return code.build();
                }
                if (CommonUtils.isList(parameter.type)) {
                    var listParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(listParameter)
                    );
                    if (isNullable(parameter)) {
                        methodBuilder.addParameter(parameterReaderType, readerParameterName);
                        code.add("$L = $T.ofNullable($T.parseOptionalStringListQueryParameter(_request, $S)).map(_var_$L -> _var_$L.stream().map($L::read).toList()).orElse(null);",
                            parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name, parameter.variableElement, parameter.variableElement, readerParameterName);
                    } else {
                        methodBuilder.addParameter(parameterReaderType, readerParameterName);
                        code.add("$L = $T.parseStringListQueryParameter(_request, $S).stream().map($L::read).toList();", parameter.variableElement, requestHandlerUtils, parameter.name, readerParameterName);
                    }

                    return code.build();

                }

                var parameterReaderType = ParameterizedTypeName.get(
                    HttpServerClassNames.stringParameterReader,
                    TypeName.get(parameter.type)
                );
                methodBuilder.addParameter(parameterReaderType, readerParameterName);

                if (isNullable(parameter)) {
                    code.add("$L = $T.ofNullable($T.parseOptionalStringQueryParameter(_request, $S)).map($L::read).orElse(null);", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name, readerParameterName);
                } else {
                    code.add("$L = $L.read($T.parseStringQueryParameter(_request, $S));", parameter.variableElement, readerParameterName, requestHandlerUtils, parameter.name);
                }
                return code.build();
            }
        }
        return code.build();
    }

    private boolean isNullable(Parameter parameter) {
        return CommonUtils.isNullable(parameter.variableElement);
    }

    @Nullable
    private List<Parameter> parseParameters(RequestMappingData requestMappingData) {
        var rawParameters = requestMappingData.executableElement().getParameters();
        var parameters = new ArrayList<Parameter>(rawParameters.size());
        for (int i = 0; i < rawParameters.size(); i++) {
            var parameter = rawParameters.get(i);
            var parameterType = requestMappingData.executableType().getParameterTypes().get(i);
            var query = AnnotationUtils.findAnnotation(parameter, HttpServerClassNames.query);
            if (query != null) {
                var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(query, "value");
                var queryParameterName = value == null || value.isBlank()
                    ? parameter.getSimpleName().toString()
                    : value;

                parameters.add(new Parameter(QUERY, queryParameterName, parameterType, parameter));
                continue;
            }
            var header = AnnotationUtils.findAnnotation(parameter, HttpServerClassNames.header);
            if (header != null) {
                var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(header, "value");
                var headerParameterName = value == null || value.isBlank()
                    ? parameter.getSimpleName().toString()
                    : value;

                parameters.add(new Parameter(HEADER, headerParameterName, parameterType, parameter));
                continue;
            }
            var cookie = AnnotationUtils.findAnnotation(parameter, HttpServerClassNames.cookie);
            if (cookie != null) {
                var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(cookie, "value");
                var cookieParameterName = value == null || value.isBlank()
                    ? parameter.getSimpleName().toString()
                    : value;

                parameters.add(new Parameter(COOKIE, cookieParameterName, parameterType, parameter));
                continue;
            }
            var path = AnnotationUtils.findAnnotation(parameter, HttpServerClassNames.path);
            if (path != null) {
                var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(path, "value");
                var pathParameterName = value == null || value.isBlank()
                    ? parameter.getSimpleName().toString()
                    : value;
                if (requestMappingData.route().contains("{%s}".formatted(pathParameterName))) {
                    parameters.add(new Parameter(PATH, pathParameterName, parameterType, parameter));
                    continue;
                } else {
                    this.processingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Path parameter '%s' is not present in the request mapping path".formatted(pathParameterName),
                        parameter
                    );
                    continue;
                }
            }
            if (parameter.asType().toString().equals(CommonClassNames.context.canonicalName())) {
                parameters.add(new Parameter(CONTEXT, parameter.getSimpleName().toString(), parameterType, parameter));
                continue;
            }
            if (parameter.asType().toString().equals(HttpServerClassNames.httpServerRequest.canonicalName())) {
                parameters.add(new Parameter(REQUEST, parameter.getSimpleName().toString(), parameterType, parameter));
                continue;
            }

            parameters.add(new Parameter(MAPPED_HTTP_REQUEST, parameter.getSimpleName().toString(), parameterType, parameter));
        }

        if (parameters.size() != requestMappingData.executableElement().getParameters().size()) {
            return null;
        }
        return parameters;
    }

    private String methodName(RequestMappingData requestMappingData) {
        final String suffix = requestMappingData.route().endsWith("/")
            ? "_trailing_slash"
            : "";

        return requestMappingData.httpMethod().toLowerCase() + Stream.of(requestMappingData.route().split("[^A-Za-z0-9]+"))
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.joining("_", "_", suffix));
    }

    private boolean isBlocking(RequestMappingData requestMappingData) {
        var returnType = requestMappingData.executableType().getReturnType();
        var isAsync = this.types.isAssignable(returnType, this.completionStageTypeErasure)
            || this.publisherTypeErasure != null && this.types.isAssignable(returnType, this.publisherTypeErasure)
            || this.types.isAssignable(returnType, this.jdkPublisherTypeErasure);
        return !isAsync;
    }


    private void addParameterMappers(MethodSpec.Builder methodBuilder, RequestMappingData requestMappingData, List<Parameter> bodyParameterType) {
        for (var parameter : bodyParameterType) {
            if (parameter.parameterType != MAPPED_HTTP_REQUEST) {
                continue;
            }
            var mapper = requestMappingData.httpRequestMappingData().get(parameter.variableElement);
            var mapperName = parameter.name + "HttpRequestMapper";
            final TypeName mapperType;
            var tags = mapper != null
                ? mapper.toTagAnnotation()
                : null;

            if (mapper != null && mapper.mapperClass() != null) {
                mapperType = TypeName.get(mapper.mapperClass());
            } else {
                var typeMirror = parameter.type;
                var argType = (typeMirror instanceof PrimitiveType pt) ? types.boxedClass(pt).asType() : typeMirror;
                if (isBlocking(requestMappingData)) {
                    mapperType = ParameterizedTypeName.get(httpServerRequestMapper, TypeName.get(argType));
                } else {
                    mapperType = ParameterizedTypeName.get(httpServerRequestMapper, ParameterizedTypeName.get(ClassName.get(CompletionStage.class), TypeName.get(argType)));
                }
            }
            var b = ParameterSpec.builder(mapperType, mapperName);
            if (tags != null) {
                b.addAnnotation(tags);
            }
            methodBuilder.addParameter(b.build());
        }
    }

    @Nullable
    private ParameterSpec detectResponseMapper(RequestMappingData requestMappingData, ExecutableElement method) {
        var tags = requestMappingData.responseMapper() == null
            ? null
            : requestMappingData.responseMapper().toTagAnnotation();
        if (requestMappingData.responseMapper() != null && requestMappingData.responseMapper().mapperClass() != null) {
            var b = ParameterSpec.builder(TypeName.get(requestMappingData.responseMapper().mapperClass()), "_responseMapper");
            if (tags != null) {
                b.addAnnotation(tags);
            }
            return b.build();
        }


        var returnType = requestMappingData.executableType().getReturnType();
        if (returnType.getKind() == TypeKind.ERROR) {
            this.processingEnvironment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Method return type is ERROR", method);
            return null;
        }

        var isAsync = !isBlocking(requestMappingData);

        var returnTypeName = TypeName.get(returnType).box();
        var resultTypeName = isAsync
            ? ((ParameterizedTypeName) returnTypeName).typeArguments.get(0)
            : returnTypeName;
        if (resultTypeName.box().toString().equals("java.lang.Void") && tags == null) {
            return null;
        }
        if (resultTypeName.box().toString().equals(httpServerResponse.canonicalName()) && tags == null) {
            return null;
        }

        var mapperType = ParameterizedTypeName.get(httpServerResponseMapper, resultTypeName);
        var b = ParameterSpec.builder(mapperType, "_responseMapper");
        if (tags != null) {
            b.addAnnotation(tags);
        }
        return b.build();
    }

    private record Parameter(ParameterType parameterType, String name, TypeMirror type,
                             VariableElement variableElement) {
    }

    enum ParameterType {
        MAPPED_HTTP_REQUEST,
        HEADER,
        COOKIE,
        QUERY,
        PATH,
        REQUEST,
        CONTEXT
    }
}
