package io.koraframework.openapi.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openapitools.codegen.*;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.SourceVersion;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

@NullMarked
public abstract class AbstractGenerator<C, R> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractGenerator.class);

    public static class Classes {

        // Common
        public static final ClassName nullable = ClassName.get("org.jspecify.annotations", "Nullable");
        public static final ClassName jsonGenerator = ClassName.get("tools.jackson.core", "JsonGenerator");
        public static final ClassName jsonParser = ClassName.get("tools.jackson.core", "JsonParser");

        public static final ClassName config = ClassName.get("io.koraframework.config.common", "Config");
        public static final ClassName configSource = ClassName.get("io.koraframework.config.common.annotation", "ConfigSource");
        public static final ClassName configValueExtractorAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigMapper");
        public static final ClassName configValueException = ClassName.get("io.koraframework.config.common.exception", "ConfigValueException");
        public static final ClassName configValueExtractor = ClassName.get("io.koraframework.config.common.mapper", "ConfigValueMapper");

        public static final ClassName generated = ClassName.get("io.koraframework.common.annotation", "Generated");
        public static final ClassName httpRoute = ClassName.get("io.koraframework.http.common.annotation", "HttpRoute");
        public static final ClassName httpClient = ClassName.get("io.koraframework.http.client.common.annotation", "HttpClient");
        public static final ClassName httpHeaders = ClassName.get("io.koraframework.http.common.header", "HttpHeaders");
        public static final ClassName interceptWith = ClassName.get("io.koraframework.http.common.annotation", "InterceptWith");
        public static final ClassName query = ClassName.get("io.koraframework.http.common.annotation", "Query");
        public static final ClassName path = ClassName.get("io.koraframework.http.common.annotation", "Path");
        public static final ClassName header = ClassName.get("io.koraframework.http.common.annotation", "Header");
        public static final ClassName cookie = ClassName.get("io.koraframework.http.common.annotation", "Cookie");

        public static final ClassName tag = ClassName.get("io.koraframework.common.annotation", "Tag");
        public static final ClassName component = ClassName.get("io.koraframework.common.annotation", "Component");
        public static final ClassName defaultComponent = ClassName.get("io.koraframework.common.annotation", "DefaultComponent");
        public static final ClassName module = ClassName.get("io.koraframework.common.annotation", "Module");
        public static final ClassName mapping = ClassName.get("io.koraframework.common.annotation", "Mapping");
        public static final ClassName principal = ClassName.get("io.koraframework.common", "Principal");
        public static final ClassName httpResponseEntity = ClassName.get("io.koraframework.http.common", "HttpResponseEntity");
        public static final ClassName principalWithScopes = ClassName.get("io.koraframework.http.common.auth", "PrincipalWithScopes");
        public static final ClassName httpBody = ClassName.get("io.koraframework.http.common.body", "HttpBody");
        public static final ClassName formMultipart = ClassName.get("io.koraframework.http.common.form", "FormMultipart");
        public static final ClassName formPart = formMultipart.nestedClass("FormPart");
        public static final ClassName httpBodyInput = ClassName.get("io.koraframework.http.common.body", "HttpBodyInput");
        public static final ClassName httpBodyOutput = ClassName.get("io.koraframework.http.common.body", "HttpBodyOutput");

        // Client
        public static final ClassName responseCodeMapper = ClassName.get("io.koraframework.http.client.common.annotation", "ResponseCodeMapper");
        public static final ClassName httpClientTokenProvider = ClassName.get("io.koraframework.http.client.common.auth", "HttpClientTokenProvider");
        public static final ClassName basicAuthHttpClientTokenProvider = ClassName.get("io.koraframework.http.client.common.auth", "BasicAuthHttpClientTokenProvider");
        public static final ClassName apiKeyHttpClientInterceptor = ClassName.get("io.koraframework.http.client.common.interceptor", "ApiKeyHttpClientInterceptor");
        public static final ClassName basicAuthHttpClientInterceptor = ClassName.get("io.koraframework.http.client.common.interceptor", "BasicAuthHttpClientInterceptor");
        public static final ClassName bearerAuthHttpClientInterceptor = ClassName.get("io.koraframework.http.client.common.interceptor", "BearerAuthHttpClientInterceptor");
        public static final ClassName httpClientInterceptor = ClassName.get("io.koraframework.http.client.common.interceptor", "HttpClientInterceptor");
        public static final ClassName httpClientInterceptChain = httpClientInterceptor.nestedClass("InterceptChain");

        public static final ClassName httpClientRequest = ClassName.get("io.koraframework.http.client.common.request", "HttpClientRequest");
        public static final ClassName multipartWriter = ClassName.get("io.koraframework.http.client.common.request.form", "MultipartWriterUtils");
        public static final ClassName httpClientRequestMapper = ClassName.get("io.koraframework.http.client.common.request", "HttpClientRequestMapper");

        public static final ClassName httpClientResponse = ClassName.get("io.koraframework.http.client.common.response", "HttpClientResponse");
        public static final ClassName httpClientResponseMapper = ClassName.get("io.koraframework.http.client.common.response", "HttpClientResponseMapper");
        public static final ClassName httpClientResponseException = ClassName.get("io.koraframework.http.client.common.exception", "HttpClientResponseException");
        public static final ClassName simpleHttpClientResponse = ClassName.get("io.koraframework.http.client.common.response", "SimpleHttpClientResponse");
        public static final ClassName stringParameterConverter = ClassName.get("io.koraframework.http.client.common.request", "HttpClientParameterWriter");
        public static final ClassName enumStringParameterConverter = ClassName.get("io.koraframework.http.client.common.request.mapper", "EnumHttpClientParameterWriter");

        // Server
        public static final ClassName httpController = ClassName.get("io.koraframework.http.server.common.annotation", "HttpController");
        public static final ClassName httpServerRequest = ClassName.get("io.koraframework.http.server.common.request", "HttpServerRequest");
        public static final ClassName httpServerResponse = ClassName.get("io.koraframework.http.server.common.response", "HttpServerResponse");
        public static final ClassName httpServerInterceptor = ClassName.get("io.koraframework.http.server.common.interceptor", "HttpServerInterceptor");
        public static final ClassName httpServerResponseException = ClassName.get("io.koraframework.http.server.common.response", "HttpServerResponseException");
        public static final ClassName httpServerPrincipalExtractor = ClassName.get("io.koraframework.http.server.common.auth", "HttpServerPrincipalExtractor");
        public static final ClassName httpServerInterceptChain = httpServerInterceptor.nestedClass("InterceptChain");

        public static final ClassName stringParameterReader = ClassName.get("io.koraframework.http.server.common.request", "HttpServerParameterReader");
        public static final ClassName httpServerRequestMapper = ClassName.get("io.koraframework.http.server.common.request", "HttpServerRequestMapper");
        public static final ClassName httpServerResponseMapper = ClassName.get("io.koraframework.http.server.common.response", "HttpServerResponseMapper");
        public static final ClassName enumStringParameterReader = ClassName.get("io.koraframework.http.server.common.request.mapper", "EnumHttpServerParameterReader");

        // Validation
        public static final ClassName valid = ClassName.get("io.koraframework.validation.common.annotation", "Valid");
        public static final ClassName validate = ClassName.get("io.koraframework.validation.common.annotation", "Validate");
        public static final ClassName range = ClassName.get("io.koraframework.validation.common.annotation", "Range");
        public static final ClassName min = ClassName.get("io.koraframework.validation.common.annotation", "Min");
        public static final ClassName max = ClassName.get("io.koraframework.validation.common.annotation", "Max");
        public static final ClassName positive = ClassName.get("io.koraframework.validation.common.annotation", "Positive");
        public static final ClassName positiveOrZero = ClassName.get("io.koraframework.validation.common.annotation", "PositiveOrZero");
        public static final ClassName negative = ClassName.get("io.koraframework.validation.common.annotation", "Negative");
        public static final ClassName negativeOrZero = ClassName.get("io.koraframework.validation.common.annotation", "NegativeOrZero");
        public static final ClassName size = ClassName.get("io.koraframework.validation.common.annotation", "Size");
        public static final ClassName pattern = ClassName.get("io.koraframework.validation.common.annotation", "Pattern");
        public static final ClassName boundary = ClassName.get("io.koraframework.validation.common.annotation", "Range", "Boundary");
        public static final ClassName validationHttpServerInterceptor = ClassName.get("io.koraframework.validation.module.http.server", "ValidationHttpServerInterceptor");

        // Json
        public static final ClassName json = ClassName.get("io.koraframework.json.common.annotation", "Json");
        public static final ClassName jsonField = ClassName.get("io.koraframework.json.common.annotation", "JsonField");
        public static final ClassName jsonInclude = ClassName.get("io.koraframework.json.common.annotation", "JsonInclude");
        public static final ClassName jsonWriterAnnotation = ClassName.get("io.koraframework.json.common.annotation", "JsonWriter");
        public static final ClassName jsonReaderAnnotation = ClassName.get("io.koraframework.json.common.annotation", "JsonReader");
        public static final ClassName jsonDiscriminatorField = ClassName.get("io.koraframework.json.common.annotation", "JsonDiscriminatorField");
        public static final ClassName jsonDiscriminatorValue = ClassName.get("io.koraframework.json.common.annotation", "JsonDiscriminatorValue");
        public static final ClassName jsonNullable = ClassName.get("io.koraframework.json.common", "JsonNullable");
        public static final ClassName jsonWriter = ClassName.get("io.koraframework.json.common", "JsonWriter");
        public static final ClassName enumJsonWriter = ClassName.get("io.koraframework.json.common.writer", "EnumJsonWriter");
        public static final ClassName jsonReader = ClassName.get("io.koraframework.json.common", "JsonReader");
        public static final ClassName enumJsonReader = ClassName.get("io.koraframework.json.common.reader", "EnumJsonReader");
    }

    public CodegenParams params;
    public String apiPackage;
    public String modelPackage;
    public String outputFolder;
    public Map<String, ModelsMap> models;
    public Map<String, String> typeMapping;
    public Map<String, OperationsMap> operationsByClassName;
    public SecurityData security;

    public abstract R generate(C ctx);

    /**
     * @return true when the oneOf member references the schema of the model, so the model implements the oneOf interface
     */
    protected boolean isOneOfMember(CodegenProperty member, CodegenModel model) {
        if (member.getRef() == null) {
            return false;
        }
        var memberModels = models.get(ModelUtils.getSimpleRef(member.getRef()));
        return memberModels != null && memberModels.getModels().getFirst().getModel().classname.equals(model.classname);
    }

    /**
     * A member of a oneOf without a discriminator.
     *
     * @param model    class of an object schema that implements the oneOf interface itself, {@code null} for any other member
     * @param property the member as it is declared: a string, a number, an array, a map or an enum is wrapped into a subtype with a single value
     */
    public record OneOfMember(@Nullable CodegenModel model, CodegenProperty property) {}

    /**
     * Members of a oneOf without a discriminator. Such a schema is a sealed interface: an object schema implements it,
     * any other member gets a wrapper subtype. JSON is written by the actual subtype, but there is nothing to choose
     * a subtype by while reading, so a JSON reader is not generated and has to be provided by an application.
     */
    protected List<OneOfMember> oneOfWithoutDiscriminatorMembers(CodegenModel model) {
        var result = new ArrayList<OneOfMember>();
        var oneOf = model.getComposedSchemas() == null ? null : model.getComposedSchemas().getOneOf();
        for (var member : oneOf == null ? List.<CodegenProperty>of() : oneOf) {
            var memberModels = member.getRef() == null ? null : models.get(ModelUtils.getSimpleRef(member.getRef()));
            var memberModel = memberModels == null ? null : memberModels.getModels().getFirst().getModel();
            if (memberModel == null || memberModel.isEnum || memberModel.isMap || memberModel.isArray || memberModel.isPrimitiveType) {
                result.add(new OneOfMember(null, member));
            } else if (result.stream().noneMatch(m -> m.model() == memberModel)) {
                result.add(new OneOfMember(memberModel, member));
            }
        }
        return result;
    }

    /**
     * @param subtypeReaders readers an own reader of the schema can be built from, see {@link #oneOfSubtypeReader}
     */
    protected void warnOneOfWithoutDiscriminator(CodegenModel model, List<String> subtypeReaders) {
        logger.warn("""
            OpenAPI schema `{}` is a oneOf without a discriminator: it is generated as a sealed interface `{}`.
            JSON writer is generated, but JSON reader can't be generated: nothing tells which subtype to read.
            Provide an own `JsonReader<{}>` component where the schema is read: a server request body, a client response body or a field of a model that is read.
            It can be built from the readers of the subtypes:
            {}""",
            model.name, model.classname, model.classname, subtypeReaders.stream().map(reader -> "  - " + reader).collect(java.util.stream.Collectors.joining("\n")));
    }

    /**
     * @param subtype    simple name of a subtype of a oneOf without a discriminator
     * @param valueType  type of the wrapped value, or {@code null} when the subtype is a class of an object schema
     * @return the reader an application already has for the subtype: of the subtype itself or of the value it wraps
     */
    protected static String oneOfSubtypeReader(String subtype, @Nullable String valueType) {
        if (valueType == null) {
            return "JsonReader<%s>".formatted(subtype);
        }
        // packages are dropped: java.util.List<java.lang.String> -> List<String>
        return "JsonReader<%s> for the value of %s".formatted(valueType.replaceAll("\\b[a-z_][\\w$]*\\.", ""), subtype);
    }

    /**
     * Name of the wrapper subtype of a oneOf member that is not an object schema, by the type of its value: {@code StringValue}, {@code ListStringValue}
     */
    protected static String oneOfValueName(TypeName valueType) {
        return oneOfValueTypeName(valueType) + "Value";
    }

    private static String oneOfValueTypeName(TypeName type) {
        if (type instanceof ClassName className) {
            return String.join("", className.simpleNames());
        }
        if (type instanceof ParameterizedTypeName parameterized) {
            var name = new StringBuilder(oneOfValueTypeName(parameterized.rawType()));
            for (var typeArgument : parameterized.typeArguments()) {
                name.append(oneOfValueTypeName(typeArgument));
            }
            return name.toString();
        }
        if (type instanceof ArrayTypeName array) {
            return oneOfValueTypeName(array.componentType().box()) + "Array";
        }
        return type.withoutAnnotations().toString().replaceAll("\\W", "");
    }

    /**
     * Discriminator subtypes of a model: the explicit discriminator mapping plus every oneOf member it does not cover,
     * which OpenAPI maps implicitly by its schema name.
     */
    protected List<CodegenDiscriminator.MappedModel> discriminatorMappedModels(CodegenModel model) {
        var result = new ArrayList<>(model.discriminator.getMappedModels());
        var oneOf = model.getComposedSchemas() == null ? null : model.getComposedSchemas().getOneOf();
        if (oneOf == null) {
            return result;
        }
        for (var member : oneOf) {
            if (member.getRef() == null) {
                continue;
            }
            var schemaName = ModelUtils.getSimpleRef(member.getRef());
            var memberModels = models.get(schemaName);
            if (memberModels == null) {
                continue;
            }
            var memberModel = memberModels.getModels().getFirst().getModel();
            if (result.stream().noneMatch(m -> m.getModelName().equals(memberModel.classname))) {
                // inline members are extracted as <parent>_oneOf[_N]: they have no name to map, and a free-form one is not even a class
                if (memberModel.isMap || memberModel.isArray || memberModel.isPrimitiveType || schemaName.matches(Pattern.quote(model.name) + "_oneOf(_\\d+)?")) {
                    throw new IllegalArgumentException(oneOfWithDiscriminatorInlineMemberError(model.name));
                }
                var mappedModel = new CodegenDiscriminator.MappedModel(schemaName, memberModel.classname);
                mappedModel.setModel(memberModel);
                result.add(mappedModel);
            }
        }
        return result;
    }

    /**
     * A property of a sealed model that a subtype property overrides.
     */
    protected record SealedParentProperty(CodegenModel parent, CodegenProperty property) {}

    @Nullable
    protected SealedParentProperty sealedParentProperty(CodegenModel model, CodegenProperty property) {
        for (var modelsMap : models.values()) {
            var parent = modelsMap.getModels().getFirst().getModel();
            if (parent == model || parent.discriminator == null) {
                continue;
            }
            if (discriminatorMappedModels(parent).stream().noneMatch(m -> m.getModelName().equals(model.classname))) {
                continue;
            }
            for (var parentProperty : parent.allVars) {
                if (parentProperty.name.equals(property.name)) {
                    return new SealedParentProperty(parent, parentProperty);
                }
            }
        }
        return null;
    }

    /**
     * A subtype implements the accessors its sealed parent declares, so a property both declare must have one type.
     *
     * @throws IllegalArgumentException when the subtype declares the property with a type the accessor of the parent cannot have
     */
    protected void checkSealedParentProperty(CodegenModel model, CodegenProperty property) {
        var inherited = sealedParentProperty(model, property);
        if (inherited == null || property.isAnyType || inherited.property().isAnyType) {
            return;
        }
        var parentProperty = inherited.property();
        // an inline enum is a string here, its class is the one of the parent
        var parentType = asType(parentProperty).box().withoutAnnotations();
        var type = asType(property).box().withoutAnnotations();
        // a model of a subtype may extend the model of the parent
        if (!parentType.equals(type) && !(parentProperty.isModel && property.isModel)) {
            throw new IllegalArgumentException(sealedParentPropertyTypeError(model, property, inherited.parent(), parentType.toString(), type.toString()));
        }
        if (property.name.equals(inherited.parent().discriminator.getPropertyName())) {
            // the discriminator is required and not nullable, whatever the schemas declare
            return;
        }
        if (property.isNullable && !parentProperty.isNullable) {
            // a subtype is also a value of its parent schema, which does not allow null
            logger.warn("""
                Invalid OpenAPI schema `{}`: property `{}` is nullable, but the same property of its discriminator parent `{}` is not.

                A value of a subtype must also be valid against the parent schema, so null is never valid and the property is generated as not nullable.

                Fix: remove `nullable: true` from property `{}` in schema `{}`, or add it to the property in schema `{}`.
                """, model.name, property.baseName, inherited.parent().name, property.baseName, model.name, inherited.parent().name);
            property.isNullable = false;
        }
        // a nullable property that is not required is a JsonNullable, any other one is a plain value
        var required = property.required || parentProperty.required;
        var parentJsonNullable = parentProperty.isNullable && !parentProperty.required;
        var jsonNullable = property.isNullable && !required;
        if (parentJsonNullable != jsonNullable) {
            throw new IllegalArgumentException(sealedParentPropertyTypeError(model, property, inherited.parent(),
                propertyPresence(parentProperty.required, parentProperty.isNullable), propertyPresence(required, property.isNullable)));
        }
    }

    private static String propertyPresence(boolean required, boolean nullable) {
        return (required ? "required" : "not required") + ", " + (nullable ? "nullable" : "not nullable");
    }

    private static String sealedParentPropertyTypeError(CodegenModel model, CodegenProperty property, CodegenModel parent, String parentDeclares, String modelDeclares) {
        return """
            Invalid OpenAPI schema `%s`: property `%s` differs from the same property of its discriminator parent `%s`.

            Parent `%s` declares: %s
            Subtype `%s` declares: %s

            A discriminator subtype implements the accessors of its parent, so a property both of them declare must have the same type, `required` and `nullable`.

            Fix: declare property `%s` the same way in both schemas, or remove it from schema `%s` so it is inherited from the parent.
            """.formatted(model.name, property.baseName, parent.name, parent.name, parentDeclares, model.name, modelDeclares, property.baseName, model.name);
    }

    /**
     * A subtype is picked by its discriminator, so the property is always there, whatever the schema declares.
     */
    protected void requireDiscriminatorProperty(CodegenModel model, CodegenProperty property) {
        // a subtype inherits the declaration of its parent, the warning is on the schema that declares the property
        if ((!property.required || property.isNullable) && sealedParentProperty(model, property) == null) {
            logger.warn("""
                Invalid OpenAPI schema `{}`: discriminator property `{}` is {}.

                A discriminator property must be required and not nullable, so it is generated as a required non-null value.

                Fix: {} in schema `{}`.
                """, model.name, property.baseName,
                !property.required && property.isNullable ? "not required and nullable" : property.isNullable ? "nullable" : "not required",
                !property.required && property.isNullable ? "add `%s` to `required` and remove `nullable: true` from it".formatted(property.baseName)
                    : property.isNullable ? "remove `nullable: true` from property `%s`".formatted(property.baseName)
                    : "add `%s` to `required`".formatted(property.baseName),
                model.name);
        }
        property.required = true;
        property.isNullable = false;
    }

    /**
     * Discriminator values of a model when the property is the discriminator of a parent of the model, sorted.
     */
    protected List<String> discriminatorValues(CodegenModel model, CodegenProperty property) {
        var values = new TreeSet<String>();
        for (var modelsMap : models.values()) {
            var parent = modelsMap.getModels().getFirst().getModel();
            if (parent == model || parent.discriminator == null || !property.name.equals(parent.discriminator.getPropertyName())) {
                continue;
            }
            for (var mappedModel : discriminatorMappedModels(parent)) {
                if (mappedModel.getModelName().equals(model.classname)) {
                    values.add(mappedModel.getMappingName());
                }
            }
        }
        return List.copyOf(values);
    }

    /**
     * An inline enum property with the model that declares its nested enum class and the property as that model declares it.
     */
    protected record InlineEnum(CodegenModel owner, CodegenProperty property) {
        public CodegenProperty source() {
            return inlineEnumSource(property);
        }
    }

    private static CodegenProperty inlineEnumSource(CodegenProperty property) {
        var source = property;
        while (source.isContainer && source.items != null) {
            source = source.items;
        }
        return source;
    }

    /**
     * The nested enum class of a model property, null when the property has none.
     * A subtype implements the accessors its sealed parent declares, so a property the parent declares has the type the parent gives it:
     * the enum nested in the parent when the parent declares an inline enum, and no enum class at all otherwise.
     */
    @Nullable
    protected InlineEnum inlineEnum(CodegenModel model, CodegenProperty property) {
        var inherited = sealedParentProperty(model, property);
        if (inherited != null) {
            return inlineEnum(inherited.parent(), inherited.property());
        }
        return property.isInnerEnum ? new InlineEnum(model, property) : null;
    }

    /**
     * Enum vars of an inline enum. Subtypes share the enum a sealed model declares, so that enum also has the values a subtype declares
     * for the property. An enum of a discriminator property also has the mapping names the declared values miss.
     */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> inlineEnumVars(InlineEnum inlineEnum) {
        var owner = inlineEnum.owner();
        var source = inlineEnum.source();
        var enumVars = new ArrayList<>((List<Map<String, Object>>) source.allowableValues.get("enumVars"));
        var discriminatorValues = new ArrayList<>(discriminatorValues(owner, inlineEnum.property()));
        if (owner.discriminator != null) {
            var mappedModels = discriminatorMappedModels(owner);
            for (var mappedModel : mappedModels) {
                if (mappedModel.getModel() == null) {
                    continue;
                }
                for (var subtypeProperty : mappedModel.getModel().allVars) {
                    if (subtypeProperty.name.equals(inlineEnum.property().name)) {
                        for (var enumVar : declaredEnumVars(subtypeProperty, source)) {
                            addEnumVar(enumVars, enumVar);
                        }
                    }
                }
                if (inlineEnum.property().name.equals(owner.discriminator.getPropertyName())) {
                    discriminatorValues.add(mappedModel.getMappingName());
                }
            }
        }
        if (source.isString) {
            var codegen = new KoraCodegen();
            for (var discriminatorValue : discriminatorValues) {
                var enumVar = new HashMap<String, Object>();
                enumVar.put("name", codegen.toEnumVarName(discriminatorValue, source.dataType));
                enumVar.put("value", codegen.toEnumValue(discriminatorValue, source.dataType));
                enumVar.put("isString", true);
                addEnumVar(enumVars, enumVar);
            }
        }
        return enumVars;
    }

    // enum vars a property declares as an inline enum of the same type as the given one
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> declaredEnumVars(CodegenProperty property, CodegenProperty sameTypeSource) {
        if (!property.isInnerEnum) {
            return List.of();
        }
        var source = inlineEnumSource(property);
        if (source.allowableValues == null || !Objects.equals(source.dataType, sameTypeSource.dataType)) {
            return List.of();
        }
        return (List<Map<String, Object>>) source.allowableValues.get("enumVars");
    }

    private static void addEnumVar(List<Map<String, Object>> enumVars, Map<String, Object> enumVar) {
        if (enumVars.stream().anyMatch(v -> String.valueOf(v.get("value")).equals(String.valueOf(enumVar.get("value"))))) {
            return;
        }
        var name = String.valueOf(enumVar.get("name"));
        var uniqueName = name;
        for (var suffix = 2; containsEnumVarName(enumVars, uniqueName); suffix++) {
            uniqueName = name + "_" + suffix;
        }
        var copy = new HashMap<>(enumVar);
        copy.put("name", uniqueName);
        enumVars.add(copy);
    }

    private static boolean containsEnumVarName(List<Map<String, Object>> enumVars, String name) {
        return enumVars.stream().anyMatch(v -> name.equals(String.valueOf(v.get("name"))));
    }

    /**
     * Values a model accepts for a property whose type has more of them: the discriminator values of a subtype,
     * or the values a subtype declares for an enum it shares with its sealed parent.
     *
     * @param enumType  the enum of the property, null when the property is a string
     * @param constants enum constant names, or the strings themselves
     * @param values    the values as the schema declares them
     */
    protected record AllowedValues(@Nullable ClassName enumType, List<String> constants, List<String> values, boolean discriminator) {}

    @Nullable
    @SuppressWarnings("unchecked")
    protected AllowedValues allowedValues(CodegenModel model, CodegenProperty property) {
        if (property.isContainer) {
            return null;
        }
        var discriminatorValues = discriminatorValues(model, property);
        var discriminator = !discriminatorValues.isEmpty();
        var inlineEnum = inlineEnum(model, property);
        var codegen = new KoraCodegen();
        ClassName enumType;
        List<Map<String, Object>> enumVars;
        List<String> literals;
        if (inlineEnum != null) {
            var source = inlineEnum.source();
            enumType = ClassName.get(modelPackage, inlineEnum.owner().classname, source.enumName);
            enumVars = inlineEnumVars(inlineEnum);
            var declared = declaredEnumVars(property, source).stream().map(v -> String.valueOf(v.get("value"))).toList();
            if (discriminator) {
                // the mapping picks the subtype, so every value mapped to it is accepted, whatever enum the subtype declares
                literals = discriminatorValues.stream().map(v -> codegen.toEnumValue(v, source.dataType)).toList();
                var undeclared = declared.isEmpty() ? List.<String>of() : literals.stream().filter(v -> !declared.contains(v)).toList();
                if (!undeclared.isEmpty()) {
                    logger.warn("""
                        Invalid OpenAPI schema `{}`: discriminator property `{}` maps values {} to the schema, but its enum {} does not allow them.

                        The discriminator mapping takes precedence, so the schema accepts every value mapped to it.

                        Fix: add the values to the enum of property `{}`, or remove them from the discriminator mapping.
                        """, model.name, property.baseName, undeclared, declared, property.baseName);
                }
            } else {
                literals = inlineEnum.owner() == model ? List.of() : declared;
            }
        } else if (!discriminator) {
            return null;
        } else if (property.isString && !property.isEnum && !property.isEnumRef) {
            return new AllowedValues(null, discriminatorValues, discriminatorValues, true);
        } else {
            var enumModel = models.values().stream()
                .map(m -> m.getModels().getFirst().getModel())
                .filter(m -> m.isEnum && m.isString && m.classname.equals(property.dataType))
                .findFirst()
                .orElse(null);
            if (enumModel == null) {
                return null;
            }
            enumType = ClassName.get(modelPackage, enumModel.classname);
            enumVars = (List<Map<String, Object>>) enumModel.allowableValues.get("enumVars");
            literals = discriminatorValues.stream().map(v -> codegen.toEnumValue(v, enumModel.dataType)).toList();
        }
        var constants = new ArrayList<String>();
        var values = new ArrayList<String>();
        for (var literal : literals) {
            var enumVar = enumVars.stream().filter(v -> literal.equals(String.valueOf(v.get("value")))).findFirst().orElse(null);
            if (enumVar == null) {
                if (discriminator) {
                    logger.warn("""
                        Invalid OpenAPI schema `{}`: discriminator property `{}` maps value {} to the schema, but enum `{}` does not have it.

                        The value cannot be read or written, and the discriminator value of the schema is not checked.

                        Fix: add the value to the enum, or remove it from the discriminator mapping.
                        """, model.name, property.baseName, literal, enumType.simpleName());
                }
                return null;
            }
            constants.add(String.valueOf(enumVar.get("name")));
            values.add(literal.length() > 1 && literal.startsWith("\"") && literal.endsWith("\"") ? literal.substring(1, literal.length() - 1) : literal);
        }
        if (constants.isEmpty() || constants.size() == enumVars.size()) {
            return null;
        }
        return new AllowedValues(enumType, constants, values, discriminator);
    }

    private static String oneOfWithDiscriminatorInlineMemberError(String schemaName) {
        return """
            Unsupported OpenAPI schema `%s`: oneOf with a discriminator requires each member to be a named object schema referenced via $ref.

            Kora generates oneOf as a sealed interface with one class per member, mapped to a discriminator value by its schema name, so inline, free-form, array or primitive members cannot be subtypes.

            Fix: move each oneOf member to `components/schemas` as an object schema and reference it via `$ref`.
            """.formatted(schemaName);
    }

    protected static String camelize(String s) {
        return org.openapitools.codegen.utils.StringUtils.camelize(s);
    }

    protected static String capitalize(String s) {
        return StringUtils.capitalize(s);
    }

    /**
     * OpenAPI allows an operation to declare a response for a whole status-code range: {@code 1XX},
     * {@code 2XX}, {@code 3XX}, {@code 4XX} or {@code 5XX} (see the OpenAPI 3 "Responses Object").
     * These are not exact codes, so they cannot be emitted where an {@code int} status is required
     * and cannot be registered directly on a {@code @ResponseCodeMapper(code = ...)}.
     */
    public static boolean isRangeCode(CodegenResponse response) {
        return !response.isDefault && response.code != null && response.code.matches("[1-5]XX");
    }

    /** Inclusive lower bound of a range code, e.g. {@code "4XX"} -> {@code 400}. */
    public static int rangeCodeLowerBound(String code) {
        return (code.charAt(0) - '0') * 100;
    }

    /** Exclusive upper bound of a range code, e.g. {@code "4XX"} -> {@code 500}. */
    public static int rangeCodeUpperBound(String code) {
        return rangeCodeLowerBound(code) + 100;
    }

    /** Whether a range response or an OpenAPI {@code default} response carries a runtime-supplied status code. */
    public static boolean hasDynamicStatusCode(CodegenResponse response) {
        return response.isDefault || isRangeCode(response);
    }

    /** Whether an exact or range code is a {@code 2xx} success; the OpenAPI {@code default} response never is. */
    public static boolean isSuccessCode(CodegenResponse response) {
        if (response.isDefault) {
            return false;
        }
        var code = isRangeCode(response) ? rangeCodeLowerBound(response.code) : Integer.parseInt(response.code);
        return code >= 200 && code < 300;
    }

    protected static String toVarName(String s) {
        return new KoraCodegen().toVarName(s);
    }

    /**
     * Security scheme names such as {@code api-key} or {@code partner.token} are not valid identifiers,
     * so generated variables, parameters and methods use their sanitized form.
     */
    protected static String securitySchemeVarName(String securitySchemeName) {
        return SourceVersion.isIdentifier(securitySchemeName) && !SourceVersion.isKeyword(securitySchemeName) ? securitySchemeName : toVarName(securitySchemeName);
    }

    public TypeName asType(OperationsMap ctx, CodegenOperation operation, CodegenParameter param) {
        if (param.isBodyParam && isBareObject(param) && params.rawBodyMode != CodegenParams.RawBodyMode.OBJECT) {
            return requestBodyType();
        }
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
        throw new IllegalArgumentException("""
            Cannot resolve Java type for OpenAPI parameter `%s`.

            Parameter dataType: `%s`
            Parameter baseType: `%s`
            Location flags: path=%s, query=%s, header=%s, cookie=%s, form=%s, body=%s

            Fix: make sure the parameter schema has a supported OpenAPI type/format or references a generated model.
            """.formatted(param.paramName, param.dataType, param.baseType, param.isPathParam, param.isQueryParam, param.isHeaderParam, param.isCookieParam, param.isFormParam, param.isBodyParam));
    }

    private static boolean isScalar(IJsonSchemaValidationProperties schema) {
        return schema.getIsNumber() || schema.getIsInteger() || schema.getIsLong() || schema.getIsShort()
               || schema.getIsFloat() || schema.getIsDouble() || schema.getIsDecimal() || schema.getIsBoolean()
               || schema.getIsString() || schema.getIsUuid() || schema.getIsDate() || schema.getIsDateTime();
    }

    /**
     * A schema without a type at all — `additionalProperties: true` or an empty schema — which the
     * OpenAPI generator reports through the `AnyType` mapping.
     */
    private boolean isAnyType(IJsonSchemaValidationProperties schema) {
        var anyType = typeMapping == null ? null : typeMapping.get("AnyType");
        return Objects.equals(schema.getDataType(), Objects.requireNonNullElse(anyType, "oas_any_type_not_mapped"));
    }

    /**
     * `date-time` follows `typeMappings` and falls back to {@link OffsetDateTime}. The supported set is
     * the same one {@code KoraCodegen} already accepts when it renders default values for such fields.
     */
    private TypeName dateTimeType() {
        var mapped = typeMapping == null ? null : typeMapping.getOrDefault("date-time", typeMapping.get("DateTime"));
        if (mapped == null) {
            return ClassName.get(OffsetDateTime.class);
        }
        if (isMappedTo(mapped, Instant.class)) {
            return ClassName.get(Instant.class);
        }
        if (isMappedTo(mapped, ZonedDateTime.class)) {
            return ClassName.get(ZonedDateTime.class);
        }
        if (isMappedTo(mapped, LocalDateTime.class)) {
            return ClassName.get(LocalDateTime.class);
        }
        return ClassName.get(OffsetDateTime.class);
    }

    private static boolean isMappedTo(String mapped, Class<?> type) {
        return type.getSimpleName().equals(mapped) || type.getCanonicalName().equals(mapped);
    }

    /**
     * Length and pattern constraints are validated for strings only: a uuid, a date, a byte array or an enum
     * has no such validator, so the application graph could not be built.
     */
    protected boolean isValidatedAsString(IJsonSchemaValidationProperties schema) {
        return !schema.getIsEnum() && ClassName.get(String.class).equals(asType(schema));
    }

    /**
     * Warns that length and pattern constraints declared in the contract for a non-string type are not validated.
     *
     * @param owner where the property or parameter is declared, e.g. {@code model `Event`}
     */
    protected void warnIgnoredStringValidation(IJsonSchemaValidationProperties schema, String owner) {
        var constraints = new ArrayList<String>(3);
        if (schema.getMinLength() != null) {
            constraints.add("minLength");
        }
        if (schema.getMaxLength() != null) {
            constraints.add("maxLength");
        }
        if (schema.getPattern() != null) {
            constraints.add("pattern");
        }
        if (!params.enableValidation || constraints.isEmpty() || isValidatedAsString(schema)) {
            return;
        }
        var name = schema instanceof CodegenProperty p ? p.baseName : schema instanceof CodegenParameter p ? p.baseName : "";
        var type = schema.getIsEnum() ? "an enum" : asType(schema).toString();
        var message = "Validation constraints %s of `%s` in %s are ignored: they are validated for strings only, but the type is generated as %s. Remove them from the OpenAPI contract or validate the value in the application code."
            .formatted(String.join("/", constraints), name, owner, type);
        if (params.reportedWarnings.add(message)) {
            logger.warn(message);
        }
    }

    /**
     * Whether the items of an array or the values of a map (at any nesting depth) are models that have to be validated.
     */
    protected static boolean hasModelItems(IJsonSchemaValidationProperties schema) {
        var items = schema.getItems();
        return items != null && (items.getIsModel() || (items.getIsArray() || items.getIsMap()) && hasModelItems(items));
    }

    public TypeName asType(IJsonSchemaValidationProperties schema) {
        var type = schemaType(schema);
        // response bodies become type arguments of the response mappers and ApiResponses, which can not be primitives
        if (schema instanceof CodegenResponse) {
            return type.box();
        }
        return type;
    }

    private TypeName schemaType(IJsonSchemaValidationProperties schema) {
        if (schema instanceof CodegenResponse rs) {
            if (rs.isFile) {
                return ArrayTypeName.of(TypeName.BYTE);
            }
            if (isBareObject(rs) && params.rawBodyMode != CodegenParams.RawBodyMode.OBJECT) {
                return responseBodyType();
            }
            if (rs.isMap && rs.returnProperty != null && !isBareObject(rs)) {
                return asType(rs.returnProperty);
            }
        }
        if (schema.getIsModel() && schema instanceof CodegenModel c) {
            return ClassName.get(modelPackage, c.getClassname());
        }
        if (isAnyType(schema)) {
            // a type-less composed schema, e.g. `allOf: [{}, {description: ...}]`
            return ClassName.get(Object.class);
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
            if (schema.getAdditionalProperties() == null) {
                return ClassName.get(Object.class);
            }
            return ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), asType(schema.getAdditionalProperties()).box());
        }
        if (schema.getIsBinary() || schema.getIsByteArray()) {
            // Kotlin mode does not treat `byte[]` as a primitive, so a `format: byte` body is flagged as a model there
            return ArrayTypeName.of(TypeName.BYTE);
        }
        if (schema.getIsModel()) {
            if (schema.getDataType().contains(".")) {
                return ClassName.bestGuess(schema.getDataType());
            }
            // a top level scalar body is flagged as a model when its data type is not a language primitive
            if (!isScalar(schema)) {
                return ClassName.get(modelPackage, schema.getDataType());
            }
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
        if ("Object".equals(schema.getDataType()) || isAnyType(schema) || schema instanceof CodegenProperty p && p.isFreeFormObject) {
            return ClassName.get(Object.class);
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
            return dateTimeType();
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
        throw new IllegalArgumentException("""
            Cannot resolve Java type for OpenAPI schema.

            Schema dataType: `%s`
            Schema format: `%s`
            Schema ref: `%s`
            Schema flags: model=%s, array=%s, map=%s, enum=%s, string=%s, number=%s, boolean=%s

            Fix: use a supported OpenAPI schema type/format, add a `$ref` to a model schema, or adjust `rawBodyMode` for bare object bodies.
            """.formatted(
            schema.getDataType(),
            schema.getFormat(),
            schema.getRef(),
            schema.getIsModel(),
            schema.getIsArray(),
            schema.getIsMap(),
            schema.getIsEnum(),
            schema.getIsString(),
            schema.getIsNumber(),
            schema.getIsBoolean()
        ));
    }

    protected TypeName requestBodyType() {
        if (params.rawBodyMode == CodegenParams.RawBodyMode.BYTES) {
            return ArrayTypeName.of(TypeName.BYTE);
        }
        if (params.rawBodyMode == CodegenParams.RawBodyMode.OBJECT) {
            return ClassName.get(Object.class);
        }
        return params.codegenMode.isClient()
            ? Classes.httpBodyOutput
            : Classes.httpBodyInput;
    }

    protected TypeName responseBodyType() {
        if (params.rawBodyMode == CodegenParams.RawBodyMode.BYTES) {
            return ArrayTypeName.of(TypeName.BYTE);
        }
        if (params.rawBodyMode == CodegenParams.RawBodyMode.OBJECT) {
            return ClassName.get(Object.class);
        }
        return params.codegenMode.isClient()
            ? Classes.httpBodyInput
            : Classes.httpBodyOutput;
    }

    protected boolean isBareObject(IJsonSchemaValidationProperties schema) {
        if (schema instanceof CodegenResponse response) {
            // a response does not carry additionalProperties, so a map with typed values is told apart by isFreeFormObject
            return "Object".equals(response.dataType) || response.isFreeFormObject;
        }
        return "Object".equals(schema.getDataType())
               || schema.getIsMap() && schema.getAdditionalProperties() == null
               || schema instanceof CodegenProperty p && p.isFreeFormObject
               || schema instanceof CodegenParameter cp && cp.isFreeFormObject;
    }

    protected boolean requiresJsonMapper(IJsonSchemaValidationProperties schema) {
        return !isBareObject(schema) || params.rawBodyMode == CodegenParams.RawBodyMode.OBJECT;
    }

    /**
     * @return the request body's content type when it is a non-JSON, non-model binary or string body declared with a
     * content type other than the implicit default ({@code application/octet-stream} for binary, {@code text/plain}
     * for string), or {@code null} when the default/generic mapper is sufficient.
     */
    @Nullable
    protected static String customBodyContentType(@Nullable CodegenParameter bodyParam) {
        if (bodyParam == null || bodyParam.isModel) {
            return null;
        }
        if (bodyParam.isBinary) {
            return nonDefaultContentType(bodyParam.getContent(), "application/octet-stream");
        }
        if (bodyParam.isString && !KoraCodegen.isContentJson(bodyParam)) {
            return nonDefaultContentType(bodyParam.getContent(), "text/plain");
        }
        return null;
    }

    /**
     * @return true for a model, map or free-form object form part, or an array of them: such a part has no plain text form
     */
    public static boolean isStructuredFormPart(CodegenParameter p) {
        if (p.isArray) {
            // each element of an array part is converted on its own
            return p.items != null && (p.items.isModel || p.items.isMap || p.items.isFreeFormObject || p.items.isArray);
        }
        return p.isModel || p.isMap || p.isFreeFormObject;
    }

    /**
     * Explicit {@code encoding.explode} of a url-encoded form field: the parsed parameter can't tell an absent value from {@code false}
     */
    public static final String FORM_EXPLODE_EXTENSION = "x-kora-form-explode";

    /**
     * @return the delimiter an array field of a url-encoded form is joined with ({@code explode: false}),
     * or {@code null} when each value is a field of its own. {@code explode} defaults to true for the {@code form} style only
     */
    @Nullable
    public static String urlEncodedArrayDelimiter(CodegenParameter p) {
        var style = p.style == null ? "form" : p.style;
        var explode = p.vendorExtensions.get(FORM_EXPLODE_EXTENSION) instanceof Boolean explicit ? explicit : style.equals("form");
        if (explode) {
            return null;
        }
        return switch (style) {
            case "spaceDelimited" -> " ";
            case "pipeDelimited" -> "|";
            default -> ",";
        };
    }

    /**
     * A field of a url-encoded form without {@code encoding.contentType} is serialized with the {@code form} style,
     * so an object is not a JSON value but a field per property (`explode: true` is the default of the style).
     *
     * @return the model whose properties are the fields, or {@code null} when the field is not an object serialized with a style
     * @throws IllegalArgumentException when the style does not define a serialization of the field
     */
    @Nullable
    protected CodegenModel explodedFormObject(CodegenOperation operation, CodegenParameter p) {
        if (!isStructuredFormPart(p) || p.contentType != null && !p.contentType.isBlank() || KoraCodegen.isContentJson(p)) {
            return null;
        }
        if (params.urlEncodedFormObjectsAsJson) {
            // the option keeps an object a JSON value of a single field
            return null;
        }
        if (p.isArray) {
            throw new IllegalArgumentException(unsupportedFormObjectError(operation, p, "an array of objects has no `form` style serialization"));
        }
        var model = p.isModel ? formObjectModel(p) : null;
        if (model == null) {
            throw new IllegalArgumentException(unsupportedFormObjectError(operation, p, "a map or a free-form object has no fixed set of fields"));
        }
        if (Boolean.FALSE.equals(p.vendorExtensions.get(FORM_EXPLODE_EXTENSION)) || p.style != null && !p.style.equals("form")) {
            throw new IllegalArgumentException(unsupportedFormObjectError(operation, p, "only the `form` style with `explode: true` is supported for an object"));
        }
        if (model.getComposedSchemas() != null || model.discriminator != null) {
            throw new IllegalArgumentException(unsupportedFormObjectError(operation, p, "a composed (`oneOf`, `anyOf`, `allOf`) or a polymorphic object has no fixed set of fields"));
        }
        for (var property : model.allVars) {
            var value = property.isArray ? property.items : property;
            if (value == null || value.isArray || value.isModel || value.isMap || value.isFreeFormObject || value.isAnyType) {
                throw new IllegalArgumentException(unsupportedFormObjectError(operation, p, "property `%s` is not a scalar or an array of scalars".formatted(property.baseName)));
            }
        }
        for (var other : operation.formParams) {
            if (other == p) {
                continue;
            }
            var otherModel = isStructuredFormPart(other) && other.isModel && !other.isArray && (other.contentType == null || other.contentType.isBlank())
                ? formObjectModel(other)
                : null;
            for (var property : model.allVars) {
                var collides = otherModel == null
                    ? other.baseName.equals(property.baseName)
                    : otherModel.allVars.stream().anyMatch(otherProperty -> otherProperty.baseName.equals(property.baseName));
                if (collides) {
                    throw new IllegalArgumentException(unsupportedFormObjectError(operation, p, "property `%s` has the same field name as field `%s`".formatted(property.baseName, other.baseName)));
                }
            }
        }
        return model;
    }

    @Nullable
    private CodegenModel formObjectModel(CodegenParameter p) {
        for (var modelsMap : models.values()) {
            for (var modelMap : modelsMap.getModels()) {
                if (modelMap.getModel().classname.equals(p.dataType)) {
                    return modelMap.getModel();
                }
            }
        }
        return null;
    }

    private static String unsupportedFormObjectError(CodegenOperation operation, CodegenParameter p, String reason) {
        return """
            Unsupported OpenAPI form field `%s` in operation `%s`: %s.

            A field of an `application/x-www-form-urlencoded` body without `encoding.contentType` is serialized with the `form` style:
            an object is sent as a separate field per property, which is defined for an object with scalar and array of scalars properties only.

            Fix: declare `contentType: application/json` in the `encoding` of the field to send it as JSON, or describe the field as an object with scalar properties.
            The generator option `%s: true` sends every such object as JSON.
            """.formatted(p.baseName, operation.operationId, reason, CodegenParams.URL_ENCODED_FORM_OBJECTS_AS_JSON);
    }

    /**
     * @return the content type a form part is sent with: the declared {@code encoding.contentType} (a JSON one is preferred
     * when a list is declared), {@code application/json} for a structured part without an encoding, or {@code null} for a plain text part
     */
    @Nullable
    public static String formPartContentType(CodegenParameter p) {
        if (p.contentType != null && !p.contentType.isBlank()) {
            var contentTypes = p.contentType.split(",");
            for (var contentType : contentTypes) {
                if (isJsonMediaType(contentType)) {
                    return contentType.trim();
                }
            }
            return contentTypes[0].trim();
        }
        if (KoraCodegen.isContentJson(p) || isStructuredFormPart(p)) {
            return "application/json";
        }
        return null;
    }

    /**
     * @return true for {@code application/json}, {@code text/json} and a type with the {@code +json} structured syntax suffix (RFC 6839)
     */
    public static boolean isJsonMediaType(String mediaType) {
        var type = mediaTypeWithoutParameters(mediaType);
        return type.equals("application/json") || type.equals("text/json") || type.endsWith("+json");
    }

    /**
     * @return true when a form part is {@code application/json} or {@code text/json}, so it is converted with the {@code @Json} tagged converter
     */
    public static boolean isJsonFormPart(CodegenParameter p) {
        var contentType = formPartContentType(p);
        if (contentType == null) {
            return false;
        }
        var type = mediaTypeWithoutParameters(contentType);
        return type.equals("application/json") || type.equals("text/json");
    }

    /**
     * @return the name of the {@code ApiFormPartsModule} tag class of a form part converter, or {@code null} when the part has no tag of its own.
     * A part gets the tag of its media type when the type is JSON-like ({@code +json}) or when a structured part declares a non-JSON type
     */
    @Nullable
    public static String formPartTag(CodegenParameter p) {
        var contentType = formPartContentType(p);
        if (contentType == null || isJsonFormPart(p)) {
            return null;
        }
        if (!isJsonMediaType(contentType) && !isStructuredFormPart(p)) {
            // a scalar part with a declared text type is converted with a stock converter
            return null;
        }
        var tag = new StringBuilder();
        for (var word : mediaTypeWithoutParameters(contentType).split("[^a-z0-9]+")) {
            if (!word.isEmpty()) {
                tag.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
            }
        }
        return tag.toString();
    }

    /**
     * @return true when {@code ApiFormPartsModule} provides a default converter with the part's tag: a JSON-like type is converted as JSON,
     * while a converter of any other type has to be provided by an application
     */
    public static boolean hasDefaultFormPartConverter(CodegenParameter p) {
        var contentType = formPartContentType(p);
        return formPartTag(p) != null && contentType != null && isJsonMediaType(contentType);
    }

    /**
     * @return true when a non-binary form part declares a JSON media type, so it is converted as JSON whatever its type is, a string too
     */
    public static boolean isJsonTypedFormPart(CodegenParameter p) {
        var contentType = formPartContentType(p);
        if (contentType == null || !isJsonMediaType(contentType) || p.isFile) {
            return false;
        }
        var byteArray = "byte[]".equals(p.dataType) || "ByteArray".equals(p.dataType) || "byte[]".equals(p.baseType) || "ByteArray".equals(p.baseType)
            || p.dataType != null && (p.dataType.contains("byte[]") || p.dataType.contains("ByteArray"));
        return !byteArray;
    }

    private static String mediaTypeWithoutParameters(String mediaType) {
        var parametersStart = mediaType.indexOf(';');
        return (parametersStart < 0 ? mediaType : mediaType.substring(0, parametersStart)).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @return the response's content type when it is a non-JSON, non-binary string response declared with a content
     * type other than the implicit default ({@code text/plain}), or {@code null} otherwise. Binary responses already
     * carry their content type from {@link CodegenResponse#getContent()} directly.
     */
    @Nullable
    protected static String customResponseContentType(CodegenResponse response) {
        if (response.isBinary || response.dataType == null) {
            return null;
        }
        if (response.isString && !KoraCodegen.isContentJson(response.getContent())) {
            return nonDefaultContentType(response.getContent(), "text/plain");
        }
        return null;
    }

    @Nullable
    private static String nonDefaultContentType(@Nullable Map<String, CodegenMediaType> content, String defaultContentType) {
        var contentType = (content == null || content.isEmpty()) ? defaultContentType : content.keySet().iterator().next();
        return defaultContentType.equals(contentType) ? null : contentType;
    }

    protected boolean hasRawBodyHeaders(CodegenOperation operation) {
        for (var param : operation.allParams) {
            if (param.isBodyParam && (!KoraCodegen.isContentJson(param) || isBareObject(param) && params.rawBodyMode != CodegenParams.RawBodyMode.OBJECT)) {
                return true;
            }
        }
        return false;
    }
}
