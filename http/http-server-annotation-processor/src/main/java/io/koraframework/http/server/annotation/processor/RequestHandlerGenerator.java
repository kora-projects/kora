package io.koraframework.http.server.annotation.processor;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.TagUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.koraframework.http.server.annotation.processor.HttpServerClassNames.*;
import static io.koraframework.http.server.annotation.processor.RequestHandlerGenerator.ParameterType.*;

public class RequestHandlerGenerator {

    private final ProcessingEnvironment processingEnvironment;

    public RequestHandlerGenerator(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }


    @Nullable
    public MethodSpec generate(TypeElement controller, RequestMappingData requestMappingData) {
        var methodName = this.methodName(requestMappingData);
        var parameters = parseParameters(requestMappingData);
        if (parameters == null) {
            return null;
        }

        var tag = TagUtils.parseTagValue(controller);
        var paramBuilder = ParameterSpec.builder(TypeName.get(controller.asType()), "_controller");
        if (tag != null) {
            paramBuilder.addAnnotation(TagUtils.makeAnnotationSpec(tag));
        }

        var methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(httpServerRequestHandler)
            .addParameter(paramBuilder.build());
        if (tag != null) {
           methodBuilder.addAnnotation(TagUtils.makeAnnotationSpec(tag));
        }

        this.addParameterMappers(methodBuilder, requestMappingData, parameters);
        var responseMapper = this.detectResponseMapper(requestMappingData, requestMappingData.executableElement());
        if (responseMapper != null) {
            methodBuilder.addParameter(responseMapper);
        }

        var handlerCode = this.buildRequestHandler(controller, requestMappingData, parameters, methodBuilder);

        methodBuilder.addCode("return $T.of($S, $S, (_request) -> {$>\n$L\n$<});",
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
            requestMappingBlock.add("return ");
            requestMappingBlock.add("$L.intercept($L, ($N) -> $>{\n", interceptorName, requestName, newRequestName);
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
                case MAPPED_HTTP_REQUEST, REQUEST -> CodeBlock.of("");
            };
            handler.add(codeBlock);
            handler.add("\n");
        }

        if (hasNonBodyParams) {
            handler.nextControlFlow("catch (Exception _e)");
            handler.beginControlFlow("if (_e instanceof $T)", httpServerResponse);
            handler.addStatement("throw _e");
            handler.nextControlFlow("else");
            handler.addStatement("throw $T.of(400, _e)", httpServerResponseException);
            handler.endControlFlow();
            handler.endControlFlow();
            handler.add("\n");
        }

        if (CommonUtils.isPublisher(returnType)) {
            processingEnvironment.getMessager().printWarning("Method return type is Publisher<T> which is unsupported and has no meaning", requestMappingData.executableElement());
        } else if (CommonUtils.isFuture(returnType)) {
            processingEnvironment.getMessager().printWarning("Method return type is Future<T> which is unsupported and has no meaning", requestMappingData.executableElement());
        } else if (CommonUtils.isCompletionStage(returnType)) {
            processingEnvironment.getMessager().printWarning("Method return type is CompletionStage<T> which is unsupported and has no meaning", requestMappingData.executableElement());
        }
        var controllerCall = this.generateBlockingCall(requestMappingData, parameters, requestName);

        handler.add(controllerCall);

        for (int i = 0; i < interceptors.size(); i++) {
            handler.addStatement("$<})");
        }
        return handler.build();
    }

    private CodeBlock generateBlockingCall(RequestMappingData requestMappingData, List<Parameter> parameters, String requestName) {
        var executeParameters = parameters.stream()
            .map(_p -> _p.variableElement.getSimpleName())
            .collect(Collectors.joining(", "));
        var mappedParameters = parameters.stream().filter(p -> p.parameterType == MAPPED_HTTP_REQUEST).toList();
        var b = CodeBlock.builder();
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
            b.addStatement("return $T.of(200)", HttpServerClassNames.httpServerResponse);
        } else if (HttpServerClassNames.httpServerResponse.canonicalName().equals(requestMappingData.executableElement().getReturnType().toString())) {
            b.addStatement("return _controller.$N($L)", requestMappingData.executableElement().getSimpleName(), executeParameters);
        } else {
            b.addStatement("var _result = _controller.$N($L)", requestMappingData.executableElement().getSimpleName(), executeParameters);
            b.addStatement("return _responseMapper.apply(_request, _result)");
        }
        return b.build();
    }

    private CodeBlock definePathParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.lang.Boolean", "boolean" -> code.add("$L = $T.parsePathBoolean(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.lang.Integer", "int" -> code.add("$L = $T.parsePathInteger(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.lang.Long", "long" -> code.add("$L = $T.parsePathLong(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.lang.Double", "double" -> code.add("$L = $T.parsePathDouble(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.lang.String" -> code.add("$L = $T.parsePathString(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.UUID" -> code.add("$L = $T.parsePathUuid(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            default -> {
                var parameterReaderType = ParameterizedTypeName.get(
                    HttpServerClassNames.stringParameterReader,
                    TypeName.get(parameter.type)
                );
                var parameterReaderName = "_" + parameter.variableElement.getSimpleName().toString() + "Reader";
                methodBuilder.addParameter(parameterReaderType, parameterReaderName);
                code.add("$L = $L.read($T.parsePathString(_request, $S));", parameter.variableElement, parameterReaderName, requestHandlerUtils, parameter.name);
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
                    code.add("$L = $T.parseHeaderStringNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderString(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Optional<java.lang.String>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderStringNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.util.List<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderStringListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderStringList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderStringSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderStringSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "int" -> code.add("$L = $T.parseHeaderInteger(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Integer>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderIntegerNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Integer" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderIntegerNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderInteger(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderIntegerListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderIntegerList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderIntegerSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderIntegerSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "long" -> code.add("$L = $T.parseHeaderLong(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Long>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderLongNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Long" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderLongNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderLong(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderLongListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderLongList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderLongSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderLongSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "double" -> code.add("$L = $T.parseHeaderDouble(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Double>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderDoubleNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Double" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderDoubleNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderDouble(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderDoubleListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderDoubleList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderDoubleSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderDoubleSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "java.util.Optional<java.util.UUID>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderUuidNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.util.UUID" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderUuidNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderUuid(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderUuidListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderUuidList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderUuidSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderUuidSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
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
                    code.add("$L = $T.ofNullable($T.parseHeaderStringNullable(_request, $S)).map($L::read);", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name, parameterReaderName);
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
                        code.add("$L = $T.parseHeaderSomeListNullable(_request, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, parameter.name, parameterReaderName);
                    } else {
                        code.add("$L = $T.parseHeaderSomeList(_request, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, parameter.name, parameterReaderName);
                    }

                    return code.build();
                }

                if (CommonUtils.isSet(parameter.type)) {
                    var listParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(listParameter)
                    );

                    final String parameterReaderName = "_" + parameter.name + "Reader";
                    methodBuilder.addParameter(parameterReaderType, parameterReaderName);

                    if (isNullable(parameter)) {
                        code.add("$L = $T.parseHeaderSomeSetNullable(_request, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, parameter.name, parameterReaderName);
                    } else {
                        code.add("$L = $T.parseHeaderSomeSet(_request, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, parameter.name, parameterReaderName);
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
                    code.add("var $N = $T.parseHeaderStringNullable(_request, $S);\n", transitParameterName, requestHandlerUtils, parameter.name);
                    code.add("$L = $L == null ? null : $L.read($L);", parameter.variableElement, transitParameterName, parameterReaderName, transitParameterName);
                } else {
                    code.add("$L = $L.read($T.parseHeaderString(_request, $S));", parameter.variableElement, parameterReaderName, requestHandlerUtils, parameter.name);
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
                    code.add("$L = $T.parseCookieStringNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseCookieString(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "io.koraframework.http.common.cookie.Cookie" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseCookieNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseCookie(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Optional<java.lang.String>" -> {
                code.add("$L = $T.ofNullable($T.parseCookieStringNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            }
            case "java.util.Optional<io.koraframework.http.common.cookie.Cookie>" -> {
                code.add("$L = $T.ofNullable($T.parseCookieNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
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
                    code.add("var $L_cookie = $T.parseCookieStringNullable(_request, $S);\n", parameter.variableElement, requestHandlerUtils, parameter.name);
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
                    code.add("var $N = $T.parseCookieStringNullable(_request, $S);\n", transitParameterName, requestHandlerUtils, parameter.name);
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
                    code.add("$L = $T.parseQueryUuidNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryUuid(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Optional<java.util.UUID>" ->
                code.add("$L = $T.ofNullable($T.parseQueryUuidNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.util.List<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryUuidListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryUuidList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryUuidSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryUuidSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "int" -> code.add("$L = $T.parseQueryInteger(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Integer>" ->
                code.add("$L = $T.ofNullable($T.parseQueryIntegerNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Integer" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryIntegerNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryInteger(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryIntegerListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryIntegerList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryIntegerSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryIntegerSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "long" -> code.add("$L = $T.parseQueryLong(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Long>" ->
                code.add("$L = $T.ofNullable($T.parseQueryLongNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Long" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryLongNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryLong(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryLongListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryLongList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryLongSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryLongSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "double" -> code.add("$L = $T.parseQueryDouble(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Double>" ->
                code.add("$L = $T.ofNullable($T.parseQueryDoubleNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Double" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryDoubleNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryDouble(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryDoubleListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryDoubleList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryDoubleSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryDoubleSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "java.util.Optional<java.lang.String>" ->
                code.add("$L = $T.ofNullable($T.parseQueryStringNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryStringNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryString(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryStringListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryStringList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryStringSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryStringSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }

            case "boolean" -> code.add("$L = $T.parseQueryBoolean(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
            case "java.util.Optional<java.lang.Boolean>" ->
                code.add("$L = $T.ofNullable($T.parseQueryBooleanNullable(_request, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name);
            case "java.lang.Boolean" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryBooleanNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryBoolean(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.List<java.lang.Boolean>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryBooleanListNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryBooleanList(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Boolean>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryBooleanSetNullable(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryBooleanSet(_request, $S);", parameter.variableElement, requestHandlerUtils, parameter.name);
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
                    code.add("$L = $T.ofNullable($T.parseQueryStringNullable(_request, $S)).map($L::read);", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name, readerParameterName);
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
                        code.add("$L = $T.parseQuerySomeListNullable(_request, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, parameter.name, readerParameterName);
                    } else {
                        methodBuilder.addParameter(parameterReaderType, readerParameterName);
                        code.add("$L = $T.parseQuerySomeList(_request, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, parameter.name, readerParameterName);
                    }

                    return code.build();
                }

                if (CommonUtils.isSet(parameter.type)) {
                    var listParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(listParameter)
                    );
                    if (isNullable(parameter)) {
                        methodBuilder.addParameter(parameterReaderType, readerParameterName);
                        code.add("$L = $T.parseQuerySomeSetNullable(_request, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, parameter.name, readerParameterName);
                    } else {
                        methodBuilder.addParameter(parameterReaderType, readerParameterName);
                        code.add("$L = $T.parseQuerySomeSet(_request, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, parameter.name, readerParameterName);
                    }

                    return code.build();
                }

                var parameterReaderType = ParameterizedTypeName.get(
                    HttpServerClassNames.stringParameterReader,
                    TypeName.get(parameter.type)
                );
                methodBuilder.addParameter(parameterReaderType, readerParameterName);

                if (isNullable(parameter)) {
                    code.add("$L = $T.ofNullable($T.parseQueryStringNullable(_request, $S)).map($L::read).orElse(null);", parameter.variableElement, Optional.class, requestHandlerUtils, parameter.name, readerParameterName);
                } else {
                    code.add("$L = $L.read($T.parseQueryString(_request, $S));", parameter.variableElement, readerParameterName, requestHandlerUtils, parameter.name);
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

        return requestMappingData.httpMethod().toLowerCase(Locale.ROOT) + Stream.of(requestMappingData.route().split("[^A-Za-z0-9]+"))
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.joining("_", "_", suffix));
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
                mapperType = ParameterizedTypeName.get(httpServerRequestMapper, TypeName.get(typeMirror).box());
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

        var returnTypeName = TypeName.get(returnType);
        if (returnTypeName.withoutAnnotations().equals(TypeName.VOID) && tags == null) {
            return null;
        }
        if (returnTypeName.withoutAnnotations().equals(httpServerResponse) && tags == null) {
            return null;
        }

        var mapperType = ParameterizedTypeName.get(httpServerResponseMapper, returnTypeName.box());
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
        REQUEST
    }
}
