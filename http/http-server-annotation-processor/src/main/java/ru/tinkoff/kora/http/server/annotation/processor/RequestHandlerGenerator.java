package ru.tinkoff.kora.http.server.annotation.processor;

import com.squareup.javapoet.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.Path;
import ru.tinkoff.kora.http.common.annotation.Query;
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.*;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.tinkoff.kora.http.server.annotation.processor.HttpServerUtils.interceptWithClassName;
import static ru.tinkoff.kora.http.server.annotation.processor.HttpServerUtils.interceptWithContainerClassName;
import static ru.tinkoff.kora.http.server.annotation.processor.RequestHandlerGenerator.ParameterType.*;

public class RequestHandlerGenerator {

    private final Elements elements;
    private final Types types;
    private final ProcessingEnvironment processingEnvironment;
    private final TypeElement httpServerResponseMapperElement;
    private final TypeElement httpServerResponseEntityElement;
    private final DeclaredType httpServerResponseEntityWildcard;
    private final TypeElement httpServerResponseEntityMapperElement;
    private final TypeElement stringParameterReaderElement;

    public RequestHandlerGenerator(Elements elements, Types types, ProcessingEnvironment processingEnvironment) {
        this.elements = elements;
        this.types = types;
        this.processingEnvironment = processingEnvironment;
        this.httpServerResponseMapperElement = this.elements.getTypeElement(HttpServerResponseMapper.class.getCanonicalName());
        this.httpServerResponseEntityElement = this.elements.getTypeElement(HttpServerResponseEntity.class.getCanonicalName());
        this.httpServerResponseEntityMapperElement = this.elements.getTypeElement(HttpServerResponseEntityMapper.class.getCanonicalName());
        this.httpServerResponseEntityWildcard = this.types.getDeclaredType(this.httpServerResponseEntityElement, this.types.getWildcardType(null, null));
        this.stringParameterReaderElement = this.elements.getTypeElement(StringParameterReader.class.getCanonicalName());

    }

    @Nullable
    public MethodSpec generate(TypeElement controller, RequestMappingData requestMappingData) {
        var methodName = this.methodName(requestMappingData);

        var methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(TypeName.get(HttpServerRequestHandler.class))
            .addParameter(TypeName.get(controller.asType()), "_controller");

        var parameters = parseParameters(requestMappingData);
        if (parameters == null) {
            return null;
        }

        this.addParameterMappers(methodBuilder, requestMappingData, parameters);
        if (!isMonoVoidReturnType(requestMappingData) && !isVoidReturnType(requestMappingData.executableType())) {
            var responseMapper = this.detectResponseMapper(requestMappingData, requestMappingData.executableElement());
            if (responseMapper == null) {
                return null;
            }
            methodBuilder.addParameter(responseMapper);
        }

        var isBlocking = isBlocking(requestMappingData);
        if (isBlocking) {
            methodBuilder.addParameter(TypeName.get(BlockingRequestExecutor.class), "_executor");
        }

        var handlerCode = this.buildRequestHandler(controller, requestMappingData, parameters, methodBuilder);

        methodBuilder.addCode("return $T.$L($S, _request -> {$>\n$L\n$<});",
            HttpServerRequestHandlerImpl.class,
            requestMappingData.httpMethod().toLowerCase(),
            requestMappingData.route(),
            handlerCode
        );

        return methodBuilder.build();
    }

    private CodeBlock buildRequestHandler(TypeElement controller, RequestMappingData requestMappingData, List<Parameter> parameters, MethodSpec.Builder methodBuilder) {
        var handler = CodeBlock.builder();
        var isBlocking = this.isBlocking(requestMappingData);
        var returnType = requestMappingData.executableType().getReturnType();
        var returnVoid = isVoidReturnType(returnType);
        var isNullable = this.isNullable(requestMappingData);
        var isMonoVoid = this.isMonoVoidReturnType(requestMappingData);

        for (var parameter : parameters) {
            var codeBlock = switch (parameter.parameterType) {
                case PATH -> this.definePathParameter(parameter, methodBuilder);
                case QUERY -> this.defineQueryParameter(parameter, methodBuilder);
                case HEADER -> this.defineHeaderParameter(parameter, methodBuilder);
                case MAPPED_HTTP_REQUEST -> CodeBlock.of("");
            };
            handler.add(codeBlock);
            handler.add("\n");

        }
        var interceptors = Stream.concat(
                CommonUtils.findRepeatableAnnotationsOnElement(controller, interceptWithClassName, interceptWithContainerClassName).stream().map(HttpServerUtils::parseInterceptor),
                CommonUtils.findRepeatableAnnotationsOnElement(requestMappingData.executableElement(), interceptWithClassName, interceptWithContainerClassName).stream().map(HttpServerUtils::parseInterceptor)
            )
            .distinct()
            .toList();
        handler.add("\n");
        handler.add("return ");
        var executeParameters = parameters.stream()
            .map(_p -> switch (_p.parameterType) {
                case MAPPED_HTTP_REQUEST, PATH, QUERY, HEADER -> _p.variableElement.getSimpleName();
            })
            .collect(Collectors.joining(", "));
        var mappedParameters = parameters.stream().filter(p -> p.parameterType == MAPPED_HTTP_REQUEST).toList();
        final CodeBlock controllerCall;
        if (isBlocking) {
            if (returnVoid) {
                controllerCall = CodeBlock.of("""
                    _executor.execute(() -> {
                      _controller.$L($L);
                      return new $T(200, "application/octet-stream", $T.of(), $T.allocate(0));
                    })
                    """, requestMappingData.executableElement().getSimpleName(), executeParameters, SimpleHttpServerResponse.class, HttpHeaders.class, ByteBuffer.class);
            } else if (isNullable) {
                controllerCall = CodeBlock.of("_executor.execute(() -> $T.ofNullable(_controller.$L($L)))\n", Optional.class, requestMappingData.executableElement().getSimpleName(), executeParameters);
            } else {
                controllerCall = CodeBlock.of("_executor.execute(() -> _controller.$L($L))\n", requestMappingData.executableElement().getSimpleName(), executeParameters);
            }
        } else if (isMonoVoid) {
            controllerCall = CodeBlock.builder()
                .add("$T.deferContextual(_ctx -> _controller.$L($L))\n", Mono.class, requestMappingData.executableElement().getSimpleName(), executeParameters)
                .add("  .thenReturn(new $T(200, \"application/octet-stream\", $T.of(), $T.allocate(0)))", SimpleHttpServerResponse.class, HttpHeaders.class, ByteBuffer.class)
                .build();
        } else {
            controllerCall = CodeBlock.of("$T.deferContextual(_ctx -> _controller.$L($L))", Mono.class, requestMappingData.executableElement().getSimpleName(), executeParameters);
        }

        var requestMappingBlock = CodeBlock.builder();
        var requestName = "_request";
        for (int i = 0; i < interceptors.size(); i++) {
            var interceptor = interceptors.get(i);
            var interceptorName = "$interceptor" + (i + 1);
            var newRequestName = "$request" + (i + 1);
            requestMappingBlock.add("$L.intercept($L, $L -> $>\n", interceptorName, requestName, newRequestName);
            requestName = newRequestName;
            var builder = ParameterSpec.builder(interceptor.type(), interceptorName);
            if (interceptor.tag() != null) {
                builder.addAnnotation(interceptor.tag());
            }
            methodBuilder.addParameter(builder.build());
        }
        for (var mappedParameter : mappedParameters) {
            requestMappingBlock.add("""
                $LHttpRequestMapper.apply($L)$>
                .flatMap($L ->$>
                """, mappedParameter.name, requestName, mappedParameter.name);
        }
        requestMappingBlock.add(controllerCall);
        for (var mappedParameter : mappedParameters) {
            requestMappingBlock.add("$<\n)$<\n");
        }


        handler.add(requestMappingBlock.build());
        if (isNullable) {
            handler.add(CodeBlock.of(".flatMap(_response -> _responseMapper.apply(_response.orElse(null)))"));
        } else if (!isMonoVoid) {
            handler.add(CodeBlock.of(".flatMap(_response -> _responseMapper.apply(_response))"));
        }
        for (var i : interceptors) {
            handler.add(")$<\n");
        }
        handler.add(";\n");
        return handler.build();
    }

    private boolean isMonoVoidReturnType(RequestMappingData requestMappingData) {
        var returnType = requestMappingData.executableType().getReturnType();

        var publisherType = this.elements.getTypeElement(Publisher.class.getCanonicalName());
        var publisherTypeErasure = this.types.erasure(publisherType.asType());

        var isPublisher = this.types.isAssignable(returnType, publisherTypeErasure);
        if (!isPublisher) {
            return false;
        }
        var t = publisherType.getTypeParameters().get(0);
        var typeMirror = this.types.asMemberOf((DeclaredType) returnType, t);
        return typeMirror.toString().equals("java.lang.Void");
    }

    private CodeBlock definePathParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        var code = CodeBlock.builder();
        var typeString = parameter.type.toString();
        switch (typeString) {
            case "java.lang.Integer", "int" -> code.add("var $L = $T.parseIntegerPathParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            case "java.lang.Long", "long" -> code.add("var $L = $T.parseLongPathParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            case "java.lang.Double", "double" -> code.add("var $L = $T.parseDoublePathParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            case "java.lang.String" -> code.add("var $L = $T.parseStringPathParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            case "java.util.UUID" -> code.add("var $L = $T.parseUUIDPathParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            default -> {
                var parameterReaderType = this.types.getDeclaredType(
                    this.stringParameterReaderElement,
                    parameter.type
                );
                var parameterReaderName = "_" + parameter.variableElement.getSimpleName().toString() + "Reader";
                methodBuilder.addParameter(TypeName.get(parameterReaderType), parameterReaderName);
                code.add("var $L = $L.read($T.parseStringPathParameter(_request, $S));", parameter.variableElement, parameterReaderName, RequestHandlerUtils.class, parameter.name);
                return code.build();
            }
        }
        return code.build();
    }

    private CodeBlock defineHeaderParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        var code = CodeBlock.builder();
        var typeString = parameter.type.toString();
        switch (typeString) {
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalStringHeaderParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseStringHeaderParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }
            case "java.util.Optional<java.lang.String>" ->
                code.add("var $L = $T.ofNullable($T.parseOptionalStringHeaderParameter(_request, $S));", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name);
            case "java.util.List<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalStringListHeaderParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseStringListHeaderParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }

            case "java.util.Optional<java.lang.Integer>" ->
                code.add("var $L = $T.ofNullable($T.parseOptionalIntegerHeaderParameter(_request, $S));", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name);
            case "java.lang.Integer" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalIntegerHeaderParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseIntegerHeaderParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }
            case "java.util.List<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalIntegerListHeaderParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseIntegerListHeaderParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }

            default -> {
                var listElement = elements.getTypeElement(List.class.getCanonicalName());
                var listErasure = types.erasure(listElement.asType());
                var optionalElement = elements.getTypeElement(Optional.class.getCanonicalName());
                var optionalErasure = types.erasure(optionalElement.asType());

                if (this.types.isAssignable(parameter.type, optionalErasure)) {
                    var optionalParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = this.types.getDeclaredType(this.stringParameterReaderElement, optionalParameter);
                    var parameterReaderName = "_" + parameter.variableElement.getSimpleName().toString() + "Reader";

                    methodBuilder.addParameter(TypeName.get(parameterReaderType), parameterReaderName);
                    code.add("var $L = $T.ofNullable($T.parseOptionalStringHeaderParameter(_request, $S)).map($L::read);", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name, parameterReaderName);
                    return code.build();
                }

                if (this.types.isAssignable(parameter.type, listErasure)) {
                    var listParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = this.types.getDeclaredType(this.stringParameterReaderElement, listParameter);
                    methodBuilder.addParameter(TypeName.get(parameterReaderType), "_" + parameter.name + "Reader");

                    if (isNullable(parameter)) {
                        code.add("""
                                var _optional_$L = $T.parseOptionalStringListHeaderParameter(_request, $S);
                                var $L = (_optional_$L == null) ? null : _optional_$L.stream().map($L::read).toList();
                                """, parameter.variableElement, RequestHandlerUtils.class, parameter.name,
                            parameter.variableElement, parameter.variableElement, parameter.variableElement, "_" + parameter.name + "Reader");
                    } else {
                        code.add("var $L = $T.parseStringListHeaderParameter(_request, $S).stream().map($L::read).toList();", parameter.variableElement, RequestHandlerUtils.class, parameter.name, "_" + parameter.name + "Reader");
                    }

                    return code.build();
                }

                var parameterReaderType = this.types.getDeclaredType(this.stringParameterReaderElement, parameter.type);
                var parameterReaderName = "_" + parameter.variableElement.getSimpleName() + "Reader";
                methodBuilder.addParameter(TypeName.get(parameterReaderType), parameterReaderName);

                if (isNullable(parameter)) {
                    var transitParameterName = "_" + parameter.variableElement.getSimpleName() + "RawValue";
                    code.add("var $L = $T.parseOptionalStringHeaderParameter(_request, $S);\n", transitParameterName, RequestHandlerUtils.class, parameter.name);
                    code.add("var $L = $L == null ? null : $L.read($L);", parameter.variableElement, transitParameterName, parameterReaderName, transitParameterName);
                } else {
                    code.add("var $L = $L.read($T.parseStringHeaderParameter(_request, $S));", parameter.variableElement, parameterReaderName, RequestHandlerUtils.class, parameter.name);
                }
                return code.build();
            }
        }
        return code.build();
    }

    private CodeBlock defineQueryParameter(Parameter parameter, MethodSpec.Builder methodBuilder) {
        var code = CodeBlock.builder();
        var typeString = parameter.type.toString();
        switch (typeString) {
            case "java.util.UUID" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalUuidQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseUuidQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }
            case "java.util.Optional<java.util.UUID>" ->
                code.add("var $L = $T.ofNullable($T.parseOptionalUuidQueryParameter(_request, $S));", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name);
            case "java.util.List<java.util.UUID>" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalUuidListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseUuidListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }

            case "int" -> code.add("var $L = $T.parseIntegerQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            case "java.util.Optional<java.lang.Integer>" ->
                code.add("var $L = $T.ofNullable($T.parseOptionalIntegerQueryParameter(_request, $S));", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name);
            case "java.lang.Integer" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalIntegerQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseIntegerQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }
            case "java.util.List<java.lang.Integer>" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalIntegerListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseIntegerListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }

            case "long" -> code.add("var $L = $T.parseLongQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            case "java.util.Optional<java.lang.Long>" ->
                code.add("var $L = $T.ofNullable($T.parseOptionalLongQueryParameter(_request, $S));", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name);
            case "java.lang.Long" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalLongQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseLongQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }
            case "java.util.List<java.lang.Long>" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalLongListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseLongListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }

            case "double" -> code.add("var $L = $T.parseDoubleQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            case "java.util.Optional<java.lang.Double>" ->
                code.add("var $L = $T.ofNullable($T.parseOptionalDoubleQueryParameter(_request, $S));", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name);
            case "java.lang.Double" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalDoubleQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseDoubleQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }
            case "java.util.List<java.lang.Double>" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalDoubleListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseDoubleListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }

            case "java.util.Optional<java.lang.String>" ->
                code.add("var $L = $T.ofNullable($T.parseOptionalStringQueryParameter(_request, $S));", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name);
            case "java.lang.String" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalStringQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseStringQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }
            case "java.util.List<java.lang.String>" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalStringListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseStringListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }

            case "boolean" -> code.add("var $L = $T.parseBooleanQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
            case "java.util.Optional<java.lang.Boolean>" ->
                code.add("var $L = $T.ofNullable($T.parseOptionalBooleanQueryParameter(_request, $S));", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name);
            case "java.lang.Boolean" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalBooleanQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseBooleanQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }
            case "java.util.List<java.lang.Boolean>" -> {
                if (isNullable(parameter)) {
                    code.add("var $L = $T.parseOptionalBooleanListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                } else {
                    code.add("var $L = $T.parseBooleanListQueryParameter(_request, $S);", parameter.variableElement, RequestHandlerUtils.class, parameter.name);
                }
            }

            default -> {
                var listElement = elements.getTypeElement(List.class.getCanonicalName());
                var listErasure = types.erasure(listElement.asType());
                var optionalElement = elements.getTypeElement(Optional.class.getCanonicalName());
                var optionalErasure = types.erasure(optionalElement.asType());
                final String readerParameterName = "_" + parameter.name + "Reader";

                if (this.types.isAssignable(parameter.type, optionalErasure)) {
                    var optionalParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = this.types.getDeclaredType(this.stringParameterReaderElement, optionalParameter);

                    methodBuilder.addParameter(TypeName.get(parameterReaderType), readerParameterName);
                    code.add("var $L = $T.ofNullable($T.parseOptionalStringQueryParameter(_request, $S)).map($L::read);", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name, readerParameterName);
                    return code.build();
                }
                if (this.types.isAssignable(parameter.type, listErasure)) {
                    var listParameter = ((DeclaredType) parameter.type).getTypeArguments().get(0);
                    var parameterReaderType = this.types.getDeclaredType(this.stringParameterReaderElement, listParameter);

                    if (isNullable(parameter)) {
                        methodBuilder.addParameter(TypeName.get(parameterReaderType), readerParameterName);
                        code.add("var $L = $T.ofNullable($T.parseOptionalStringListQueryParameter(_request, $S)).map(_var_$L -> _var_$L.stream().map($L::read).toList()).orElse(null);",
                            parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name, parameter.variableElement, parameter.variableElement, readerParameterName);
                    } else {
                        methodBuilder.addParameter(TypeName.get(parameterReaderType), readerParameterName);
                        code.add("var $L = $T.parseStringListQueryParameter(_request, $S).stream().map($L::read).toList();", parameter.variableElement, RequestHandlerUtils.class, parameter.name, readerParameterName);
                    }

                    return code.build();

                }

                var parameterReaderType = this.types.getDeclaredType(this.stringParameterReaderElement, parameter.type);
                methodBuilder.addParameter(TypeName.get(parameterReaderType), readerParameterName);

                if (isNullable(parameter)) {
                    code.add("var $L = $T.ofNullable($T.parseOptionalStringQueryParameter(_request, $S)).map($L::read).orElse(null);", parameter.variableElement, Optional.class, RequestHandlerUtils.class, parameter.name, readerParameterName);
                } else {
                    code.add("var $L = $L.read($T.parseStringQueryParameter(_request, $S));", parameter.variableElement, readerParameterName, RequestHandlerUtils.class, parameter.name);
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
            var query = parameter.getAnnotation(Query.class);
            if (query != null) {
                var queryParameterName = query.value().isBlank()
                    ? parameter.getSimpleName().toString()
                    : query.value();

                parameters.add(new Parameter(QUERY, queryParameterName, parameterType, parameter));
                continue;
            }
            var header = parameter.getAnnotation(Header.class);
            if (header != null) {
                var queryParameterName = header.value().isBlank()
                    ? parameter.getSimpleName().toString()
                    : header.value();

                parameters.add(new Parameter(HEADER, queryParameterName, parameterType, parameter));
                continue;
            }
            var path = parameter.getAnnotation(Path.class);
            if (path != null) {
                var pathParameterName = path.value().isBlank()
                    ? parameter.getSimpleName().toString()
                    : path.value();
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
            .collect(Collectors.joining("_", "_",  suffix));
    }

    private boolean isNullable(RequestMappingData requestMappingData) {
        return CommonUtils.isNullable(requestMappingData.executableElement());
    }

    private boolean isBlocking(RequestMappingData requestMappingData) {
        var returnType = requestMappingData.executableType().getReturnType();

        var publisherType = this.elements.getTypeElement(Publisher.class.getCanonicalName()).asType();
        var publisherTypeErasure = this.types.erasure(publisherType);

        return !this.types.isAssignable(returnType, publisherTypeErasure);
    }


    private void addParameterMappers(MethodSpec.Builder methodBuilder, RequestMappingData requestMappingData, List<Parameter> bodyParameterType) {
        for (var parameter : bodyParameterType) {
            if (parameter.parameterType != MAPPED_HTTP_REQUEST) {
                continue;
            }
            var mapper = requestMappingData.httpRequestMappingData().get(parameter.variableElement);
            var mapperName = parameter.name + "HttpRequestMapper";
            TypeName mapperType;
            var tags = mapper != null
                ? mapper.toTagAnnotation()
                : null;

            if (mapper != null && mapper.mapperClass() != null) {
                mapperType = TypeName.get(mapper.mapperClass());
            } else {

                var typeMirror = parameter.type;
                var genericParam = (typeMirror instanceof PrimitiveType primitiveType)
                    ? this.types.boxedClass(primitiveType).asType()
                    : typeMirror;
                mapperType = ParameterizedTypeName.get(ClassName.get(HttpServerRequestMapper.class), TypeName.get(genericParam));
            }
            var b = ParameterSpec.builder(mapperType, mapperName);
            if (tags != null) {
                b.addAnnotation(tags);
            }
            methodBuilder.addParameter(b.build());
        }
    }

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


        var publisherType = this.elements.getTypeElement(Publisher.class.getCanonicalName()).asType();
        var publisherTypeErasure = this.types.erasure(publisherType);
        final TypeMirror typeName;

        if (isVoidReturnType(returnType)) {
            var voidType = this.elements.getTypeElement(HttpServerResponse.class.getCanonicalName());
            typeName = this.types.getDeclaredType(this.httpServerResponseMapperElement, voidType.asType());
        } else if (this.types.isAssignable(returnType, publisherTypeErasure)) {
            var resultType = ((DeclaredType) returnType).getTypeArguments().get(0);
            if (this.types.isAssignable(resultType, this.httpServerResponseEntityWildcard)) {
                var argument = this.httpServerResponseEntityElement.getTypeParameters().get(0);
                var argumentType = this.types.asMemberOf((DeclaredType) resultType, argument);
                typeName = this.types.getDeclaredType(this.httpServerResponseEntityMapperElement, argumentType);
            } else {
                typeName = this.types.getDeclaredType(this.httpServerResponseMapperElement, resultType);
            }
        } else if (returnType instanceof PrimitiveType) {
            typeName = this.types.getDeclaredType(this.httpServerResponseMapperElement, this.types.boxedClass((PrimitiveType) returnType).asType());
        } else if (this.types.isAssignable(returnType, this.httpServerResponseEntityWildcard)) {
            var argument = this.httpServerResponseEntityElement.getTypeParameters().get(0);
            var argumentType = this.types.asMemberOf((DeclaredType) returnType, argument);
            typeName = this.types.getDeclaredType(this.httpServerResponseEntityMapperElement, argumentType);
        } else {
            typeName = this.types.getDeclaredType(this.httpServerResponseMapperElement, returnType);
        }

        var b = ParameterSpec.builder(TypeName.get(typeName), "_responseMapper");
        if (tags != null) {
            b.addAnnotation(tags);
        }
        return b.build();
    }


    private boolean isVoidReturnType(TypeMirror returnType) {
        return returnType instanceof NoType || returnType.toString().equals("java.lang.Void") || returnType.toString().equals("kotlin.Unit");
    }

    private record Parameter(ParameterType parameterType, String name, TypeMirror type, VariableElement variableElement) {}

    enum ParameterType {
        MAPPED_HTTP_REQUEST,
        HEADER,
        QUERY,
        PATH,
    }
}
