package ru.tinkoff.kora.openapi.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.IJsonSchemaValidationProperties;
import org.openapitools.codegen.model.OperationsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public abstract class AbstractGenerator<C, R> {
    private static final Logger log = LoggerFactory.getLogger(AbstractGenerator.class);

    public static class Classes {
        public static final ClassName generated = ClassName.get("ru.tinkoff.kora.common.annotation", "Generated");
        public static final ClassName tag = ClassName.get("ru.tinkoff.kora.common", "Tag");
        public static final ClassName component = ClassName.get("ru.tinkoff.kora.common", "Component");
        public static final ClassName mapping = ClassName.get("ru.tinkoff.kora.common", "Mapping");
        public static final ClassName httpRoute = ClassName.get("ru.tinkoff.kora.http.common.annotation", "HttpRoute");
        public static final ClassName httpClient = ClassName.get("ru.tinkoff.kora.http.client.common.annotation", "HttpClient");
        public static final ClassName httpController = ClassName.get("ru.tinkoff.kora.http.server.common.annotation", "HttpController");
        public static final ClassName responseCodeMapper = ClassName.get("ru.tinkoff.kora.http.client.common.annotation", "ResponseCodeMapper");
        public static final ClassName httpClientInterceptor = ClassName.get("ru.tinkoff.kora.http.client.common.interceptor", "HttpClientInterceptor");
        public static final ClassName httpServerInterceptor = ClassName.get("ru.tinkoff.kora.http.server.common", "HttpServerInterceptor");
        public static final ClassName httpServerRequest = ClassName.get("ru.tinkoff.kora.http.server.common", "HttpServerRequest");
        public static final ClassName interceptWith = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith");
        public static final ClassName query = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Query");
        public static final ClassName path = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Path");
        public static final ClassName header = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Header");
        public static final ClassName cookie = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Cookie");
        public static final ClassName nullable = ClassName.get("jakarta.annotation", "Nullable");
        public static final ClassName formPart = ClassName.get("ru.tinkoff.kora.http.common.form", "FormMultipart", "FormPart");

        public static final ClassName validationHttpServerInterceptor = ClassName.get("ru.tinkoff.kora.validation.module.http.server", "ValidationHttpServerInterceptor");
        public static final ClassName valid = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Valid");
        public static final ClassName validate = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Validate");
        public static final ClassName range = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Range");
        public static final ClassName size = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Size");
        public static final ClassName pattern = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Pattern");
        public static final ClassName boundary = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Range", "Boundary");
    }

    public KoraCodegen.CodegenParams params;
    public String apiPackage;
    public String modelPackage;

    public abstract R generate(C ctx);

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
        if (schema.getIsArray()) {
            return ParameterizedTypeName.get(ClassName.get(List.class), asType(schema.getItems()).box());
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
                    log.warn("Inner enums are not supported right now and will be replaced with strings");
                    return ClassName.get(String.class);
                }
            }
            if (schema.getDataType().contains(".")) {
                return ClassName.bestGuess(schema.getDataType());
            }
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
        if (schema.getRef() != null) {
            // must be model one
            return ClassName.get(modelPackage, schema.getDataType());
        }
        if (schema.getIsModel()) {
            return ClassName.get(modelPackage, schema.getDataType());
        }
        return null;

    }
}
