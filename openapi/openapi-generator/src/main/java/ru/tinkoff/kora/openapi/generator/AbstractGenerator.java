package ru.tinkoff.kora.openapi.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.media.JsonSchema;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.IJsonSchemaValidationProperties;
import org.openapitools.codegen.model.OperationsMap;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public abstract class AbstractGenerator<C, R> {
    public static class Classes {
        public static final ClassName generated = ClassName.get("ru.tinkoff.kora.common.annotation", "Generated");
        public static final ClassName tag = ClassName.get("ru.tinkoff.kora.common", "Tag");
        public static final ClassName mapping = ClassName.get("ru.tinkoff.kora.common", "Mapping");
        public static final ClassName httpRoute = ClassName.get("ru.tinkoff.kora.http.common.annotation", "HttpRoute");
        public static final ClassName httpClient = ClassName.get("ru.tinkoff.kora.http.client.common.annotation", "HttpClient");
        public static final ClassName responseCodeMapper = ClassName.get("ru.tinkoff.kora.http.client.common.annotation", "ResponseCodeMapper");
        public static final ClassName httpClientInterceptor = ClassName.get("ru.tinkoff.kora.http.client.common.interceptor", "HttpClientInterceptor");
        public static final ClassName interceptWith = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith");
        public static final ClassName query = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Query");
        public static final ClassName path = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Path");
        public static final ClassName header = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Header");
        public static final ClassName cookie = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Cookie");
        public static final ClassName nullable = ClassName.get("jakarta.annotation", "Nullable");
        public static final ClassName formPart = ClassName.get("ru.tinkoff.kora.http.common.form", "FormMultipart", "FormPart");
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
            return ClassName.bestGuess(param.dataType);
        }
        if (!param.isPrimitiveType) {
            throw new IllegalArgumentException(param.dataType + " is not a primitive type");
        }
        var schema = param.getSchema();
        var openapiType = schema != null ? schema.getOpenApiType() : null;
        if (openapiType == null) {
            // must be form param
            if (!param.isFormParam) {
                throw new IllegalArgumentException();
            }
            try {
                var jsonSchema = Json.mapper().readValue(param.jsonSchema, JsonSchema.class);
                openapiType = jsonSchema.getType();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
//        var type = switch (openapiType) {
//            case "string" -> ClassName.get(String.class);
//            case "boolean" -> TypeName.BOOLEAN;
//            case "integer" -> Objects.equals(param.dataFormat, "int32")
//                ? TypeName.INT
//                : TypeName.LONG;
//            case "number" -> {
//                if (Objects.equals(param.dataFormat, "double")) {
//                    yield TypeName.DOUBLE;
//                }
//                if (Objects.equals(param.dataFormat, "float")) {
//                    yield TypeName.FLOAT;
//                }
//                yield ClassName.get(BigDecimal.class);
//            }
//            default -> throw new IllegalArgumentException(openapiType + "/" + param.dataType + "/" + param.dataFormat + " unexpected");
//        };
        if (param.required) {
            return type;
        } else {
            return type.box();
        }
    }

    public TypeName asType(IJsonSchemaValidationProperties schema) {
        if (schema.getIsArray()) {
            return ParameterizedTypeName.get(ClassName.get(List.class), asType(schema.getItems()).box());
        }
        if (schema.getIsModel()) {
            // todo
            if (schema.getDataType().contains(".")) {
                return ClassName.bestGuess(schema.getDataType());
            }
            return ClassName.get(modelPackage, schema.getDataType());
        }
        if (schema.getIsEnum()) {
            if (schema instanceof CodegenProperty p) {
                if (p.isInnerEnum) {
                    throw new RuntimeException("Inner enums are not supported right now");
                }
            }
            // todo
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
