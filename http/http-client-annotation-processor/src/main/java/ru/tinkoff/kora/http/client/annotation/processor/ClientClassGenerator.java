package ru.tinkoff.kora.http.client.annotation.processor;

import com.squareup.javapoet.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ComparableTypeMirror;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.HttpClientResponseException;
import ru.tinkoff.kora.http.client.common.annotation.ResponseCodeMapper;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;

import javax.annotation.Nullable;
import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class ClientClassGenerator {
    private static final ClassName interceptWithClassName = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith");
    private static final ClassName interceptWithContainerClassName = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith", "InterceptWithContainer");
    private final ProcessingEnvironment processingEnv;
    private final Elements elements;
    private final Types types;
    private final TypeElement requestMapperType;
    private final ReturnType.ReturnTypeParser returnTypeParser;
    private final Parameter.ParameterParser parameterParser;
    private final TypeMirror responseMapperType;
    private final TypeMirror httpResponseType;

    private final TypeMirror listTypeErasure;
    private final TypeMirror stringType;

    public ClientClassGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elements = this.processingEnv.getElementUtils();
        this.types = this.processingEnv.getTypeUtils();
        this.returnTypeParser = new ReturnType.ReturnTypeParser(processingEnv, this.elements, this.types);
        this.requestMapperType = this.elements.getTypeElement(HttpClientRequestMapper.class.getCanonicalName());
        var httpClientResponseMapperElement = this.elements.getTypeElement(HttpClientResponseMapper.class.getCanonicalName());
        this.responseMapperType = httpClientResponseMapperElement != null
            ? this.types.erasure(httpClientResponseMapperElement.asType())
            : null;

        this.parameterParser = new Parameter.ParameterParser(this.elements, this.types);
        this.httpResponseType = new ComparableTypeMirror(this.types, this.types.erasure(this.elements.getTypeElement(HttpClientResponse.class.getCanonicalName()).asType()));

        this.listTypeErasure = this.types.erasure(this.elements.getTypeElement(List.class.getCanonicalName()).asType());
        this.stringType = this.elements.getTypeElement(String.class.getCanonicalName()).asType();
    }

    public TypeSpec generate(TypeElement element) {
        var typeName = HttpClientUtils.clientName(element);
        var methods = this.parseMethods(element);
        var builder = CommonUtils.extendsKeepAop(element, typeName)
            .addOriginatingElement(element)
            .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", ClientClassGenerator.class.getCanonicalName()).build());
        builder.addMethod(this.buildConstructor(builder, element, methods));

        for (var method : methods) {
            builder.addField(HttpClient.class, method.element().getSimpleName() + "Client", Modifier.PRIVATE, Modifier.FINAL);
            builder.addField(int.class, method.element().getSimpleName() + "RequestTimeout", Modifier.PRIVATE, Modifier.FINAL);
            builder.addField(String.class, method.element().getSimpleName() + "Url", Modifier.PRIVATE, Modifier.FINAL);
            var methodSpec = this.buildMethod(method);
            builder.addMethod(methodSpec);
        }
        return builder.build();
    }

    private MethodSpec buildMethod(MethodData methodData) {
        var method = methodData.element();
        var b = CommonUtils.overridingKeepAop(method)
            .addException(HttpClientException.class);
        var methodClientName = method.getSimpleName() + "Client";
        var methodRequestTimeout = method.getSimpleName() + "RequestTimeout";
        var httpRoute = method.getAnnotation(HttpRoute.class);
        b.addCode("""
            var _client = this.$L;
            var _requestBuilder = new $T($S, this.$LUrl)
              .requestTimeout(this.$L);
            """, methodClientName, HttpClientRequestBuilder.class, httpRoute.method(), method.getSimpleName(), methodRequestTimeout);
        for (var parameter : methodData.parameters()) {
            if (parameter instanceof Parameter.PathParameter path) {
                if (requiresConverter(path.parameter().asType())) {
                    b.addCode("_requestBuilder.templateParam($S, $L.convert($L));\n", path.pathParameterName(), getConverterName(methodData, path.parameter()), path.parameter());
                } else {
                    b.addCode("_requestBuilder.templateParam($S, $T.toString($L));\n", path.pathParameterName(), Objects.class, path.parameter());
                }
            }
            if (parameter instanceof Parameter.HeaderParameter header) {
                boolean nullable = CommonUtils.isNullable(header.parameter());
                if (nullable) {
                    b.beginControlFlow("if ($L != null)", header.parameter());
                }

                if (requiresConverter(header.parameter().asType())) {
                    b.addCode("_requestBuilder.header($S, $L.convert($L));\n", header.headerName(), getConverterName(methodData, header.parameter()), header.parameter());
                } else {
                    b.addCode("_requestBuilder.header($S, $T.toString($L));\n", header.headerName(), Objects.class, header.parameter());
                }

                if (nullable) {
                    b.endControlFlow();
                }

            }
            if (parameter instanceof Parameter.QueryParameter query) {
                boolean nullable = CommonUtils.isNullable(query.parameter());
                if (nullable) {
                    b.beginControlFlow("if ($L != null)", query.parameter());
                }
                String targetLiteral = query.parameter().getSimpleName().toString();
                TypeMirror type = query.parameter().asType();
                boolean isList = types.isSameType(types.erasure(type), this.listTypeErasure);
                if (isList) {
                    type = ((DeclaredType) type).getTypeArguments().get(0);
                    var paramName = "_" + targetLiteral + "_element";
                    b.beginControlFlow("for (var $L : $L)", paramName, targetLiteral);
                    targetLiteral = paramName;
                }

                if (requiresConverter(type)) {
                    b.addCode("_requestBuilder.queryParam($S, $L.convert($L));\n", query.queryParameterName(), getConverterName(methodData, query.parameter()), targetLiteral);
                } else {
                    b.addCode("_requestBuilder.queryParam($S, $T.toString($L));\n", query.queryParameterName(), Objects.class, targetLiteral);
                }

                if (isList) {
                    b.endControlFlow();
                }
                if (nullable) {
                    b.endControlFlow();
                }
            }
        }
        b.addCode(";\n");
        for (var parameter : methodData.parameters()) {
            if (parameter instanceof Parameter.BodyParameter body) {
                var requestMapperName = method.getSimpleName() + "RequestMapper";
                b.addCode("_requestBuilder = this.$L.apply(new $T<>(_requestBuilder, $L));\n", requestMapperName, HttpClientRequestMapper.Request.class, body.parameter());
            }
        }

        b.addStatement("var _request = _requestBuilder.build()");
        var publisherType = methodData.returnType() instanceof ReturnType.FluxReturnType
            ? Flux.class
            : Mono.class;
        var responseProcess = CodeBlock.builder();
        if (methodData.responseMapper != null || httpResponseType.equals(methodData.returnType().publisherParameter())) {
            var responseMapperName = method.getSimpleName() + "ResponseMapper";
            responseProcess.add("""
                      return this.$L.apply(_response);
                """, responseMapperName);
        } else if (methodData.codeMappers().isEmpty()) {
            var responseMapperName = method.getSimpleName() + "ResponseMapper";
            responseProcess.add("""
                      var _code = _response.code();
                      if (_code >= 200 && _code < 300) {
                          return this.$L.apply(_response);
                      } else {
                          return $T.fromResponse(_response);
                      }
                """, responseMapperName, HttpClientResponseException.class);
        } else {
            responseProcess.add("      var _code = _response.code();\n");
            responseProcess.add("      return switch (_code) {\n");
            ResponseCodeMapperData defaultMapper = null;
            for (var codeMapper : methodData.codeMappers()) {
                if (codeMapper.code() == ResponseCodeMapper.DEFAULT) {
                    defaultMapper = codeMapper;
                } else {
                    var responseMapperName = "" + method.getSimpleName() + codeMapper.code() + "ResponseMapper";
                    responseProcess.add("        case $L -> this.$L.apply(_response);\n", codeMapper.code(), responseMapperName);
                }
            }
            if (defaultMapper == null) {
                responseProcess.add("        default -> $T.fromResponse(_response);\n", HttpClientResponseException.class);
            } else {
                responseProcess.add("        default -> this.$L.apply(_response);\n", method.getSimpleName() + "DefaultResponseMapper");
            }
            responseProcess.add("      };\n");
        }
        b.addCode("""
            var _result = $T.usingWhen(
                _client.execute(_request),
                _response -> {
            $L
                },
                $T::close
            );
            """, publisherType, responseProcess.build(), HttpClientResponse.class);


        if (methodData.returnType() instanceof ReturnType.FluxReturnType || methodData.returnType() instanceof ReturnType.MonoReturnType) {
            b.addCode("return _result;\n");
        } else if (methodData.returnType() instanceof ReturnType.SimpleReturnType simple) {
            b.addCode("return _result.block();\n");
        } else {
            b.addCode("_result.block();\n");
        }
        return b.build();
    }

    private MethodSpec buildConstructor(TypeSpec.Builder tb, TypeElement element, List<MethodData> methods) {
        var parameterConverters = parseParameterConverters(methods);

        var packageName = this.processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
        var configClassName = HttpClientUtils.configName(element);
        var telemetryTag = CommonUtils.parseAnnotationClassValue(element, ru.tinkoff.kora.http.client.common.annotation.HttpClient.class.getCanonicalName(), "telemetryTag");
        var httpClientTag = CommonUtils.parseAnnotationClassValue(element, ru.tinkoff.kora.http.client.common.annotation.HttpClient.class.getCanonicalName(), "httpClientTag");
        var clientParameter = ParameterSpec.builder(TypeName.get(HttpClient.class), "httpClient");
        if (httpClientTag.length > 0) {
            clientParameter.addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", CommonUtils.writeTagAnnotationValue(httpClientTag)).build());
        }
        var telemetryParameter = ParameterSpec.builder(TypeName.get(HttpClientTelemetryFactory.class), "telemetryFactory");
        if (telemetryTag.length > 0) {
            telemetryParameter.addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", CommonUtils.writeTagAnnotationValue(telemetryTag)).build());
        }
        record Interceptor(TypeName type, @Nullable AnnotationSpec tag) {}
        var interceptorParser = (Function<AnnotationMirror, Interceptor>) a -> {
            var interceptorType = ((TypeMirror) CommonUtils.parseAnnotationValueWithoutDefault(a, "value"));
            var interceptorTypeName = ClassName.get(Objects.requireNonNull(interceptorType));
            @Nullable
            var interceptorTag = (AnnotationMirror) CommonUtils.parseAnnotationValueWithoutDefault(a, "tag");
            var interceptorTagAnnotationSpec = interceptorTag == null ? null : AnnotationSpec.get(interceptorTag);
            return new Interceptor(interceptorTypeName, interceptorTagAnnotationSpec);
        };
        var classInterceptors = CommonUtils.findRepeatableAnnotationsOnElement(element, interceptWithClassName, interceptWithContainerClassName)
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
        parameterConverters.forEach((readerName, parameterizedTypeName) -> {
            tb.addField(parameterizedTypeName, readerName);
            builder.addParameter(parameterizedTypeName, readerName);
            builder.addStatement("this.$1L = $1L", readerName);
        });
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
            var methodInterceptors = CommonUtils.findRepeatableAnnotationsOnElement(methodData.element, interceptWithClassName, interceptWithContainerClassName)
                .stream()
                .map(interceptorParser)
                .filter(Predicate.not(classInterceptors::contains))
                .distinct()
                .toList();
            for (var parameter : methodData.parameters()) {
                if (parameter instanceof Parameter.BodyParameter bodyParameter) {
                    var requestMapperType = bodyParameter.mapper() != null && bodyParameter.mapper().mapperClass() != null
                        ? bodyParameter.mapper().mapperClass()
                        : this.types.getDeclaredType(this.requestMapperType, bodyParameter.parameter().asType());
                    var paramName = method.getSimpleName() + "RequestMapper";
                    tb.addField(TypeName.get(requestMapperType), paramName, Modifier.PRIVATE, Modifier.FINAL);
                    var tags = bodyParameter.mapper() != null
                        ? bodyParameter.mapper().toTagAnnotation()
                        : null;
                    var constructorParameter = ParameterSpec.builder(TypeName.get(requestMapperType), paramName);
                    if (tags != null) {
                        constructorParameter.addAnnotation(tags);
                    }
                    builder.addParameter(constructorParameter.build());
                    builder.addStatement("this.$L = $L", paramName, paramName);
                }
            }
            if (methodData.codeMappers().isEmpty()) {
                var responseMapperName = method.getSimpleName() + "ResponseMapper";
                var responseMapperType = methodData.responseMapper() != null && methodData.responseMapper().mapperClass() != null
                    ? TypeName.get(methodData.responseMapper().mapperClass())
                    : methodData.returnType().responseMapperType();

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
            } else {
                for (var codeMapper : methodData.codeMappers()) {
                    var responseMapperName = "" + method.getSimpleName() + (codeMapper.code() > 0 ? codeMapper.code() : "Default") + "ResponseMapper";
                    var responseMapperType = codeMapper.responseMapperType(methodData.returnType().publisherType());
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
            var name = method.getSimpleName();
            builder.addCode("var $L = config.apply(httpClient, $T.class, $S, config.$LConfig(), telemetryFactory, $S);\n", name, element, name, name, method.getAnnotation(HttpRoute.class).path());
            builder.addCode("this.$LUrl = $L.url();\n", name, name);
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

    record ResponseCodeMapperData(int code, @Nullable TypeMirror type, @Nullable TypeMirror mapper) {
        public TypeName responseMapperType(ClassName publisherType) {
            if (this.mapper() != null) {
                return TypeName.get(this.mapper());
            }
            var publisherParam = TypeName.get(this.type());
            return ParameterizedTypeName.get(
                ClassName.get(HttpClientResponseMapper.class),
                publisherParam,
                ParameterizedTypeName.get(publisherType, publisherParam)
            );
        }
    }

    record MethodData(
        ExecutableElement element,
        ReturnType returnType,
        @Nullable CommonUtils.MappingData responseMapper,
        List<ResponseCodeMapperData> codeMappers,
        List<Parameter> parameters) {}

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
                var parameter = this.parameterParser.parseParameter(method, i);
                parameters.add(parameter);
            }
            var returnType = this.returnTypeParser.parseReturnType(method);
            var responseCodeMappers = this.parseMapperData(method);

            var responseMapper = CommonUtils.parseMapping(method).getMapping(this.types, this.responseMapperType);
            result.add(new MethodData(method, returnType, responseMapper, responseCodeMappers, parameters));
        }
        return result;
    }

    private List<ResponseCodeMapperData> parseMapperData(ExecutableElement element) {
        var mappersOptional = element.getAnnotationMirrors().stream()
            .filter(a -> a.getAnnotationType().toString().equals(ResponseCodeMapper.ResponseCodeMappers.class.getCanonicalName()))
            .findFirst();
        final List<AnnotationMirror> annotations;
        if (mappersOptional.isEmpty()) {
            annotations = element.getAnnotationMirrors().stream()
                .map(AnnotationMirror.class::cast)
                .filter(a -> a.getAnnotationType().toString().equals(ResponseCodeMapper.class.getCanonicalName()))
                .toList();
        } else {
            @SuppressWarnings("unchecked")
            var mappersArray = (List<AnnotationValue>) mappersOptional.get().getElementValues().values().iterator().next().getValue();
            annotations = mappersArray.stream()
                .map(AnnotationValue::getValue)
                .map(AnnotationMirror.class::cast)
                .toList();
        }
        if (annotations.isEmpty()) {
            return List.of();
        }
        return annotations.stream()
            .map(a -> this.parseMapperData(element, a))
            .filter(Objects::nonNull)
            .toList();
    }

    @Nullable
    private ResponseCodeMapperData parseMapperData(ExecutableElement method, AnnotationMirror annotation) {
        var code = annotation.getElementValues().entrySet()
            .stream()
            .filter(a -> a.getKey().getSimpleName().toString().equals("code"))
            .map(a -> a.getValue().getValue())
            .map(Integer.class::cast)
            .findFirst()
            .get();

        var type = annotation.getElementValues().entrySet()
            .stream()
            .filter(a -> a.getKey().getSimpleName().toString().equals("type"))
            .map(a -> a.getValue().getValue())
            .map(TypeMirror.class::cast)
            .findFirst()
            .orElse(null);

        var mapper = annotation.getElementValues().entrySet()
            .stream()
            .filter(a -> a.getKey().getSimpleName().toString().equals("mapper"))
            .map(a -> a.getValue().getValue())
            .map(TypeMirror.class::cast)
            .findFirst()
            .orElse(null);
        if (mapper == null && type == null) {
            var returnType = method.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) {
                returnType = elements.getTypeElement("java.lang.Void").asType();
            }
            return new ResponseCodeMapperData(code, returnType, mapper);
        }

        return new ResponseCodeMapperData(code, type, mapper);
    }

    private Map<String, ParameterizedTypeName> parseParameterConverters(List<MethodData> methods) {
        var result = new HashMap<String, ParameterizedTypeName>();
        for (MethodData method : methods) {
            for (Parameter parameter : method.parameters) {
                if (parameter instanceof Parameter.PathParameter pathParameter) {
                    TypeMirror type = pathParameter.parameter().asType();
                    if (requiresConverter(type)) {
                        result.put(
                            getConverterName(method, pathParameter.parameter()),
                            getConverterTypeName(type)
                        );
                    }
                }
                if (parameter instanceof Parameter.QueryParameter queryParameter) {
                    TypeMirror type = queryParameter.parameter().asType();
                    if (types.isSameType(types.erasure(type), this.listTypeErasure)) {
                        type = ((DeclaredType) type).getTypeArguments().get(0);
                    }

                    if (requiresConverter(type)) {
                        result.put(
                            getConverterName(method, queryParameter.parameter()),
                            getConverterTypeName(type)
                        );
                    }
                }
                if (parameter instanceof Parameter.HeaderParameter headerParameter) {
                    TypeMirror type = headerParameter.parameter().asType();
                    if (requiresConverter(type)) {
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

    private boolean requiresConverter(TypeMirror type) {
        return !type.getKind().isPrimitive() && !types.isSameType(stringType, type);
    }

    private String getConverterName(MethodData method, VariableElement parameter) {
        return method.element.getSimpleName().toString() + CommonUtils.capitalize(parameter.getSimpleName().toString()) + "Converter";
    }

    private ParameterizedTypeName getConverterTypeName(TypeMirror type) {
        return ParameterizedTypeName.get(ClassName.get(StringParameterConverter.class), TypeName.get(type));
    }
}
