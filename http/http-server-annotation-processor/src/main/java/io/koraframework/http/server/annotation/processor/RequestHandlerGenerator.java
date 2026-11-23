package io.koraframework.http.server.annotation.processor;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.TagUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
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
import java.util.Set;
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
    public MethodSpec generate(TypeElement controller, RequestMappingData requestMappingData, List<ExecutableElement> overrideChain, Set<String> generatedNames) {
        var methodName = this.methodName(requestMappingData, generatedNames);
        var parameters = parseParameters(requestMappingData, overrideChain);
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

        var handlerCode = this.buildRequestHandler(controller, requestMappingData, overrideChain, parameters, methodBuilder);

        methodBuilder.addCode("return $T.of($S, $S, (_request) -> {$>\n$L\n$<});",
            HttpServerClassNames.httpServerRequestHandlerImpl,
            requestMappingData.httpMethod().toUpperCase(),
            requestMappingData.route(),
            handlerCode
        );

        return methodBuilder.build();
    }

    private CodeBlock buildRequestHandler(TypeElement controller, RequestMappingData requestMappingData, List<ExecutableElement> overrideChain, List<Parameter> parameters, MethodSpec.Builder methodBuilder) {
        var handler = CodeBlock.builder();
        var returnType = requestMappingData.executableType().getReturnType();

        var hasNonBodyParams = false;

        var interceptors = Stream.concat(
                AnnotationUtils.findAnnotations(controller, interceptWithClassName, interceptWithContainerClassName).stream().map(HttpServerUtils::parseInterceptor),
                overrideChain.reversed().stream().flatMap(m -> AnnotationUtils.findAnnotations(m, interceptWithClassName, interceptWithContainerClassName).stream()).map(HttpServerUtils::parseInterceptor)
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
                case REQUEST -> handler.add("var $N = $N;\n", parameter.name(), requestName);
                default -> {}
            }
        }

        if (hasNonBodyParams) {
            handler.beginControlFlow("try");
        }

        for (var parameter : parameters) {
            var codeBlock = switch (parameter.parameterType) {
                case PATH -> this.definePathParameter(parameter, methodBuilder, requestName);
                case QUERY -> this.defineQueryParameter(parameter, methodBuilder, requestName);
                case HEADER -> this.defineHeaderParameter(parameter, methodBuilder, requestName);
                case COOKIE -> this.defineCookieParameter(parameter, methodBuilder, requestName);
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
        } else if (TypeName.get(requestMappingData.executableType().getReturnType()).withoutAnnotations().equals(HttpServerClassNames.httpServerResponse)) {
            b.addStatement("return _controller.$N($L)", requestMappingData.executableElement().getSimpleName(), executeParameters);
        } else {
            b.addStatement("var _result = _controller.$N($L)", requestMappingData.executableElement().getSimpleName(), executeParameters);
            b.addStatement("return _responseMapper.apply($N, _result)", requestName);
        }
        return b.build();
    }

    private CodeBlock definePathParameter(Parameter parameter, MethodSpec.Builder methodBuilder, String requestName) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.lang.Boolean", "boolean" -> code.add("$L = $T.parsePathBoolean($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Integer", "int" -> code.add("$L = $T.parsePathInteger($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Long", "long" -> code.add("$L = $T.parsePathLong($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Double", "double" -> code.add("$L = $T.parsePathDouble($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.String" -> code.add("$L = $T.parsePathString($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.util.UUID" -> code.add("$L = $T.parsePathUuid($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            default -> {
                var parameterReaderType = ParameterizedTypeName.get(
                    HttpServerClassNames.stringParameterReader,
                    TypeName.get(parameter.type).box()
                );
                var parameterReaderName = "_" + parameter.variableElement.getSimpleName().toString() + "Reader";
                methodBuilder.addParameter(parameterReaderType, parameterReaderName);
                code.add("$L = $L.read($T.parsePathString($N, $S));", parameter.variableElement, parameterReaderName, requestHandlerUtils, requestName, parameter.name);
                return code.build();
            }
        }
        return code.build();
    }

    private CodeBlock defineHeaderParameter(Parameter parameter, MethodSpec.Builder methodBuilder, String requestName) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderStringNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderString($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Optional<java.lang.String>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderStringNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.util.List<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderStringListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderStringList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderStringSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderStringSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "int" -> code.add("$L = $T.parseHeaderInteger($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.util.Optional<java.lang.Integer>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderIntegerNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Integer" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderIntegerNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderInteger($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderIntegerListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderIntegerList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderIntegerSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderIntegerSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "long" -> code.add("$L = $T.parseHeaderLong($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.util.Optional<java.lang.Long>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderLongNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Long" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderLongNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderLong($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderLongListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderLongList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderLongSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderLongSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "double" -> code.add("$L = $T.parseHeaderDouble($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.util.Optional<java.lang.Double>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderDoubleNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Double" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderDoubleNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderDouble($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderDoubleListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderDoubleList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderDoubleSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderDoubleSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "java.util.Optional<java.util.UUID>" ->
                code.add("$L = $T.ofNullable($T.parseHeaderUuidNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.util.UUID" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderUuidNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderUuid($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderUuidListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderUuidList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseHeaderUuidSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseHeaderUuidSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
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
                    code.add("$L = $T.ofNullable($T.parseHeaderStringNullable($N, $S)).map($L::read);", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name, parameterReaderName);
                    return code.build();
                }

                if (CommonUtils.isList(parameter.type)) {
                    var listParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(listParameter)
                    );

                    final String parameterReaderName = "_" + parameter.variableElement.getSimpleName() + "Reader";
                    methodBuilder.addParameter(parameterReaderType, parameterReaderName);

                    if (isNullable(parameter)) {
                        code.add("$L = $T.parseHeaderSomeListNullable($N, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, requestName, parameter.name, parameterReaderName);
                    } else {
                        code.add("$L = $T.parseHeaderSomeList($N, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, requestName, parameter.name, parameterReaderName);
                    }

                    return code.build();
                }

                if (CommonUtils.isSet(parameter.type)) {
                    var listParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(listParameter)
                    );

                    final String parameterReaderName = "_" + parameter.variableElement.getSimpleName() + "Reader";
                    methodBuilder.addParameter(parameterReaderType, parameterReaderName);

                    if (isNullable(parameter)) {
                        code.add("$L = $T.parseHeaderSomeSetNullable($N, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, requestName, parameter.name, parameterReaderName);
                    } else {
                        code.add("$L = $T.parseHeaderSomeSet($N, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, requestName, parameter.name, parameterReaderName);
                    }

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
                    code.add("var $N = $T.parseHeaderStringNullable($N, $S);\n", transitParameterName, requestHandlerUtils, requestName, parameter.name);
                    code.add("$L = $L == null ? null : $L.read($L);", parameter.variableElement, transitParameterName, parameterReaderName, transitParameterName);
                } else {
                    code.add("$L = $L.read($T.parseHeaderString($N, $S));", parameter.variableElement, parameterReaderName, requestHandlerUtils, requestName, parameter.name);
                }
                return code.build();
            }
        }
        return code.build();
    }

    private CodeBlock defineCookieParameter(Parameter parameter, MethodSpec.Builder methodBuilder, String requestName) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseCookieStringNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseCookieString($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "io.koraframework.http.common.cookie.Cookie" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseCookieNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseCookie($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Optional<java.lang.String>" -> {
                code.add("$L = $T.ofNullable($T.parseCookieStringNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            }
            case "java.util.Optional<io.koraframework.http.common.cookie.Cookie>" -> {
                code.add("$L = $T.ofNullable($T.parseCookieNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
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
                    code.add("var $L_cookie = $T.parseCookieStringNullable($N, $S);\n", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
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
                    code.add("var $N = $T.parseCookieStringNullable($N, $S);\n", transitParameterName, requestHandlerUtils, requestName, parameter.name);
                    code.add("$L = $L == null ? null : $L.read($L);", parameter.variableElement, transitParameterName, parameterReaderName, transitParameterName);
                } else {
                    code.add("$L = $L.read($T.parseCookieString($N, $S));", parameter.variableElement, parameterReaderName, requestHandlerUtils, requestName, parameter.name);
                }
                return code.build();
            }
        }
        return code.build();
    }

    private CodeBlock defineQueryParameter(Parameter parameter, MethodSpec.Builder methodBuilder, String requestName) {
        var code = CodeBlock.builder();
        var typeString = TypeName.get(parameter.type).withoutAnnotations().toString();
        switch (typeString) {
            case "java.util.UUID" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryUuidNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryUuid($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Optional<java.util.UUID>" ->
                code.add("$L = $T.ofNullable($T.parseQueryUuidNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.util.List<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryUuidListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryUuidList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryUuidSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryUuidSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "int" -> code.add("$L = $T.parseQueryInteger($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.util.Optional<java.lang.Integer>" ->
                code.add("$L = $T.ofNullable($T.parseQueryIntegerNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Integer" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryIntegerNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryInteger($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryIntegerListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryIntegerList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryIntegerSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryIntegerSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "long" -> code.add("$L = $T.parseQueryLong($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.util.Optional<java.lang.Long>" ->
                code.add("$L = $T.ofNullable($T.parseQueryLongNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Long" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryLongNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryLong($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryLongListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryLongList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryLongSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryLongSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "double" -> code.add("$L = $T.parseQueryDouble($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.util.Optional<java.lang.Double>" ->
                code.add("$L = $T.ofNullable($T.parseQueryDoubleNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Double" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryDoubleNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryDouble($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryDoubleListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryDoubleList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryDoubleSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryDoubleSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "java.util.Optional<java.lang.String>" ->
                code.add("$L = $T.ofNullable($T.parseQueryStringNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryStringNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryString($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryStringListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryStringList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryStringSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryStringSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            case "boolean" -> code.add("$L = $T.parseQueryBoolean($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
            case "java.util.Optional<java.lang.Boolean>" ->
                code.add("$L = $T.ofNullable($T.parseQueryBooleanNullable($N, $S));", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name);
            case "java.lang.Boolean" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryBooleanNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryBoolean($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.List<java.lang.Boolean>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryBooleanListNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryBooleanList($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }
            case "java.util.Set<java.lang.Boolean>" -> {
                if (isNullable(parameter)) {
                    code.add("$L = $T.parseQueryBooleanSetNullable($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                } else {
                    code.add("$L = $T.parseQueryBooleanSet($N, $S);", parameter.variableElement, requestHandlerUtils, requestName, parameter.name);
                }
            }

            default -> {
                final String readerParameterName = "_" + parameter.variableElement.getSimpleName() + "Reader";

                if (CommonUtils.isOptional(parameter.type)) {
                    var optionalParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = ParameterizedTypeName.get(
                        HttpServerClassNames.stringParameterReader,
                        TypeName.get(optionalParameter)
                    );

                    methodBuilder.addParameter(parameterReaderType, readerParameterName);
                    code.add("$L = $T.ofNullable($T.parseQueryStringNullable($N, $S)).map($L::read);", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name, readerParameterName);
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
                        code.add("$L = $T.parseQuerySomeListNullable($N, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, requestName, parameter.name, readerParameterName);
                    } else {
                        methodBuilder.addParameter(parameterReaderType, readerParameterName);
                        code.add("$L = $T.parseQuerySomeList($N, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, requestName, parameter.name, readerParameterName);
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
                        code.add("$L = $T.parseQuerySomeSetNullable($N, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, requestName, parameter.name, readerParameterName);
                    } else {
                        methodBuilder.addParameter(parameterReaderType, readerParameterName);
                        code.add("$L = $T.parseQuerySomeSet($N, $S, $L);",
                            parameter.variableElement, requestHandlerUtils, requestName, parameter.name, readerParameterName);
                    }

                    return code.build();
                }

                var parameterReaderType = ParameterizedTypeName.get(
                    HttpServerClassNames.stringParameterReader,
                    TypeName.get(parameter.type).box()
                );
                methodBuilder.addParameter(parameterReaderType, readerParameterName);

                if (isNullable(parameter)) {
                    code.add("$L = $T.ofNullable($T.parseQueryStringNullable($N, $S)).map($L::read).orElse(null);", parameter.variableElement, Optional.class, requestHandlerUtils, requestName, parameter.name, readerParameterName);
                } else {
                    code.add("$L = $L.read($T.parseQueryString($N, $S));", parameter.variableElement, readerParameterName, requestHandlerUtils, requestName, parameter.name);
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
    private List<Parameter> parseParameters(RequestMappingData requestMappingData, List<ExecutableElement> overrideChain) {
        var rawParameters = requestMappingData.executableElement().getParameters();
        var parameters = new ArrayList<Parameter>(rawParameters.size());
        for (int i = 0; i < rawParameters.size(); i++) {
            var parameter = rawParameters.get(i);
            var parameterType = requestMappingData.executableType().getParameterTypes().get(i);
            var query = findParameterAnnotation(overrideChain, i, HttpServerClassNames.query);
            if (query != null) {
                var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(query, "value");
                var queryParameterName = value == null || value.isBlank()
                    ? parameter.getSimpleName().toString()
                    : value;

                parameters.add(new Parameter(QUERY, queryParameterName, parameterType, parameter));
                continue;
            }
            var header = findParameterAnnotation(overrideChain, i, HttpServerClassNames.header);
            if (header != null) {
                var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(header, "value");
                var headerParameterName = value == null || value.isBlank()
                    ? parameter.getSimpleName().toString()
                    : value;

                parameters.add(new Parameter(HEADER, headerParameterName, parameterType, parameter));
                continue;
            }
            var cookie = findParameterAnnotation(overrideChain, i, HttpServerClassNames.cookie);
            if (cookie != null) {
                var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(cookie, "value");
                var cookieParameterName = value == null || value.isBlank()
                    ? parameter.getSimpleName().toString()
                    : value;

                parameters.add(new Parameter(COOKIE, cookieParameterName, parameterType, parameter));
                continue;
            }
            var path = findParameterAnnotation(overrideChain, i, HttpServerClassNames.path);
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

    /**
     * Parameter annotations may be declared on any method of the override chain, the most specific declaration wins
     */
    @Nullable
    private static AnnotationMirror findParameterAnnotation(List<ExecutableElement> overrideChain, int index, ClassName annotation) {
        for (var method : overrideChain) {
            var found = AnnotationUtils.findAnnotation(method.getParameters().get(index), annotation);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String methodName(RequestMappingData requestMappingData, Set<String> generatedNames) {
        final String suffix = requestMappingData.route().endsWith("/")
            ? "_trailing_slash"
            : "";

        var name = requestMappingData.httpMethod().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_") + Stream.of(requestMappingData.route().split("[^A-Za-z0-9]+"))
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.joining("_", "_", suffix));
        // distinct routes may map to the same name, e.g. /files and /files/*
        var uniqueName = name;
        for (int i = 1; !generatedNames.add(uniqueName); i++) {
            uniqueName = name + "_" + requestMappingData.executableElement().getSimpleName() + (i == 1 ? "" : String.valueOf(i));
        }
        return uniqueName;
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
