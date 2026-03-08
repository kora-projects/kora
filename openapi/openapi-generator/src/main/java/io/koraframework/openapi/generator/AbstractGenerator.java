package io.koraframework.openapi.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.openapitools.codegen.*;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@NullMarked
public abstract class AbstractGenerator<C, R> {
    private static final Logger log = LoggerFactory.getLogger(AbstractGenerator.class);

    public static class Classes {
        public static final ClassName generated = ClassName.get("io.koraframework.common.annotation", "Generated");
        public static final ClassName tag = ClassName.get("io.koraframework.common", "Tag");
        public static final ClassName component = ClassName.get("io.koraframework.common", "Component");
        public static final ClassName defaultComponent = ClassName.get("io.koraframework.common", "DefaultComponent");
        public static final ClassName module = ClassName.get("io.koraframework.common", "Module");
        public static final ClassName mapping = ClassName.get("io.koraframework.common", "Mapping");
        public static final ClassName httpRoute = ClassName.get("io.koraframework.http.common.annotation", "HttpRoute");
        public static final ClassName httpClient = ClassName.get("io.koraframework.http.client.common.annotation", "HttpClient");
        public static final ClassName httpClientResponse = ClassName.get("io.koraframework.http.client.common.response", "HttpClientResponse");
        public static final ClassName httpClientRequest = ClassName.get("io.koraframework.http.client.common.request", "HttpClientRequest");
        public static final ClassName httpClientResponseMapper = ClassName.get("io.koraframework.http.client.common.response", "HttpClientResponseMapper");
        public static final ClassName httpClientRequestMapper = ClassName.get("io.koraframework.http.client.common.request", "HttpClientRequestMapper");
        public static final ClassName stringParameterConverter = ClassName.get("io.koraframework.http.client.common.writer", "StringParameterConverter");
        public static final ClassName enumStringParameterConverter = ClassName.get("io.koraframework.http.client.common.writer", "EnumStringParameterConverter");
        public static final ClassName apiKeyHttpClientInterceptor = ClassName.get("io.koraframework.http.client.common.interceptor", "ApiKeyHttpClientInterceptor");
        public static final ClassName basicAuthHttpClientTokenProvider = ClassName.get("io.koraframework.http.client.common.auth", "BasicAuthHttpClientTokenProvider");
        public static final ClassName basicAuthHttpClientInterceptor = ClassName.get("io.koraframework.http.client.common.interceptor", "BasicAuthHttpClientInterceptor");
        public static final ClassName httpClientTokenProvider = ClassName.get("io.koraframework.http.client.common.auth", "HttpClientTokenProvider");
        public static final ClassName bearerAuthHttpClientInterceptor = ClassName.get("io.koraframework.http.client.common.interceptor", "BearerAuthHttpClientInterceptor");

        public static final ClassName httpController = ClassName.get("io.koraframework.http.server.common.annotation", "HttpController");
        public static final ClassName responseCodeMapper = ClassName.get("io.koraframework.http.client.common.annotation", "ResponseCodeMapper");
        public static final ClassName httpClientInterceptor = ClassName.get("io.koraframework.http.client.common.interceptor", "HttpClientInterceptor");
        public static final ClassName httpClientInterceptChain = httpClientInterceptor.nestedClass("InterceptChain");
        public static final ClassName httpServerInterceptor = ClassName.get("io.koraframework.http.server.common", "HttpServerInterceptor");
        public static final ClassName httpServerInterceptChain = httpServerInterceptor.nestedClass("InterceptChain");
        public static final ClassName httpServerRequest = ClassName.get("io.koraframework.http.server.common", "HttpServerRequest");
        public static final ClassName httpServerRequestMapper = ClassName.get("io.koraframework.http.server.common.handler", "HttpServerRequestMapper");
        public static final ClassName httpServerResponse = ClassName.get("io.koraframework.http.server.common", "HttpServerResponse");
        public static final ClassName httpResponseEntity = ClassName.get("io.koraframework.http.common", "HttpResponseEntity");
        public static final ClassName httpServerResponseMapper = ClassName.get("io.koraframework.http.server.common.handler", "HttpServerResponseMapper");
        public static final ClassName httpServerResponseException = ClassName.get("io.koraframework.http.server.common", "HttpServerResponseException");
        public static final ClassName stringParameterReader = ClassName.get("io.koraframework.http.server.common.handler", "StringParameterReader");
        public static final ClassName enumStringParameterReader = ClassName.get("io.koraframework.http.server.common.handler", "EnumStringParameterReader");
        public static final ClassName httpServerPrincipalExtractor = ClassName.get("io.koraframework.http.server.common.auth", "HttpServerPrincipalExtractor");
        public static final ClassName principalWithScopes = ClassName.get("io.koraframework.http.common.auth", "PrincipalWithScopes");
        public static final ClassName principal = ClassName.get("io.koraframework.common", "Principal");

        public static final ClassName httpHeaders = ClassName.get("io.koraframework.http.common.header", "HttpHeaders");
        public static final ClassName interceptWith = ClassName.get("io.koraframework.http.common.annotation", "InterceptWith");
        public static final ClassName query = ClassName.get("io.koraframework.http.common.annotation", "Query");
        public static final ClassName path = ClassName.get("io.koraframework.http.common.annotation", "Path");
        public static final ClassName header = ClassName.get("io.koraframework.http.common.annotation", "Header");
        public static final ClassName cookie = ClassName.get("io.koraframework.http.common.annotation", "Cookie");
        public static final ClassName nullable = ClassName.get("org.jspecify.annotations", "Nullable");
        public static final ClassName multipartWriter = ClassName.get("io.koraframework.http.client.common.form", "MultipartWriter");
        public static final ClassName formMultipart = ClassName.get("io.koraframework.http.common.form", "FormMultipart");
        public static final ClassName formPart = formMultipart.nestedClass("FormPart");
        public static final ClassName httpBodyOutput = ClassName.get("io.koraframework.http.common.body", "HttpBodyOutput");
        public static final ClassName httpBody = ClassName.get("io.koraframework.http.common.body", "HttpBody");

        public static final ClassName validationHttpServerInterceptor = ClassName.get("io.koraframework.validation.module.http.server", "ValidationHttpServerInterceptor");
        public static final ClassName valid = ClassName.get("io.koraframework.validation.common.annotation", "Valid");
        public static final ClassName validate = ClassName.get("io.koraframework.validation.common.annotation", "Validate");
        public static final ClassName range = ClassName.get("io.koraframework.validation.common.annotation", "Range");
        public static final ClassName size = ClassName.get("io.koraframework.validation.common.annotation", "Size");
        public static final ClassName pattern = ClassName.get("io.koraframework.validation.common.annotation", "Pattern");
        public static final ClassName boundary = ClassName.get("io.koraframework.validation.common.annotation", "Range", "Boundary");

        public static final ClassName config = ClassName.get("io.koraframework.config.common", "Config");
        public static final ClassName configValueExtractor = ClassName.get("io.koraframework.config.common.extractor", "ConfigValueExtractor");
        public static final ClassName configValueExtractorAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigValueExtractor");
        public static final ClassName configSource = ClassName.get("io.koraframework.config.common.annotation", "ConfigSource");
        public static final ClassName configValueExtractionException = ClassName.get("io.koraframework.config.common.extractor", "ConfigValueExtractionException");

        public static final ClassName jsonGenerator = ClassName.get("tools.jackson.core", "JsonGenerator");
        public static final ClassName jsonParser = ClassName.get("tools.jackson.core", "JsonParser");
        public static final ClassName jsonWriterAnnotation = ClassName.get("io.koraframework.json.common.annotation", "JsonWriter");
        public static final ClassName jsonReaderAnnotation = ClassName.get("io.koraframework.json.common.annotation", "JsonReader");
        public static final ClassName json = ClassName.get("io.koraframework.json.common.annotation", "Json");
        public static final ClassName jsonField = ClassName.get("io.koraframework.json.common.annotation", "JsonField");
        public static final ClassName jsonInclude = ClassName.get("io.koraframework.json.common.annotation", "JsonInclude");
        public static final ClassName jsonNullable = ClassName.get("io.koraframework.json.common", "JsonNullable");
        public static final ClassName jsonDiscriminatorField = ClassName.get("io.koraframework.json.common.annotation", "JsonDiscriminatorField");
        public static final ClassName jsonDiscriminatorValue = ClassName.get("io.koraframework.json.common.annotation", "JsonDiscriminatorValue");
        public static final ClassName jsonWriter = ClassName.get("io.koraframework.json.common", "JsonWriter");
        public static final ClassName enumJsonWriter = ClassName.get("io.koraframework.json.common", "EnumJsonWriter");
        public static final ClassName jsonReader = ClassName.get("io.koraframework.json.common", "JsonReader");
        public static final ClassName enumJsonReader = ClassName.get("io.koraframework.json.common", "EnumJsonReader");
    }

    public CodegenParams params;
    public String apiPackage;
    public String modelPackage;
    public Map<String, ModelsMap> models;
    public Map<String, String> typeMapping;
    public Map<String, OperationsMap> operationsByClassName;
    public SecurityData security;

    public abstract R generate(C ctx);

    protected static String camelize(String s) {
        return org.openapitools.codegen.utils.StringUtils.camelize(s);
    }

    protected static String capitalize(String s) {
        return StringUtils.capitalize(s);
    }

    protected static String toVarName(String s) {
        return new KoraCodegen().toVarName(s);
    }

    public TypeName asType(OperationsMap ctx, CodegenOperation operation, CodegenParameter param) {
        if (param.getSchema() != null) {
            return asType(param.getSchema());
        }
        if (param.getContent() != null && !param.getContent().isEmpty()) {
            var content = param.getContent().sequencedEntrySet().getFirst();
            var schema = content.getValue().getSchema();
            if (schema != null) {
                return asType(schema);
            }
        }
        var type = asType(param);
        if (type != null) {
            return type;
        }

        if (param.isFormParam && param.isFile) {
            return Classes.formPart;
        }
        if (param.isFormParam) {
            if (param.isModel) {
                return ClassName.get(modelPackage, param.dataType);
            }
            return ClassName.bestGuess(param.dataType);
        }
        if (param.isModel) {
            return ClassName.get(modelPackage, param.dataType);
        }
        if (param.isEnumRef) {
            return ClassName.get(modelPackage, Objects.requireNonNullElse(param.datatypeWithEnum, param.dataType));
        }
        if (param.isEnum) {
            if (param.dataType.contains(".")) {
                return ClassName.bestGuess(param.dataType);
            }
        }
        throw new RuntimeException("Can't detect type of " + param.dataType);
    }

    public TypeName asType(IJsonSchemaValidationProperties schema) {
        if (schema instanceof CodegenResponse rs) {
            if (rs.isFile) {
                return ArrayTypeName.of(TypeName.BYTE);
            }
        }
        if (schema.getIsModel() && schema instanceof CodegenModel c) {
            return ClassName.get(modelPackage, c.getClassname());
        }
        if (schema.getComposedSchemas() != null && (schema.getComposedSchemas().getAllOf() != null || schema.getComposedSchemas().getOneOf() != null)) {
            if (schema instanceof CodegenModel c) {
                return ClassName.get(modelPackage, c.getClassname());
            }
            return ClassName.get(modelPackage, schema.getDataType());
        }
        if (schema.getIsArray()) {
            return ParameterizedTypeName.get(ClassName.get(List.class), asType(schema.getItems()).box());
        }
        if (schema.getIsMap()) {
            return ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), asType(schema.getAdditionalProperties()).box());
        }
        if (schema.getIsModel()) {
            if (schema.getDataType().contains(".")) {
                return ClassName.bestGuess(schema.getDataType());
            }
            return ClassName.get(modelPackage, schema.getDataType());
        }
        if (schema.getIsEnum()) {
            if (schema instanceof CodegenProperty p) {
                if (p.isInnerEnum) {
                    // this will be handled on model generator level
                    return ClassName.get(String.class);
                }
            }
            if (schema.getDataType().contains(".")) {
                return ClassName.bestGuess(schema.getDataType());
            }
            if (schema instanceof CodegenProperty p) {
                return ClassName.get(modelPackage, p.datatypeWithEnum);
            }
            if (schema instanceof CodegenParameter p) {
                if (schema.getRef() == null) {
                    // this will be handled on model generator level
                    return ClassName.get(String.class);
                }
                return ClassName.get(modelPackage, p.datatypeWithEnum);
            }
            return ClassName.get(modelPackage, schema.getDataType());
        }
        if (schema instanceof CodegenProperty p && p.isEnumRef) {
            return ClassName.get(modelPackage, schema.getDataType());
        }
        if (schema instanceof CodegenParameter p && p.isEnumRef) {
            return ClassName.get(modelPackage, schema.getDataType());
        }
        if (schema.getIsLong()) {
            return TypeName.LONG;
        }
        if (schema.getIsInteger()) {
            return TypeName.INT;
        }
        if (schema.getIsDouble()) {
            return TypeName.DOUBLE;
        }
        if (schema.getIsFloat()) {
            return TypeName.FLOAT;
        }
        if (schema.getIsShort()) {
            return TypeName.SHORT;
        }
        if (schema.getIsDecimal() || schema.getIsNumber()) {
            return ClassName.get(BigDecimal.class);
        }
        if (schema.getIsBinary()) {
            return ArrayTypeName.of(TypeName.BYTE);
        }
        if (schema.getIsDate()) {
            return ClassName.get(LocalDate.class);
        }
        if (schema.getIsDateTime()) {
            return ClassName.get(OffsetDateTime.class);
        }
        if (schema.getIsBoolean()) {
            return TypeName.BOOLEAN;
        }
        if (schema.getIsUuid()) {
            return ClassName.get(UUID.class);
        }

        if (schema.getIsString()) {
            if ("uri".equals(schema.getFormat())) {
                return ClassName.get(URI.class);
            }
            return ClassName.get(String.class);
        }
        if (schema.getIsBinary() || schema.getIsByteArray()) {
            return ArrayTypeName.of(TypeName.BYTE);
        }
        if (schema.getRef() != null) {
            // must be model one
            return ClassName.get(modelPackage, schema.getDataType());
        }
        if (schema.getIsModel()) {
            return ClassName.get(modelPackage, schema.getDataType());
        }
        throw new IllegalArgumentException(schema.toString());
    }
}
