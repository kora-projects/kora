package ru.tinkoff.kora.http.client.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

import static ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames.*;

public class ClientClassGenerator {
    private final ProcessingEnvironment processingEnv;
    private final Elements elements;
    private final Types types;

    public ClientClassGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elements = this.processingEnv.getElementUtils();
        this.types = this.processingEnv.getTypeUtils();
    }

    public TypeSpec generate(TypeElement element) {
        var typeName = HttpClientUtils.clientName(element);
        var methods = this.parseMethods(element);
        var builder = CommonUtils.extendsKeepAop(element, typeName)
            .addOriginatingElement(element)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated).addMember("value", "$S", ClientClassGenerator.class.getCanonicalName()).build());
        builder.addMethod(this.buildConstructor(builder, element, methods));
        builder.addField(String.class, "rootUrl", Modifier.PRIVATE, Modifier.FINAL);

        for (var method : methods) {
            builder.addField(HttpClientClassNames.httpClient, method.element().getSimpleName() + "Client", Modifier.PRIVATE, Modifier.FINAL);
            builder.addField(Duration.class, method.element().getSimpleName() + "RequestTimeout", Modifier.PRIVATE, Modifier.FINAL);
            builder.addField(String.class, method.element().getSimpleName() + "UriTemplate", Modifier.PRIVATE, Modifier.FINAL);
            var hasUriParameters = method.parameters.stream().anyMatch(p -> p instanceof Parameter.QueryParameter || p instanceof Parameter.PathParameter);
            if (!hasUriParameters) {
                builder.addField(URI.class, method.element().getSimpleName() + "Uri", Modifier.PRIVATE, Modifier.FINAL);
            }
            var methodSpec = this.buildMethod(method);
            builder.addMethod(methodSpec);
        }
        return builder.build();
    }

    private MethodSpec buildMethod(MethodData methodData) {
        var method = methodData.element();
        var b = CommonUtils.overridingKeepAop(method)
            .addException(httpClientException);
        var methodClientName = method.getSimpleName() + "Client";
        var httpRoute = AnnotationUtils.findAnnotation(method, HttpClientClassNames.httpRoute);
        var httpMethod = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(httpRoute, "method");
        b.addStatement("var _client = this.$L", methodClientName);
        b.addStatement("var _headers = $T.of()", httpHeaders);
        b.addStatement("var _uriTemplate = this.$L", method.getSimpleName() + "UriTemplate");
        b.addStatement("var _requestTimeout = this.$L", method.getSimpleName() + "RequestTimeout");

        var hasPathParameters = methodData.parameters.stream().anyMatch(p -> p instanceof Parameter.PathParameter);
        var hasQueryParameters = methodData.parameters.stream().anyMatch(p -> p instanceof Parameter.QueryParameter);
        var bodyParameter = methodData.parameters.stream().filter(p -> p instanceof Parameter.BodyParameter).findFirst().map(Parameter.BodyParameter.class::cast).orElse(null);
        if (hasPathParameters || hasQueryParameters) {
            var httpPath = Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(httpRoute, "path"));
            final String uriWithPlaceholdersString;
            if (hasPathParameters) {
                b.addCode("var _uriNoQuery = this.rootUrl\n");
                var parts = this.parseRouteParts(httpPath, methodData.parameters);
                var uriWithPlaceholdersStringB = new StringBuilder();
                for (var routePart : parts) {
                    if (routePart.string() != null) {
                        uriWithPlaceholdersStringB.append(routePart.string);
                        b.addCode("  + $S\n", routePart.string());
                    } else {
                        uriWithPlaceholdersStringB.append("placeholder");
                        if (requiresConverter(routePart.parameter.parameter().asType())) {
                            var converterName = getConverterName(methodData, routePart.parameter.parameter());
                            b.addCode("  + $T.encode($L.convert($N), $T.UTF_8)\n", URLEncoder.class, converterName, routePart.parameter.parameter().getSimpleName(), StandardCharsets.class);
                        } else {
                            b.addCode("  + $T.encode($T.toString($N), $T.UTF_8)\n", URLEncoder.class, Objects.class, routePart.parameter.parameter().getSimpleName(), StandardCharsets.class);
                        }
                    }
                }
                uriWithPlaceholdersString = uriWithPlaceholdersStringB.toString();
                b.addCode(";\n");
            } else {
                b.addStatement("var _uriNoQuery = this.rootUrl + $S", httpPath);
                uriWithPlaceholdersString = httpPath;
            }
            if (!hasQueryParameters) {
                b.addStatement("var _uri = $T.create(_uriNoQuery)", URI.class);
            } else {
                var uriWithPlaceholders = URI.create(uriWithPlaceholdersString);
                var hasQMark = uriWithPlaceholders.getQuery() != null;
                var hasFirstParam = hasQMark && !uriWithPlaceholders.getQuery().isBlank();
                b.addStatement("var _query = new $T($L, $L)", uriQueryBuilder, !hasQMark, hasFirstParam);
                for (var parameter : methodData.parameters) {
                    if (parameter instanceof Parameter.QueryParameter p) {
                        boolean nullable = CommonUtils.isNullable(p.parameter());
                        if (nullable) {
                            b.beginControlFlow("if ($L != null)", p.parameter());
                        }
                        var targetLiteral = p.parameter().getSimpleName().toString();
                        var type = p.parameter().asType();
                        var isList = CommonUtils.isCollection(type);
                        if (isList) {
                            type = ((DeclaredType) type).getTypeArguments().get(0);
                            var paramName = "_" + targetLiteral + "_element";
                            b.beginControlFlow("if ($N.isEmpty())", targetLiteral);
                            b.addStatement("_query.unsafeAdd($S)", URLEncoder.encode(p.queryParameterName(), StandardCharsets.UTF_8));
                            b.nextControlFlow("else");
                            b.beginControlFlow("for (var $L : $L)", paramName, targetLiteral);
                            targetLiteral = paramName;
                        }

                        var isMap = CommonUtils.isMap(type);
                        if (isMap) {
                            var keyType = ((DeclaredType) type).getTypeArguments().get(0);
                            if (!String.class.getCanonicalName().equals(keyType.toString())) {
                                throw new ProcessingErrorException("@Query map key type must be String, but was: " + keyType, method);
                            }

                            type = ((DeclaredType) type).getTypeArguments().get(1);
                            var paramName = "_" + targetLiteral + "_element";
                            b.beginControlFlow("for (var $L : $L.entrySet())", paramName, targetLiteral);
                            targetLiteral = paramName;

                            b.beginControlFlow("if($L.getKey() != null && !$L.getKey().isBlank())", paramName, paramName);
                            b.beginControlFlow("if($L.getValue() == null)", paramName);
                            b.addStatement("_query.add($L.getKey())", paramName);
                            b.nextControlFlow("else");
                            b.addCode("_query.add($L.getKey(), ", paramName);
                            if (requiresConverter(type)) {
                                b.addCode("$L.convert($L.getValue())", getConverterName(methodData, p.parameter()), targetLiteral);
                            } else {
                                b.addCode("$T.toString($L.getValue())", Objects.class, targetLiteral);
                            }
                            b.addStatement(")", StandardCharsets.class);
                            b.endControlFlow().endControlFlow().endControlFlow();
                        } else {
                            b.addCode("_query.unsafeAdd($S, $T.encode(", URLEncoder.encode(p.queryParameterName(), StandardCharsets.UTF_8), URLEncoder.class);
                            if (requiresConverter(type)) {
                                b.addCode("$L.convert($L)", getConverterName(methodData, p.parameter()), targetLiteral);
                            } else {
                                b.addCode("$T.toString($L)", Objects.class, targetLiteral);
                            }
                            b.addCode(", $T.UTF_8));\n", StandardCharsets.class);
                        }

                        if (isList) {
                            b.endControlFlow().endControlFlow();
                        }

                        if (nullable) {
                            b.endControlFlow();
                        }
                    }
                }
                b.addStatement("var _uri = $T.create(_uriNoQuery + _query.build())", URI.class);
            }
        } else {
            b.addStatement("var _uri = this.$L", method.getSimpleName() + "Uri");
        }
        b.addCode("\n");
        for (var parameter : methodData.parameters()) {
            if (parameter instanceof Parameter.HeaderParameter header) {
                boolean nullable = CommonUtils.isNullable(header.parameter());
                if (nullable) {
                    b.beginControlFlow("if ($L != null)", header.parameter());
                }

                var targetLiteral = header.parameter().getSimpleName().toString();
                var type = header.parameter().asType();
                var isList = CommonUtils.isCollection(type);
                if (isList) {
                    type = ((DeclaredType) type).getTypeArguments().get(0);
                    var paramName = "_" + targetLiteral + "_element";
                    b.beginControlFlow("for (var $L : $L)", paramName, targetLiteral);
                    targetLiteral = paramName;
                }

                var isMap = CommonUtils.isMap(type);
                if (isMap) {
                    var keyType = ((DeclaredType) type).getTypeArguments().get(0);
                    if (!String.class.getCanonicalName().equals(keyType.toString())) {
                        throw new ProcessingErrorException("@Header map key type must be String, but was: " + keyType, method);
                    }

                    type = ((DeclaredType) type).getTypeArguments().get(1);
                    b.beginControlFlow("for (var $L_header : $L.entrySet())", targetLiteral, targetLiteral);
                    b.beginControlFlow("if($L_header.getKey() != null && !$L_header.getKey().isBlank() && $L_header.getValue() != null)", targetLiteral, targetLiteral, targetLiteral);
                    if (requiresConverter(type)) {
                        b.addStatement("_headers.add($L_header.getKey(), $L.convert($L_header.getValue()))", targetLiteral, getConverterName(methodData, header.parameter()), targetLiteral);
                    } else {
                        b.addStatement("_headers.add($L_header.getKey(), $L_header.getValue())", targetLiteral, targetLiteral);
                    }
                    b.endControlFlow().endControlFlow();
                } else if (ClassName.get(type).equals(httpHeaders)) {
                    b.beginControlFlow("for (var $L_header : $L)", targetLiteral, targetLiteral);
                    b.addStatement("_headers.add($L_header.getKey(), $L_header.getValue())", targetLiteral, targetLiteral);
                    b.endControlFlow();
                } else {
                    if (requiresConverter(type)) {
                        b.addCode("_headers.add($S, $L.convert($L));\n", header.headerName(), getConverterName(methodData, header.parameter()), targetLiteral);
                    } else {
                        b.addCode("_headers.add($S, $T.toString($L));\n", header.headerName(), Objects.class, targetLiteral);
                    }
                }

                if (isList) {
                    b.endControlFlow();
                }

                if (nullable) {
                    b.endControlFlow();
                }
            }
        }
        if (bodyParameter == null) {
            b.addStatement("var _body = $T.empty()", httpBody);
        } else {
            var requestMapperName = method.getSimpleName() + "RequestMapper";
            b.addStatement("final $T _body", httpBodyOutput);
            b.addCode("try {$>\n");
            b.addCode("_body = this.$N.apply($T.current(), $L);$<\n", requestMapperName, CommonClassNames.context, bodyParameter.parameter());
            b.addCode("} catch (Exception _e) {$>\n");
            b.addCode("throw new $T(_e);$<\n", httpClientEncoderException);
            b.addCode("}\n");
        }


        b.addStatement("var _request = $T.of($S, _uri, _uriTemplate, _headers, _body, _requestTimeout)", httpClientRequest, httpMethod);
        if (CommonUtils.isMono(method.getReturnType())) {
            b.addCode(buildCallMono(methodData));
        } else if (CommonUtils.isFuture(method.getReturnType())) {
            b.addCode(buildCallFuture(methodData));
        } else {
            b.addCode(buildCallBlocking(methodData));
        }
        return b.build();
    }

    private List<RoutePart> parseRouteParts(String httpPath, List<Parameter> parameters) {
        var parts = List.of(new RoutePart(null, httpPath));
        for (var parameter : parameters) {
            var newList = new ArrayList<RoutePart>();
            if (parameter instanceof Parameter.PathParameter p) {
                for (var part : parts) {
                    if (part.parameter != null) {
                        newList.add(part);
                    } else {
                        var from = 0;
                        var token = "{" + p.pathParameterName() + "}";
                        while (true) {
                            var idx = part.string.indexOf(token, from);
                            if (idx < 0) {
                                var str = part.string.substring(from);
                                if (!str.isEmpty()) {
                                    newList.add(new RoutePart(null, str));
                                }
                                break;
                            } else {
                                var str = part.string.substring(from, idx);
                                if (!str.isEmpty()) {
                                    newList.add(new RoutePart(null, str));
                                }
                                newList.add(new RoutePart(p, null));
                                from = idx + token.length();
                            }
                        }
                    }
                }
                parts = newList;
            }
        }
        return parts;
    }

    private record RoutePart(@Nullable Parameter.PathParameter parameter, @Nullable String string) {
        RoutePart {
            if (parameter != null && string != null) throw new IllegalStateException();
        }
    }


    private CodeBlock mapBlockingResponse(MethodData methodData, TypeMirror resultType) {
        var b = CodeBlock.builder();
        if (methodData.responseMapper != null && methodData.codeMappers().isEmpty()) {
            var responseMapperName = methodData.element.getSimpleName() + "ResponseMapper";
            if (resultType.getKind() != TypeKind.VOID) {
                b.add("return ");
            }
            b.addStatement("this.$N.apply(_response)", responseMapperName);
        } else if (methodData.codeMappers().isEmpty()) {
            b.addStatement("var _code = _response.code()");
            b.beginControlFlow("if (_code >= 200 && _code < 300)");
            if (resultType.getKind() == TypeKind.VOID) {
                b.addStatement("return");
            } else if (resultType instanceof DeclaredType dt && dt.asElement().toString().equals("java.lang.Void")) {
                b.addStatement("return null");
            } else {
                var responseMapperName = methodData.element().getSimpleName() + "ResponseMapper";
                b.addStatement("return this.$N.apply(_response)", responseMapperName);
            }
            b.nextControlFlow("else");
            b.addStatement("throw $T.fromResponseFuture(_response).get()", httpClientResponseException);
            b.endControlFlow();
        } else {
            b.addStatement("var _code = _response.code()");
            if (resultType.getKind() != TypeKind.VOID) {
                b.add("return ");
            }
            b.add("switch (_code) {\n");
            ResponseCodeMapperData defaultMapper = null;
            for (var codeMapper : methodData.codeMappers()) {
                if (codeMapper.code() == -1) {
                    defaultMapper = codeMapper;
                } else {
                    var responseMapperName = "" + methodData.element().getSimpleName() + codeMapper.code() + "ResponseMapper";
                    if (isMapperAssignable(methodData.element.getReturnType(), codeMapper.type, codeMapper.mapper)) {
                        b.add("  case $L -> this.$L.apply(_response);\n", codeMapper.code(), responseMapperName);
                    } else {
                        b.add("  case $L -> throw this.$L.apply(_response);\n", codeMapper.code(), responseMapperName);
                    }
                }
            }
            if (defaultMapper == null) {
                b.add("  default -> {\n");
                b.add("    throw $T.fromResponseFuture(_response).get();\n", httpClientResponseException);
                b.add("  }\n");
            } else {
                if (isMapperAssignable(methodData.element.getReturnType(), defaultMapper.type, defaultMapper.mapper)) {
                    b.add("  default -> this.$L.apply(_response);\n", methodData.element().getSimpleName() + "DefaultResponseMapper");
                } else {
                    b.add("  default -> throw this.$L.apply(_response);\n", methodData.element().getSimpleName() + "DefaultResponseMapper");
                }
            }
            b.add("};\n");
        }
        return b.build();
    }

    private CodeBlock buildCallBlocking(MethodData method) {
        var b = CodeBlock.builder();
        b.beginControlFlow("try (var _response = _client.execute(_request).toCompletableFuture().get())");
        b.add(mapBlockingResponse(method, method.element().getReturnType()));
        b.nextControlFlow("catch (java.util.concurrent.ExecutionException e)")
            .addStatement("if (e.getCause() instanceof RuntimeException re) throw re")
            .addStatement("if (e.getCause() instanceof Error er) throw er")
            .addStatement("throw new $T(e.getCause())", httpClientUnknownException);
        b.nextControlFlow("catch (RuntimeException e)")
            .addStatement("throw e");
        b.nextControlFlow("catch (Exception e)")
            .addStatement("throw new $T(e)", httpClientUnknownException);
        b.endControlFlow();// try response
        return b.build();
    }

    private CodeBlock mapFutureResponse(MethodData methodData, TypeMirror resultType) {
        var b = CodeBlock.builder();
        if (methodData.responseMapper != null) {
            var responseMapperName = methodData.element.getSimpleName() + "ResponseMapper";
            b.addStatement("_result = this.$N.apply(_response)", responseMapperName);
        } else if (methodData.codeMappers().isEmpty()) {
            b.addStatement("var _code = _response.code()");
            b.beginControlFlow("if (_code >= 200 && _code < 300)");
            if (resultType instanceof DeclaredType dt && dt.asElement().toString().equals("java.lang.Void")) {
                b.addStatement("_result = $T.completedFuture(null)", CompletableFuture.class);
            } else {
                var responseMapperName = methodData.element().getSimpleName() + "ResponseMapper";
                b.addStatement("_result = this.$N.apply(_response)", responseMapperName);
            }
            b.nextControlFlow("else");
            b.addStatement("return $T.fromResponse(_response)", httpClientResponseException);
            b.endControlFlow();
        } else {
            b.addStatement("var _code = _response.code()");
            b.add("_result = switch (_code) {\n");
            ResponseCodeMapperData defaultMapper = null;
            for (var codeMapper : methodData.codeMappers()) {
                if (codeMapper.code() == -1) {
                    defaultMapper = codeMapper;
                } else {
                    var responseMapperName = "" + methodData.element().getSimpleName() + codeMapper.code() + "ResponseMapper";
                    if (isMapperAssignable(resultType, codeMapper.type, codeMapper.mapper)) {
                        b.add("  case $L -> this.$L.apply(_response);\n", codeMapper.code(), responseMapperName);
                    } else {
                        b.add("  case $L -> this.$L.apply(_response).thenCompose($T::failedFuture);\n", codeMapper.code(), responseMapperName, CompletableFuture.class);
                    }
                }
            }
            if (defaultMapper == null) {
                b.add("  default -> $T.fromResponse(_response);\n", httpClientResponseException);
            } else {
                var responseMapperName = methodData.element().getSimpleName() + "DefaultResponseMapper";
                if (isMapperAssignable(resultType, defaultMapper.type, defaultMapper.mapper)) {
                    b.add("  default -> this.$L.apply(_response);\n", responseMapperName);
                } else {
                    b.add("  default -> this.$L.apply(_response).thenCompose($T::failedFuture);\n", responseMapperName, CompletableFuture.class);
                }
            }
            b.add("};\n");
        }
        return b.build();
    }

    private CodeBlock buildCallFuture(MethodData method) {
        var returnType = (DeclaredType) method.element().getReturnType();
        var returnTypeContent = returnType.getTypeArguments().get(0);
        var b = CodeBlock.builder();
        b.add("return _client.execute(_request)$>\n")
            .add(".thenCompose(_response -> {$>\n");
        b.addStatement("$T _result", ParameterizedTypeName.get(ClassName.get(CompletionStage.class), WildcardTypeName.subtypeOf(TypeName.get(returnTypeContent))));
        b.beginControlFlow("try");
        b.add(mapFutureResponse(method, returnTypeContent));
        b.nextControlFlow("catch (Throwable _e)");
        b.addStatement("_result = $T.failedFuture(_e)", CompletableFuture.class);
        b.endControlFlow();
        b.add("return _result.whenComplete((__r, _err) -> {$>\n");
        b.add("try {\n");
        b.add("  _response.close();\n");
        b.add("} catch (Exception _ex) {\n");
        b.add("   _err.addSuppressed(_ex);\n");
        b.add("}$<\n"); // try
        b.add("});$<\n");// whenComplete
        b.add("}).<$T>thenApply(_r -> _r)", TypeName.get(returnTypeContent));// thenCompose response
        if (method.element.getReturnType() instanceof DeclaredType dt && dt.asElement().toString().equals(CompletableFuture.class.getCanonicalName())) {
            b.add(".toCompletableFuture()");
        }
        b.add(";$<\n");
        return b.build();
    }

    private CodeBlock buildCallMono(MethodData method) {
        var returnType = (DeclaredType) method.element().getReturnType();
        var returnTypeContent = returnType.getTypeArguments().get(0);
        var b = CodeBlock.builder();
        b.add("return $T.fromFuture(() -> _client.execute(_request)$>\n", CommonClassNames.mono)
            .add(".thenCompose(_response -> {$>\n");
        b.addStatement("$T _result", ParameterizedTypeName.get(ClassName.get(CompletionStage.class), WildcardTypeName.subtypeOf(TypeName.get(returnTypeContent))));
        b.beginControlFlow("try");
        b.add(mapFutureResponse(method, returnTypeContent));
        b.nextControlFlow("catch (Throwable _e)");
        b.addStatement("_result = $T.failedFuture(_e)", CompletableFuture.class);
        b.endControlFlow();
        b.add("return _result.whenComplete((__r, _err) -> {$>\n");
        b.add("try {\n");
        b.add("  _response.close();\n");
        b.add("} catch (Exception _ex) {\n");
        b.add("   _err.addSuppressed(_ex);\n");
        b.add("}$<\n"); // try
        b.add("});$<\n");// whenComplete
        b.add("}).<$T>thenApply(_r -> _r).toCompletableFuture()", TypeName.get(returnTypeContent));// thenCompose response
        b.add(");$<\n");
        return b.build();
    }

    private MethodSpec buildConstructor(TypeSpec.Builder tb, TypeElement element, List<MethodData> methods) {
        var parameterConverters = parseParameterConverters(methods);

        var packageName = this.processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
        var configClassName = HttpClientUtils.configName(element);
        var annotation = Objects.requireNonNull(AnnotationUtils.findAnnotation(element, httpClientAnnotation));
        var telemetryTag = AnnotationUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(annotation, "telemetryTag");
        var httpClientTag = AnnotationUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(annotation, "httpClientTag");
        var clientParameter = ParameterSpec.builder(httpClient, "httpClient");
        if (httpClientTag != null && !httpClientTag.isEmpty()) {
            clientParameter.addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value", TagUtils.writeTagAnnotationValue(httpClientTag)).build());
        }
        var telemetryParameter = ParameterSpec.builder(httpClientTelemetryFactory, "telemetryFactory");
        if (telemetryTag != null && !telemetryTag.isEmpty()) {
            telemetryParameter.addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value", TagUtils.writeTagAnnotationValue(telemetryTag)).build());
        }
        record Interceptor(TypeName type, @Nullable AnnotationSpec tag) {}
        var interceptorParser = (Function<AnnotationMirror, Interceptor>) a -> {
            var interceptorType = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(a, "value");
            var interceptorTypeName = ClassName.get(Objects.requireNonNull(interceptorType));
            @Nullable
            var interceptorTag = AnnotationUtils.<AnnotationMirror>parseAnnotationValueWithoutDefault(a, "tag");
            var interceptorTagAnnotationSpec = interceptorTag == null ? null : AnnotationSpec.get(interceptorTag);
            return new Interceptor(interceptorTypeName, interceptorTagAnnotationSpec);
        };

        var classInterceptors = AnnotationUtils.findAnnotations(element, interceptWithClassName, interceptWithContainerClassName)
            .stream()
            .map(interceptorParser)
            .toList();
        var interceptorsCounter = 0;
        var addedInterceptorsMap = new HashMap<Interceptor, String>();
        var builder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(clientParameter.build())
            .addParameter(ClassName.get(packageName, configClassName), "config")
            .addParameter(telemetryParameter.build());
        for (var entry : parameterConverters.entrySet()) {
            var readerName = entry.getKey();
            var parameterizedTypeName = entry.getValue();
            tb.addField(parameterizedTypeName, readerName, Modifier.PRIVATE, Modifier.FINAL);
            builder.addParameter(parameterizedTypeName, readerName);
            builder.addStatement("this.$1L = $1L", readerName);
        }
        builder.addStatement("this.rootUrl = $T.requireNonNull(config.url())", Objects.class);

        for (var classInterceptor : classInterceptors) {
            if (addedInterceptorsMap.containsKey(classInterceptor)) {
                continue;
            }
            var name = "$interceptor" + (interceptorsCounter + 1);
            var p = ParameterSpec.builder(classInterceptor.type, name);
            if (classInterceptor.tag != null) {
                p.addAnnotation(classInterceptor.tag);
            }
            var parameter = p.build();
            builder.addParameter(parameter);
            addedInterceptorsMap.put(classInterceptor, name);
            interceptorsCounter++;
        }
        classInterceptors = new ArrayList<>(classInterceptors);
        Collections.reverse(classInterceptors);

        for (var methodData : methods) {
            var method = methodData.element();
            var methodInterceptors = AnnotationUtils.findAnnotations(methodData.element, interceptWithClassName, interceptWithContainerClassName)
                .stream()
                .map(interceptorParser)
                .filter(Predicate.not(classInterceptors::contains))
                .distinct()
                .toList();
            for (var parameter : methodData.parameters()) {
                if (parameter instanceof Parameter.BodyParameter bodyParameter) {
                    var requestMapperType = bodyParameter.mapper() != null && bodyParameter.mapper().mapperClass() != null
                        ? TypeName.get(bodyParameter.mapper().mapperClass())
                        : ParameterizedTypeName.get(httpClientRequestMapper, TypeName.get(bodyParameter.parameter().asType()));
                    var paramName = method.getSimpleName() + "RequestMapper";
                    tb.addField(requestMapperType, paramName, Modifier.PRIVATE, Modifier.FINAL);
                    var tags = bodyParameter.mapper() != null
                        ? bodyParameter.mapper().toTagAnnotation()
                        : null;
                    var constructorParameter = ParameterSpec.builder(requestMapperType, paramName);
                    if (tags != null) {
                        constructorParameter.addAnnotation(tags);
                    }
                    builder.addParameter(constructorParameter.build());
                    builder.addStatement("this.$L = $L", paramName, paramName);
                }
            }
            if (methodData.codeMappers().isEmpty()) {
                var responseMapperName = method.getSimpleName() + "ResponseMapper";
                if (methodData.responseMapper() != null && methodData.responseMapper().mapperClass() != null && CommonUtils.hasDefaultConstructorAndFinal(this.types, methodData.responseMapper().mapperClass())) {
                    var responseMapperTypeElement = (TypeElement) ((DeclaredType) methodData.responseMapper.mapperClass()).asElement();
                    var mapperClassName = ClassName.get(responseMapperTypeElement);
                    var b = !responseMapperTypeElement.getTypeParameters().isEmpty()
                        ? FieldSpec.builder(ParameterizedTypeName.get(mapperClassName, TypeName.get(method.getReturnType())), responseMapperName)
                        .initializer(CodeBlock.of("new $T<>()", mapperClassName))
                        : FieldSpec.builder(mapperClassName, responseMapperName)
                        .initializer(CodeBlock.of("new $T()", mapperClassName));
                    var responseMapperField = b.addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build();
                    tb.addField(responseMapperField);
                } else {
                    var isVoid = method.getReturnType().getKind() == TypeKind.VOID;
                    var isFutureOfVoid = (CommonUtils.isFuture(method.getReturnType()) || CommonUtils.isMono(method.getReturnType()))
                        && method.getReturnType() instanceof DeclaredType dt
                        && dt.getTypeArguments().get(0).toString().equals("java.lang.Void");
                    if (!isVoid && !isFutureOfVoid) {
                        final TypeName responseMapperType;
                        if (methodData.responseMapper() != null && methodData.responseMapper().mapperClass() != null) {
                            responseMapperType = TypeName.get(methodData.responseMapper().mapperClass());
                        } else if (CommonUtils.isMono(methodData.element.getReturnType()) || CommonUtils.isFuture(methodData.element.getReturnType())) {
                            responseMapperType = ParameterizedTypeName.get(
                                HttpClientClassNames.httpClientResponseMapper,
                                ParameterizedTypeName.get(
                                    ClassName.get(CompletionStage.class),
                                    ((ParameterizedTypeName) methodData.returnType()).typeArguments.get(0)
                                )
                            );
                        } else {
                            responseMapperType = ParameterizedTypeName.get(
                                HttpClientClassNames.httpClientResponseMapper,
                                methodData.returnType()
                            );
                        }

                        var responseMapperParameter = ParameterSpec.builder(responseMapperType, responseMapperName);
                        var responseMapperTags = methodData.responseMapper() != null
                            ? methodData.responseMapper().toTagAnnotation()
                            : null;
                        if (responseMapperTags != null) {
                            responseMapperParameter.addAnnotation(responseMapperTags);
                        }
                        tb.addField(responseMapperType, responseMapperName, Modifier.PRIVATE, Modifier.FINAL);
                        builder.addParameter(responseMapperParameter.build());
                        builder.addStatement("this.$L = $L", responseMapperName, responseMapperName);
                    }
                }
            } else {
                for (var codeMapper : methodData.codeMappers()) {
                    var responseMapperName = "" + method.getSimpleName() + (codeMapper.code() > 0 ? codeMapper.code() : "Default") + "ResponseMapper";
                    if (codeMapper.mapper() != null && CommonUtils.hasDefaultConstructorAndFinal(this.types, codeMapper.mapper())) {
                        var mapperTypeElement = (TypeElement) codeMapper.mapper().asElement();
                        var mapperTypeName = ClassName.get(mapperTypeElement);
                        var b = mapperTypeElement.getTypeParameters().isEmpty()
                            ? FieldSpec.builder(mapperTypeName, responseMapperName)
                            .initializer(CodeBlock.of("new $T()", mapperTypeName))
                            : FieldSpec.builder(ParameterizedTypeName.get(mapperTypeName, TypeName.get(method.getReturnType())), responseMapperName)
                            .initializer(CodeBlock.of("new $T<>()", mapperTypeName));
                        var responseMapperField = b.addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build();
                        tb.addField(responseMapperField);
                    } else {
                        var returnType = method.getReturnType();
                        var responseMapperType = CommonUtils.isMono(returnType) || CommonUtils.isFuture(returnType)
                            ? codeMapper.futureResponseMapperType(((DeclaredType) returnType).getTypeArguments().get(0))
                            : codeMapper.responseMapperType(returnType);
                        var responseMapperParameter = ParameterSpec.builder(responseMapperType, responseMapperName);
                        var responseMapperTags = methodData.responseMapper() != null
                            ? methodData.responseMapper().toTagAnnotation()
                            : null;
                        if (responseMapperTags != null) {
                            responseMapperParameter.addAnnotation(responseMapperTags);
                        }
                        tb.addField(responseMapperType, responseMapperName, Modifier.PRIVATE, Modifier.FINAL);
                        builder.addParameter(responseMapperParameter.build());
                        builder.addStatement("this.$L = $L", responseMapperName, responseMapperName);
                    }
                }
            }
            var name = method.getSimpleName();
            var httpRoute = AnnotationUtils.findAnnotation(method, HttpClientClassNames.httpRoute);
            var httpPath = AnnotationUtils.parseAnnotationValueWithoutDefault(httpRoute, "path");
            builder.addCode("var $L = config.apply(httpClient, $T.class, $S, config.$L(), telemetryFactory, $S);\n", name, element, name, name, httpPath);
            builder.addCode("this.$LUriTemplate = $L.url();\n", name, name);
            var hasUriParameters = methodData.parameters().stream().anyMatch(p -> p instanceof Parameter.QueryParameter || p instanceof Parameter.PathParameter);
            if (!hasUriParameters) {
                builder.addCode("this.$LUri = $T.create($L.url());\n", name, URI.class, name);
            }
            builder.addCode("this.$LClient = $L.client()", name, name);
            if (!methodInterceptors.isEmpty() || !classInterceptors.isEmpty()) {
                builder.addCode("\n");
                for (var methodInterceptor : methodInterceptors) {
                    if (addedInterceptorsMap.containsKey(methodInterceptor)) {
                        continue;
                    }
                    var interceptorName = "$interceptor" + (interceptorsCounter + 1);
                    var p = ParameterSpec.builder(methodInterceptor.type, interceptorName);
                    if (methodInterceptor.tag != null) {
                        p.addAnnotation(methodInterceptor.tag);
                    }
                    var parameter = p.build();
                    builder.addParameter(parameter);
                    addedInterceptorsMap.put(methodInterceptor, interceptorName);
                    interceptorsCounter++;
                }
                methodInterceptors = new ArrayList<>(methodInterceptors);
                Collections.reverse(methodInterceptors);
                for (var methodInterceptor : methodInterceptors) {
                    var interceptorName = addedInterceptorsMap.get(methodInterceptor);
                    builder.addCode("  .with($L)\n", interceptorName);
                }
                for (var classInterceptor : classInterceptors) {
                    var interceptorName = addedInterceptorsMap.get(classInterceptor);
                    builder.addCode("  .with($L)\n", interceptorName);
                }
            }
            builder.addCode(";\n");
            builder.addCode("this.$LRequestTimeout = $L.requestTimeout();\n", name, name);
        }

        return builder.build();
    }

    private boolean isMapperAssignable(TypeMirror resultType, @Nullable TypeMirror mappingType, @Nullable DeclaredType mappingMapper) {
        if (mappingType == null && mappingMapper == null) {
            return true;
        }

        if (mappingType != null) {
            return types.isAssignable(mappingType, resultType);
        }
        var responseMapperType = TypeUtils.findSupertype(mappingMapper, httpClientResponseMapper);
        var typeArg = responseMapperType.getTypeArguments().get(0);
        if (CommonUtils.isFuture(typeArg)) {
            typeArg = ((DeclaredType) typeArg).getTypeArguments().get(0);
        }
        return typeArg.getKind() == TypeKind.TYPEVAR || types.isAssignable(typeArg, resultType);
    }

    record ResponseCodeMapperData(int code, @Nullable TypeMirror type, @Nullable DeclaredType mapper) {
        public TypeName responseMapperType(TypeMirror returnType) {
            if (this.mapper() != null) {
                var mapperElement = (TypeElement) this.mapper().asElement();
                if (mapperElement.getTypeParameters().isEmpty()) {
                    return TypeName.get(this.mapper());
                } else {
                    if (this.type() != null) {
                        var publisherParam = TypeName.get(this.type());
                        return ParameterizedTypeName.get(ClassName.get(mapperElement), publisherParam);
                    } else {
                        var publisherParam = TypeName.get(returnType);
                        return ParameterizedTypeName.get(ClassName.get(mapperElement), publisherParam);
                    }
                }
            }
            if (this.type() != null) {
                var publisherParam = TypeName.get(this.type());
                return ParameterizedTypeName.get(httpClientResponseMapper, publisherParam.box());
            } else {
                var publisherParam = TypeName.get(returnType);
                return ParameterizedTypeName.get(httpClientResponseMapper, publisherParam.box());
            }
        }

        public TypeName futureResponseMapperType(TypeMirror returnType) {
            if (this.mapper() != null) {
                var mapperElement = (TypeElement) this.mapper().asElement();
                if (mapperElement.getTypeParameters().isEmpty()) {
                    return TypeName.get(this.mapper());
                } else {
                    if (this.type() != null) {
                        var publisherParam = TypeName.get(this.type());
                        return ParameterizedTypeName.get(ClassName.get(mapperElement), publisherParam);
                    } else {
                        var publisherParam = TypeName.get(returnType);
                        return ParameterizedTypeName.get(ClassName.get(mapperElement), publisherParam);
                    }
                }
            }
            if (this.type() != null) {
                var publisherParam = TypeName.get(this.type());
                return ParameterizedTypeName.get(httpClientResponseMapper, ParameterizedTypeName.get(ClassName.get(CompletionStage.class), publisherParam));
            } else {
                var publisherParam = TypeName.get(returnType);
                return ParameterizedTypeName.get(httpClientResponseMapper, ParameterizedTypeName.get(ClassName.get(CompletionStage.class), publisherParam));
            }
        }
    }

    record MethodData(
        ExecutableElement element,
        TypeName returnType,
        @Nullable CommonUtils.MappingData responseMapper,
        List<ResponseCodeMapperData> codeMappers,
        List<Parameter> parameters) {
    }

    private List<MethodData> parseMethods(TypeElement element) {
        var result = new ArrayList<MethodData>();
        for (var enclosedElement : element.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD) {
                continue;
            }
            var method = (ExecutableElement) enclosedElement;
            if (method.getModifiers().contains(Modifier.DEFAULT) || method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            var parameters = new ArrayList<Parameter>();
            for (int i = 0; i < method.getParameters().size(); i++) {
                var parameter = Parameter.parse(method, i);
                parameters.add(parameter);
            }
            var returnType = TypeName.get(method.getReturnType());
            var responseCodeMappers = this.parseMapperData(method);

            var responseMapper = CommonUtils.parseMapping(method).getMapping(httpClientResponseMapper);
            result.add(new MethodData(method, returnType, responseMapper, responseCodeMappers, parameters));
        }
        return result;
    }

    private List<ResponseCodeMapperData> parseMapperData(ExecutableElement element) {
        var annotations = AnnotationUtils.findAnnotations(element, responseCodeMapper, responseCodeMappers);
        if (annotations.isEmpty()) {
            return List.of();
        }
        return annotations.stream()
            .map(a -> this.parseMapperData(element, a))
            .toList();
    }

    private ResponseCodeMapperData parseMapperData(ExecutableElement method, AnnotationMirror annotation) {
        var code = Objects.requireNonNull(AnnotationUtils.<Integer>parseAnnotationValueWithoutDefault(annotation, "code"));
        var type = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(annotation, "type");
        var mapper = AnnotationUtils.<DeclaredType>parseAnnotationValueWithoutDefault(annotation, "mapper");
        if (mapper == null && type == null) {
            var returnType = method.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) {
                returnType = elements.getTypeElement("java.lang.Void").asType();
            }
            return new ResponseCodeMapperData(code, null, null);
        }
        return new ResponseCodeMapperData(code, type, mapper);
    }

    private Map<String, ParameterizedTypeName> parseParameterConverters(List<MethodData> methods) {
        var result = new HashMap<String, ParameterizedTypeName>();
        for (var method : methods) {
            for (var parameter : method.parameters) {
                if (parameter instanceof Parameter.PathParameter pathParameter) {
                    var type = pathParameter.parameter().asType();
                    if (requiresConverter(type)) {
                        result.put(
                            getConverterName(method, pathParameter.parameter()),
                            getConverterTypeName(type)
                        );
                    }
                }
                if (parameter instanceof Parameter.QueryParameter queryParameter) {
                    var type = queryParameter.parameter().asType();
                    if (CommonUtils.isCollection(type)) {
                        type = ((DeclaredType) type).getTypeArguments().get(0);
                    } else if (CommonUtils.isMap(type)) {
                        type = ((DeclaredType) type).getTypeArguments().get(1);
                    }

                    if (requiresConverter(type)) {
                        result.put(
                            getConverterName(method, queryParameter.parameter()),
                            getConverterTypeName(type)
                        );
                    }
                }
                if (parameter instanceof Parameter.HeaderParameter headerParameter) {
                    var type = headerParameter.parameter().asType();
                    if (CommonUtils.isCollection(type)) {
                        type = ((DeclaredType) type).getTypeArguments().get(0);
                    } else if (CommonUtils.isMap(type)) {
                        type = ((DeclaredType) type).getTypeArguments().get(1);
                    }

                    if (requiresConverter(type) && !ClassName.get(headerParameter.parameter().asType()).equals(httpHeaders)) {
                        result.put(
                            getConverterName(method, headerParameter.parameter()),
                            getConverterTypeName(type)
                        );
                    }
                }
            }
        }
        return result;
    }

    private final Set<String> primitiveTypes = Set.of("java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Boolean");

    private boolean requiresConverter(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return false;
        }
        if (type instanceof DeclaredType dt) {
            return !primitiveTypes.contains(dt.asElement().toString());
        }
        return false;
    }

    private String getConverterName(MethodData method, VariableElement parameter) {
        return method.element.getSimpleName().toString() + CommonUtils.capitalize(parameter.getSimpleName().toString()) + "Converter";
    }

    private ParameterizedTypeName getConverterTypeName(TypeMirror type) {
        return ParameterizedTypeName.get(stringParameterConverter, TypeName.get(type));
    }
}
