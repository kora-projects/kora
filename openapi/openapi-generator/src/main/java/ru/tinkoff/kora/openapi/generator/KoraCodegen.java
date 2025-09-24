package ru.tinkoff.kora.openapi.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.ibm.icu.text.Transliterator;
import com.palantir.javapoet.JavaFile;
import com.samskivert.mustache.Mustache;
import com.squareup.kotlinpoet.FileSpec;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import jakarta.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.CamelizeOption;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.openapi.generator.javagen.*;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openapitools.codegen.utils.ModelUtils.getSchemaItems;
import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.escape;

@SuppressWarnings({"rawtypes", "unchecked"})
public class KoraCodegen extends DefaultCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(KoraCodegen.class);

    public record TagClient(@Nullable String httpClientTag, @Nullable String telemetryTag) {}

    public record Interceptor(@Nullable String type, @Nullable Object tag) {}

    public record AdditionalAnnotation(@Nullable String annotation) {}

    @Override
    public String getName() {
        return "kora";
    }


    private CodegenParams params;
    private final Map<String, ModelsMap> models = new HashMap<>();
    private final Map<String, OperationsMap> operationsByClassName = new HashMap<>();

    public KoraCodegen() {
        super();
        apiPackage = "org.openapitools.api";
        modelPackage = "org.openapitools.model";

        modifyFeatureSet(features -> features
            .wireFormatFeatures(EnumSet.of(WireFormatFeature.JSON))
            .securityFeatures(EnumSet.of(SecurityFeature.ApiKey, SecurityFeature.BasicAuth, SecurityFeature.BearerToken, SecurityFeature.OAuth2_AuthorizationCode))
            .excludeGlobalFeatures(
                GlobalFeature.XMLStructureDefinitions,
                GlobalFeature.Callbacks,
                GlobalFeature.LinkObjects,
                GlobalFeature.ParameterStyling
            )
            .excludeSchemaSupportFeatures(SchemaSupportFeature.Polymorphism)
            .includeClientModificationFeatures(ClientModificationFeature.BasePath)
        );

        supportsInheritance = true;
        hideGenerationTimestamp = true;

        setReservedWordsLowerCase(
            Arrays.asList(
                // special words
                "object",
                // used as internal variables, can collide with parameter names
                "error",
                "localVarPath", "localVarQueryParams", "localVarCollectionQueryParams",
                "localVarHeaderParams", "localVarCookieParams", "localVarFormParams", "localVarPostBody",
                "localVarAccepts", "localVarAccept", "localVarContentTypes",
                "localVarContentType", "localVarAuthNames", "localReturnType",
                //  "ApiClient", "ApiException", "ApiResponse", "Configuration", "StringUtil",

                // language reserved words
                "abstract", "continue", "for", "new", "switch", "assert",
                "default", "if", "package", "synchronized", "boolean", "do", "goto", "private",
                "this", "break", "double", "implements", "protected", "throw", "byte", "else",
                "import", "public", "throws", "case", "enum", "instanceof", "return", "transient",
                "catch", "extends", "int", "short", "try", "char", "final", "interface", "static",
                "void", "class", "finally", "long", "strictfp", "volatile", "const", "float",
                "native", "super", "while", "null")
        );

        cliOptions.add(new CliOption(CodegenConstants.INVOKER_PACKAGE, CodegenConstants.INVOKER_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.MODEL_PACKAGE, CodegenConstants.MODEL_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.API_PACKAGE, CodegenConstants.API_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.API_PACKAGE, CodegenConstants.API_PACKAGE_DESC));
        cliOptions.addAll(CodegenParams.cliOptions());
    }

    @Override
    public void processOpts() {
        super.processOpts();
        params = CodegenParams.parse(additionalProperties);
        switch (params.codegenMode) {
            case JAVA_CLIENT -> {
                modelTemplateFiles.put("javaModel.mustache", ".java");
                apiTemplateFiles.put("javaClientApi.mustache", ".java");
                apiTemplateFiles.put("javaApiResponses.mustache", "Responses.java");
                apiTemplateFiles.put("javaClientResponseMappers.mustache", "ClientResponseMappers.java");
                apiTemplateFiles.put("javaClientRequestMappers.mustache", "ClientRequestMappers.java");
            }
            case JAVA_SERVER -> {
                apiTemplateFiles.put("javaServerApi.mustache", "Controller.java");
                apiTemplateFiles.put("javaServerApiDelegate.mustache", "Delegate.java");
                apiTemplateFiles.put("javaApiResponses.mustache", "Responses.java");
                apiTemplateFiles.put("javaServerRequestMappers.mustache", "ServerRequestMappers.java");
                apiTemplateFiles.put("javaServerResponseMappers.mustache", "ServerResponseMappers.java");
                modelTemplateFiles.put("javaModel.mustache", ".java");

                if (params.delegateMethodBodyMode != DelegateMethodBodyMode.NONE) {
                    apiTemplateFiles.put("javaServerApiModule.mustache", "Module.java");
                }
            }
            case KOTLIN_CLIENT -> {
                modelTemplateFiles.put("kotlinModel.mustache", ".kt");
                apiTemplateFiles.put("kotlinClientApi.mustache", ".kt");
                apiTemplateFiles.put("kotlinApiResponses.mustache", "Responses.kt");
                apiTemplateFiles.put("kotlinClientResponseMappers.mustache", "ClientResponseMappers.kt");
                apiTemplateFiles.put("kotlinClientRequestMappers.mustache", "ClientRequestMappers.kt");
            }
            case KOTLIN_SERVER -> {
                modelTemplateFiles.put("kotlinModel.mustache", ".kt");
                apiTemplateFiles.put("kotlinServerApi.mustache", "Controller.kt");
                apiTemplateFiles.put("kotlinServerApiDelegate.mustache", "Delegate.kt");
                apiTemplateFiles.put("kotlinApiResponses.mustache", "Responses.kt");
                apiTemplateFiles.put("kotlinServerRequestMappers.mustache", "ServerRequestMappers.kt");
                apiTemplateFiles.put("kotlinServerResponseMappers.mustache", "ServerResponseMappers.kt");

                if (params.delegateMethodBodyMode != DelegateMethodBodyMode.NONE) {
                    apiTemplateFiles.put("kotlinServerApiModule.mustache", "Module.kt");
                }
            }
        }
        embeddedTemplateDir = templateDir = "openapi/templates/kora";
        if (!params.codegenMode.isJava()) {
            languageSpecificPrimitives = new HashSet<>(
                Arrays.asList(
                    "ByteArray",
                    "String",
                    "boolean",
                    "Boolean",
                    "Double",
                    "Int",
                    "Long",
                    "Float",
                    "Object"
                )
            );
        } else {
            languageSpecificPrimitives = new HashSet<>(
                Arrays.asList(
                    "String",
                    "boolean",
                    "Boolean",
                    "double",
                    "Double",
                    "int",
                    "Integer",
                    "long",
                    "Long",
                    "float",
                    "Float",
                    "Object",
                    "byte[]")
            );
        }

        if (!additionalProperties.containsKey(CodegenConstants.MODEL_PACKAGE)) {
            additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);
        }

        if (!additionalProperties.containsKey(CodegenConstants.API_PACKAGE)) {
            additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);
        }

        this.sanitizeConfig();
    }

    private String getUpperSnakeCase(String value, Locale locale) {
        return Arrays.stream(value.split("[^a-zA-Z0-9]"))
            .map(String::strip)
            .flatMap(s -> Arrays.stream(s.split("(?<=[\\d+])(?=[A-Za-z])|(?<=[a-z])(?=[A-Z\\d])|(?<=[A-Z])(?=[A-Z][a-z])|( +)"))) // too much, don't guarantee how it works
            .map(String::strip)
            .map(s -> s.toUpperCase(locale))
            .collect(Collectors.joining("_"));
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        objs = super.postProcessAllModels(objs);
        objs = updateAllModels(objs);
        for (var obj : objs.values()) {
            var model = (Map<String, Object>) obj;
            var models = (List<Map<String, Object>>) model.get("models");
            var codegenModel = (CodegenModel) models.get(0).get("model");
            var additionalConstructor = codegenModel.getHasVars()
                && !codegenModel.getVars().isEmpty()
                && !codegenModel.getAllVars().isEmpty()
                && codegenModel.getAllVars().size() != codegenModel.getRequiredVars().size();
            for (var requiredVar : codegenModel.requiredVars) {
                // discriminator is somehow present in both optional and required vars, so we should clean it up
                codegenModel.optionalVars.removeIf(p -> Objects.equals(p.name, requiredVar.name));
                if (codegenModel.parentModel != null && codegenModel.parentModel.discriminator != null) {
                    if (Objects.equals(requiredVar.name, codegenModel.parentModel.discriminator.getPropertyName())) {
                        requiredVar.isOverridden = true;
                    }
                }
            }
            model.put("additionalConstructor", additionalConstructor);
        }

        this.models.putAll(objs);
        return objs;
    }

    private void sanitizeConfig() {
        // Sanitize any config options here. We also have to update the additionalProperties because
        // the whole additionalProperties object is injected into the main object passed to the mustache layer

        this.setApiPackage(sanitizePackageName(apiPackage));
        if (additionalProperties.containsKey(CodegenConstants.API_PACKAGE)) {
            this.additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);
        }

        this.setModelPackage(sanitizePackageName(modelPackage));
        if (additionalProperties.containsKey(CodegenConstants.MODEL_PACKAGE)) {
            this.additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);
        }
    }

    @Override
    public String escapeReservedWord(String name) {
        if (this.reservedWordsMappings().containsKey(name)) {
            return this.reservedWordsMappings().get(name);
        }
        return "_" + name;
    }

    @Override
    public String apiFileFolder() {
        return (outputFolder + File.separator + apiPackage().replace('.', File.separatorChar)).replace('/', File.separatorChar);
    }

    @Override
    public String apiTestFileFolder() {
        return (outputFolder + File.separator + apiPackage().replace('.', File.separatorChar)).replace('/', File.separatorChar);
    }

    @Override
    public String modelTestFileFolder() {
        return (outputFolder + File.separator + modelPackage().replace('.', File.separatorChar)).replace('/', File.separatorChar);
    }

    @Override
    public String modelFileFolder() {
        return (outputFolder + File.separator + modelPackage().replace('.', File.separatorChar)).replace('/', File.separatorChar);
    }

    @Override
    public String apiDocFileFolder() {
        return outputFolder.replace('/', File.separatorChar);
    }

    @Override
    public String modelDocFileFolder() {
        return outputFolder.replace('/', File.separatorChar);
    }

    @Override
    public String toApiDocFilename(String name) {
        return toApiName(name);
    }

    @Override
    public String toModelDocFilename(String name) {
        return toModelName(name);
    }

    @Override
    public String toApiTestFilename(String name) {
        return toApiName(name) + "Test";
    }

    @Override
    public String toModelTestFilename(String name) {
        return toModelName(name) + "Test";
    }

    @Override
    public String toApiFilename(String name) {
        return toApiName(name);
    }

    @Override
    public String toVarName(String name) {
        // sanitize name
        name = sanitizeName(name, "\\W-[\\$]"); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.

        if (name.toLowerCase(Locale.ROOT).matches("^_*class$")) {
            return "propertyClass";
        }

        if ("_".equals(name)) {
            name = "_u";
        }

        // numbers are not allowed at the beginning
        if (name.matches("^\\d.*")) {
            name = "_" + name;
        }

        // if it's all uppper case, do nothing
        if (name.matches("^[A-Z0-9_]*$")) {
            return name;
        }

        if (startsWithTwoUppercaseLetters(name)) {
            name = name.substring(0, 2).toLowerCase(Locale.ROOT) + name.substring(2);
        }

        // If name contains special chars -> replace them.
        if (name.chars().anyMatch(character -> specialCharReplacements.keySet().contains("" + ((char) character)))) {
            var allowedCharacters = new ArrayList<String>();
            allowedCharacters.add("_");
            allowedCharacters.add("$");
            name = escape(name, specialCharReplacements, allowedCharacters, "_");
        }

        // camelize (lower first character) the variable name
        // pet_id => petId
        name = camelize(name, CamelizeOption.LOWERCASE_FIRST_CHAR);

        // for reserved word or word starting with number, append _
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }

        return name;
    }

    private boolean startsWithTwoUppercaseLetters(String name) {
        boolean startsWithTwoUppercaseLetters = false;
        if (name.length() > 1) {
            startsWithTwoUppercaseLetters = name.substring(0, 2).equals(name.substring(0, 2).toUpperCase(Locale.ROOT));
        }
        return startsWithTwoUppercaseLetters;
    }

    @Override
    public String toParamName(String name) {
        // to avoid conflicts with 'callback' parameter for async call
        if ("callback".equals(name)) {
            return "paramCallback";
        }

        // should be the same as variable name
        return toVarName(name);
    }

    @Override
    public String toModelName(final String name) {
        // We need to check if import-mapping has a different model for this class, so we use it
        // instead of the auto-generated one.
        if (importMapping.containsKey(name)) {
            return importMapping.get(name);
        }

        var nameWithPrefixSuffix = sanitizeName(name);
        if (!StringUtils.isEmpty(modelNamePrefix)) {
            // add '_' so that model name can be camelized correctly
            nameWithPrefixSuffix = modelNamePrefix + "_" + nameWithPrefixSuffix;
        }

        if (!StringUtils.isEmpty(modelNameSuffix)) {
            // add '_' so that model name can be camelized correctly
            nameWithPrefixSuffix = nameWithPrefixSuffix + "_" + modelNameSuffix;
        }

        // camelize the model name
        // phone_number => PhoneNumber
        final var camelizedName = camelize(nameWithPrefixSuffix);

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(camelizedName)) {
            final String modelName = "Model" + camelizedName;
//            LOGGER.warn("{} (reserved word) cannot be used as model name. Renamed to {}", camelizedName, modelName);
            return modelName;
        }

        // model name starts with number
        if (camelizedName.matches("^\\d.*")) {
            final String modelName = "Model" + camelizedName; // e.g. 200Response => Model200Response (after camelize)
//            LOGGER.warn("{} (model name starts with number) cannot be used as model name. Renamed to {}", name,
//                modelName);
            return modelName;
        }

        return camelizedName;
    }

    @Override
    public String toModelFilename(String name) {
        // should be the same as the model name
        return toModelName(name);
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        var schema = ModelUtils.unaliasSchema(this.openAPI, p, importMapping);
        var target = ModelUtils.isGenerateAliasAsModel() ? p : schema;
        if (ModelUtils.isArraySchema(target)) {
            var items = getSchemaItems(schema);
            return getSchemaType(target) + "<" + getTypeDeclaration(items) + ">";
        } else if (ModelUtils.isMapSchema(target)) {
            // Note: ModelUtils.isMapSchema(p) returns true when p is a composed schema that also defines
            // additionalproperties: true
            var inner = ModelUtils.getAdditionalProperties(target);
            if (inner == null) {
                LOGGER.error("`{}` (map property) does not have a proper inner type defined. Default to type:string", p.getName());
                inner = new StringSchema().description("TODO default missing map inner type to string");
                p.setAdditionalProperties(inner);
            }
            return getSchemaType(target) + "<String, " + getTypeDeclaration(inner) + ">";
        }
        return super.getTypeDeclaration(target);
    }

    /**
     * Same as original, but have different mapping for Map of String to T
     */
    public String getTypeDeclarationAndProp(Schema p, CodegenProperty property) {
        var schema = ModelUtils.unaliasSchema(this.openAPI, p, importMapping);
        var target = ModelUtils.isGenerateAliasAsModel() ? p : schema;
        if (ModelUtils.isMapSchema(target)) {
            // Note: ModelUtils.isMapSchema(p) returns true when p is a composed schema that also defines
            // additionalproperties: true
            var inner = ModelUtils.getAdditionalProperties(target);
            if (inner == null) {
                LOGGER.error("`{}` (map property) does not have a proper inner type defined. Default to type:string", p.getName());
                inner = new StringSchema().description("TODO default missing map inner type to string");
                p.setAdditionalProperties(inner);
            }

            if (params.codegenMode.isKotlin() && property.isNullable) {
                if (property.required) {
                    return getSchemaType(target) + "<String, ru.tinkoff.kora.json.common.JsonNullable<" + getTypeDeclaration(inner) + ">>";
                } else {
                    return getSchemaType(target) + "<String, ru.tinkoff.kora.json.common.JsonNullable<" + getTypeDeclaration(inner) + ">>?";
                }
            } else if (property.isNullable && !property.required) {
                return getSchemaType(target) + "<String, ru.tinkoff.kora.json.common.JsonNullable<" + getTypeDeclaration(inner) + ">>";
            } else {
                return getSchemaType(target) + "<String, " + getTypeDeclaration(inner) + ">";
            }
        }

        return super.getTypeDeclaration(target);
    }

    @Override
    public CodegenProperty fromProperty(String name, Schema p, boolean required, boolean schemaIsFromAdditionalProperties) {
        var property = super.fromProperty(name, p, required, schemaIsFromAdditionalProperties);
        var schema = ModelUtils.unaliasSchema(this.openAPI, p, importMapping);
        var target = ModelUtils.isGenerateAliasAsModel() ? p : schema;
        if (ModelUtils.isMapSchema(target)) {
            var dataType = getTypeDeclarationAndProp(p, property);
            property.dataType = dataType;
            if (!property.isEnum) {
                property.datatypeWithEnum = property.dataType;
            }
        }
        return property;
    }

    @Override
    public String getAlias(String name) {
        if (typeAliases != null && typeAliases.containsKey(name)) {
            return typeAliases.get(name);
        }
        return name;
    }

    @Override
    public String toDefaultValue(Schema originalSchema) {
        Schema schema = ModelUtils.getReferencedSchema(this.openAPI, originalSchema);
        Object defaultValue = schema.getDefault();
        return toDefaultSchemaValue(originalSchema, schema, defaultValue);
    }

    private String toDefaultSchemaValue(Schema originalSchema, Schema schema, Object defaultValue) {
        if (ModelUtils.isArraySchema(schema)) {
            Object def = defaultValue;
            if (!(def instanceof ArrayNode an)) {
                return null;
            }

            final String pattern;
            if (ModelUtils.isSet(schema)) {
                pattern = params.codegenMode.isKotlin()
                    ? "setOf("
                    : "java.util.Set.of(";
            } else {
                pattern = params.codegenMode.isKotlin()
                    ? "listOf("
                    : "java.util.List.of(";
            }

            Schema<?> itemOriginal = getSchemaItems(schema);
            Schema itemSchema = ModelUtils.getReferencedSchema(this.openAPI, itemOriginal);
            var builder = new StringBuilder(String.format(Locale.ROOT, pattern));

            int items = 0;
            int i = 1;
            for (JsonNode node : an) {
                String itemValue = toDefaultSchemaValue(itemOriginal, itemSchema, node.asText());
                if (itemValue != null) {
                    builder.append(itemValue);
                    items++;
                    if (i != an.size()) {
                        builder.append(", ");
                        i++;
                    }
                }
            }

            if (items == 0) {
                return null;
            }

            return builder.append(")").toString();
        } else if (ModelUtils.isMapSchema(schema) && !(schema instanceof ComposedSchema)) {
            if (defaultValue == null) {
                return null;
            }

            if (schema.getProperties() != null && schema.getProperties().size() > 0) {
                // object is complex object with free-form additional properties
                if (defaultValue != null) {
                    return super.toDefaultValue(schema);
                }
                return null;
            }

            String mapInstantiationType = instantiationTypes().getOrDefault("map", "HashMap");
            final String pattern = "new " + mapInstantiationType + "<%s>()";

            Schema schemaProperties = ModelUtils.getAdditionalProperties(schema);
            if (schemaProperties == null) {
                return null;
            }

            String typeDeclaration = String.format(Locale.ROOT, "String, %s", getTypeDeclaration(schemaProperties));
            Object java8obj = additionalProperties.get("java8");
            if (java8obj != null) {
                Boolean java8 = Boolean.valueOf(java8obj.toString());
                if (java8 != null && java8) {
                    typeDeclaration = "";
                }
            }

            return String.format(Locale.ROOT, pattern, typeDeclaration);
        } else if (ModelUtils.isIntegerSchema(schema)) {
            if (defaultValue != null) {
                if (SchemaTypeUtil.INTEGER64_FORMAT.equals(schema.getFormat())) {
                    return defaultValue + "L";
                } else {
                    return defaultValue.toString();
                }
            }
            return null;
        } else if (ModelUtils.isNumberSchema(schema)) {
            if (defaultValue != null) {
                if (SchemaTypeUtil.FLOAT_FORMAT.equals(schema.getFormat())) {
                    return defaultValue + "f";
                } else if (SchemaTypeUtil.DOUBLE_FORMAT.equals(schema.getFormat())) {
                    return (params.codegenMode.isKotlin())
                        ? defaultValue.toString()
                        : defaultValue + "d";
                } else {
                    return params.codegenMode.isKotlin()
                        ? "BigDecimal(\"" + defaultValue + "\")"
                        : "new BigDecimal(\"" + defaultValue + "\")";
                }
            }
            return null;
        } else if (ModelUtils.isBooleanSchema(schema)) {
            if (defaultValue != null) {
                return defaultValue.toString();
            }
            return null;
        } else if (ModelUtils.isURISchema(schema)) {
            if (defaultValue != null) {
                String uriValue = escapeText((String) defaultValue);
                return "java.net.URI.create(\"" + uriValue + "\")";
            }
            return null;
        } else if (ModelUtils.isStringSchema(schema)) {
            if (defaultValue != null) {
                String _default;
                if (defaultValue instanceof Date date) {
                    var dateDef = LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE);
                    return "java.time.LocalDate.parse(\"" + dateDef + "\", java.time.format.DateTimeFormatter.ISO_DATE)";
                } else if (ModelUtils.isDateSchema(schema) && defaultValue instanceof String ds) {
                    return "java.time.LocalDate.parse(\"" + ds + "\", java.time.format.DateTimeFormatter.ISO_DATE)";
                } else if (ModelUtils.isDateSchema(schema) && defaultValue instanceof Number dn) {
                    return "java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(" + dn.longValue() + "L), java.time.ZoneId.systemDefault())";
                } else if (defaultValue instanceof OffsetDateTime || ModelUtils.isDateTimeSchema(schema)) {
                    final String def;
                    if (defaultValue instanceof OffsetDateTime offsetDateTime) {
                        String dateTimeValue = String.format(Locale.ROOT, offsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), "");
                        def = "java.time.OffsetDateTime.parse(\"" + dateTimeValue + "\", java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)";
                    } else if (defaultValue instanceof String ds) {
                        def = "java.time.OffsetDateTime.parse(\"" + ds + "\", java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)";
                    } else if (defaultValue instanceof Number dn) {
                        def = "java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(" + dn.longValue() + "L), java.time.ZoneId.systemDefault())";
                    } else {
                        return null;
                    }

                    var dateTimeFormat = typeMapping.getOrDefault("date-time", typeMapping.get("DateTime"));
                    if (OffsetDateTime.class.getCanonicalName().equals(dateTimeFormat) || OffsetDateTime.class.getSimpleName().equals(dateTimeFormat)) {
                        return def;
                    } else if (Instant.class.getCanonicalName().equals(dateTimeFormat) || Instant.class.getSimpleName().equals(dateTimeFormat)) {
                        return def + ".toInstant()";
                    } else if (ZonedDateTime.class.getCanonicalName().equals(dateTimeFormat) || ZonedDateTime.class.getSimpleName().equals(dateTimeFormat)) {
                        return def + ".toZonedDateTime()";
                    } else if (LocalDateTime.class.getCanonicalName().equals(dateTimeFormat) || LocalDateTime.class.getSimpleName().equals(dateTimeFormat)) {
                        return def + ".toLocalDateTime()";
                    } else {
                        return null;
                    }
                } else if (defaultValue instanceof byte[] vb) {
                    _default = new String(vb);
                } else {
                    _default = (String) defaultValue;
                }

                if (schema.getEnum() == null) {
                    return "\"" + escapeText(_default) + "\"";
                } else {
                    // don't have schema model
                    if (originalSchema == schema) {
                        return "\"" + escapeText(_default) + "\"";
                    }

                    // convert to enum var name later in postProcessModels
                    return toModelName(ModelUtils.getSimpleRef(originalSchema.get$ref())) + "." + toEnumVarName(_default, schema.getType());
                }
            }
            return null;
        } else if (ModelUtils.isObjectSchema(schema)) {
            if (defaultValue != null) {
                return super.toDefaultValue(schema);
            }
            return null;
        } else if (ModelUtils.isComposedSchema(schema)) {
            if (defaultValue != null) {
                return super.toDefaultValue(schema);
            }
            return null;
        }

        return super.toDefaultValue(schema);
    }

    @Override
    public String toDefaultParameterValue(final Schema<?> schema) {
        Object defaultValue = schema.getDefault();
        if (defaultValue == null) {
            return null;
        }
        return toDefaultValue(schema);
    }

    /**
     * Return the example value of the parameter. Overrides the
     * setParameterExampleValue(CodegenParameter, Parameter) method in
     * DefaultCodegen to always call setParameterExampleValue(CodegenParameter)
     * in this class, which adds single quotes around strings from the
     * x-example property.
     *
     * @param codegenParameter Codegen parameter
     * @param parameter        Parameter
     */
    @Override
    public void setParameterExampleValue(CodegenParameter codegenParameter, Parameter parameter) {
        if (parameter.getExample() != null) {
            codegenParameter.example = parameter.getExample().toString();
        }

        if (parameter.getExamples() != null && !parameter.getExamples().isEmpty()) {
            Example example = parameter.getExamples().values().iterator().next();
            if (example.getValue() != null) {
                codegenParameter.example = example.getValue().toString();
            }
        }

        var schema = parameter.getSchema();
        if (schema != null && schema.getExample() != null) {
            codegenParameter.example = schema.getExample().toString();
        }

        setParameterExampleValue(codegenParameter);
    }

    /**
     * Return the example value of the parameter. Overrides the parent method in DefaultCodegen
     * to not set examples on complex models, as they don't compile properly.
     *
     * @param codegenParameter Codegen parameter
     * @param requestBody      Request body
     */
    @Override
    public void setParameterExampleValue(CodegenParameter codegenParameter, RequestBody requestBody) {
        var isModel = (codegenParameter.isModel || (codegenParameter.isContainer && codegenParameter.getItems().isModel));

        var content = requestBody.getContent();

        if (content.size() > 1) {
            // @see ModelUtils.getSchemaFromContent()
            LOGGER.warn("Multiple MediaTypes found, using only the first one");
        }

        var mediaType = content.values().iterator().next();
        if (mediaType.getExample() != null) {
            if (isModel) {
                LOGGER.warn("Ignoring complex example on request body");
            } else {
                codegenParameter.example = mediaType.getExample().toString();
                return;
            }
        }

        if (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty()) {
            Example example = mediaType.getExamples().values().iterator().next();
            if (example.getValue() != null) {
                if (isModel) {
                    LOGGER.warn("Ignoring complex example on request body");
                } else {
                    codegenParameter.example = example.getValue().toString();
                    return;
                }
            }
        }

        setParameterExampleValue(codegenParameter);
    }

    @Override
    public void setParameterExampleValue(CodegenParameter p) {
        String example;

        if (p.defaultValue == null) {
            example = p.example;
        } else {
            example = p.defaultValue;
        }

        String type = p.baseType;
        if (type == null) {
            type = p.dataType;
        }

        if ("String".equals(type)) {
            if (example == null) {
                example = p.paramName + "_example";
            }
            example = "\"" + escapeText(example) + "\"";
        } else if ("Integer".equals(type) || "Short".equals(type)) {
            if (example == null) {
                example = "56";
            }
        } else if ("Long".equals(type)) {
            if (example == null) {
                example = "56";
            }
            example = StringUtils.appendIfMissingIgnoreCase(example, "L");
        } else if ("Float".equals(type)) {
            if (example == null) {
                example = "3.4";
            }
            example = StringUtils.appendIfMissingIgnoreCase(example, "F");
        } else if ("Double".equals(type)) {
            if (example == null) {
                example = "3.4";
            }
            example = StringUtils.appendIfMissingIgnoreCase(example, "D");
        } else if ("Boolean".equals(type)) {
            if (example == null) {
                example = "true";
            }
        } else if ("File".equals(type)) {
            if (example == null) {
                example = "/path/to/file";
            }
            example = "new File(\"" + escapeText(example) + "\")";
        } else if ("Date".equals(type)) {
            example = "new Date()";
        } else if ("OffsetDateTime".equals(type)) {
            example = "OffsetDateTime.now()";
        } else if ("BigDecimal".equals(type)) {
            example = "new BigDecimal(78)";
        } else if (p.allowableValues != null && !p.allowableValues.isEmpty()) {
            Map<String, Object> allowableValues = p.allowableValues;
            List<Object> values = (List<Object>) allowableValues.get("values");
            example = type + ".fromValue(\"" + String.valueOf(values.get(0)) + "\")";
        } else if (!languageSpecificPrimitives.contains(type)) {
            // type is a model class, e.g. User
            example = "new " + type + "()";
        }

        if (example == null) {
            example = "null";
        } else if (Boolean.TRUE.equals(p.isArray)) {

            if (p.items.defaultValue != null) {
                String innerExample;
                if ("String".equals(p.items.dataType)) {
                    innerExample = "\"" + p.items.defaultValue + "\"";
                } else {
                    innerExample = p.items.defaultValue;
                }
                example = "Arrays.asList(" + innerExample + ")";
            } else {
                example = "Arrays.asList()";
            }
        } else if (Boolean.TRUE.equals(p.isMap)) {
            example = "new HashMap()";
        }

        p.example = example;
    }

    @Override
    public String toExampleValue(Schema p) {
        if (p.getExample() != null) {
            return escapeText(p.getExample().toString());
        } else {
            return null;
        }
    }

    @Override
    public String getSchemaType(Schema p) {
        String openAPIType = super.getSchemaType(p);

        // don't apply renaming on types from the typeMapping
        if (typeMapping.containsKey(openAPIType)) {
            return typeMapping.get(openAPIType);
        }

        if (null == openAPIType) {
            LOGGER.error("No Type defined for Schema {}", p);
        }
        return toModelName(openAPIType);
    }

    @Override
    public String toOperationId(String operationId) {
        // throw exception if method name is empty
        if (StringUtils.isEmpty(operationId)) {
            throw new RuntimeException("Empty method/operation name (operationId) not allowed");
        }

        operationId = camelize(sanitizeName(operationId), CamelizeOption.LOWERCASE_FIRST_CHAR);

        // method name cannot use reserved keyword, e.g. return
        if (isReservedWord(operationId)) {
            String newOperationId = camelize("call_" + operationId, CamelizeOption.LOWERCASE_FIRST_CHAR);
            LOGGER.warn("{} (reserved word) cannot be used as method name. Renamed to {}", operationId, newOperationId);
            return newOperationId;
        }

        // operationId starts with a number
        if (operationId.matches("^\\d.*")) {
            LOGGER.warn(operationId + " (starting with a number) cannot be used as method name. Renamed to " + camelize("call_" + operationId), true);
            operationId = camelize("call_" + operationId, CamelizeOption.LOWERCASE_FIRST_CHAR);
        }

        return operationId;
    }

    @Override
    public CodegenModel fromModel(String name, Schema model) {
        var allDefinitions = ModelUtils.getSchemas(this.openAPI);
        CodegenModel codegenModel = super.fromModel(name, model);
        if (codegenModel.description != null) {
            codegenModel.imports.add("ApiModel");
        }
        if (allDefinitions != null && codegenModel.parentSchema != null && codegenModel.hasEnums) {
            final var parentModel = allDefinitions.get(codegenModel.parentSchema);
            final CodegenModel parentCodegenModel = super.fromModel(codegenModel.parent, parentModel);
            codegenModel = reconcileInlineEnums(codegenModel, parentCodegenModel);
        }
        if ("BigDecimal".equals(codegenModel.dataType)) {
            codegenModel.imports.add("BigDecimal");
        }
        if (model.getOneOf() != null) {
            // I don't care what DefaultCodegen devs think about it
            if (model.getProperties() == null || model.getProperties().isEmpty()) {
                codegenModel.vars.clear();
                codegenModel.allVars.clear();
                codegenModel.requiredVars.clear();
                codegenModel.optionalVars.clear();
            } else {
                codegenModel.vars.removeIf(p -> !model.getProperties().containsKey(p.name));
                codegenModel.allVars.removeIf(p -> !model.getProperties().containsKey(p.name));
                codegenModel.requiredVars.removeIf(p -> !model.getProperties().containsKey(p.name));
                codegenModel.optionalVars.removeIf(p -> !model.getProperties().containsKey(p.name));
            }
            codegenModel.hasVars = !codegenModel.vars.isEmpty();
            codegenModel.hasOptional = !codegenModel.optionalVars.isEmpty();
            codegenModel.hasRequired = !codegenModel.requiredVars.isEmpty();
        }
        return codegenModel;
    }

    private void setFilteredSchemaComponentsIfEnabled(OpenAPI openAPI) {
        if (!this.params.filterWithModels) {
            return;
        }
        if (!this.openapiNormalizer.containsKey("FILTER")) {
            return;
        }

        List<Operation> ops = openAPI.getPaths().entrySet().stream()
            .flatMap(e -> e.getValue().readOperations().stream())
            .filter(op -> op.getExtensions() != null && String.valueOf(op.getExtensions().get("x-internal")).equals("false"))
            .toList();

        if (ops.isEmpty()) {
            return;
        }

        List<String> requestSchemas = ops.stream()
            .filter(op -> op.getRequestBody() != null && op.getRequestBody().getContent() != null)
            .flatMap(op -> op.getRequestBody().getContent().values().stream())
            .filter(req -> req.getSchema() != null)
            .flatMap(req -> getAllRefs(req.getSchema()).stream())
            .filter(Objects::nonNull)
            .filter(o -> !o.isBlank())
            .map(ModelUtils::getSimpleRef)
            .toList();

        List<String> responseSchemas = ops.stream()
            .flatMap(op -> op.getResponses().values().stream())
            .filter(res -> res.getContent() != null)
            .flatMap(res -> res.getContent().values().stream())
            .filter(res -> res.getSchema() != null)
            .flatMap(req -> getAllRefs(req.getSchema()).stream())
            .filter(Objects::nonNull)
            .filter(o -> !o.isBlank())
            .map(ModelUtils::getSimpleRef)
            .toList();

        Set<String> rootSchemas = Stream.concat(requestSchemas.stream(), responseSchemas.stream())
            .collect(Collectors.toUnmodifiableSet());

        Set<String> filteredToSaveSchemas = new HashSet<>(rootSchemas);
        Map<String, Schema> schemas = ModelUtils.getSchemas(openAPI);

        schemas.forEach((name, schema) -> {
            if (rootSchemas.contains(name)) {
                Set<String> refs = getSimpleRefRecursive(name, schema, schemas, new HashMap<>(), new HashSet<>(), true);
                filteredToSaveSchemas.addAll(refs);
            }
        });

        schemas.forEach((name, schema) -> {
            if (!filteredToSaveSchemas.contains(name)) {
                // if x-internal=true then model WILL NOT be generated by openapi generator
                schema.addExtension("x-internal", true);
            } else {
                schema.addExtension("x-internal", false);
            }
        });
    }

    private Set<String> getSimpleRefRecursive(String targetName,
                                              Schema<?> targetSchema,
                                              Map<String, Schema> schemas,
                                              Map<String, Set<String>> schemaToAllSimpleRefs,
                                              Set<String> visited,
                                              boolean checkVisited) {
        if (checkVisited) {
            if (visited.contains(targetName)) {
                return Collections.emptySet();
            } else {
                visited.add(targetName);
            }
        }

        final Set<String> simpleRefs = new HashSet<>();
        simpleRefs.add(targetName);

        Set<String> targetAllRefs = getAllSimpleRefs(targetSchema);
        simpleRefs.addAll(targetAllRefs);

        if (targetSchema.getDiscriminator() != null) {
            Map<String, String> mapping = targetSchema.getDiscriminator().getMapping();
            if (mapping != null) {
                Set<String> schemaRefs = mapping.values().stream()
                    .map(ModelUtils::getSimpleRef)
                    .collect(Collectors.toSet());

                List<Schema> schemaMappings = schemaRefs.stream()
                    .map(schemas::get)
                    .toList();

                for (Schema schemaMapping : schemaMappings) {
                    Set<String> itemRefs = getSimpleRefFromSchemaRef(schemaMapping, schemas, schemaToAllSimpleRefs, visited);
                    simpleRefs.addAll(itemRefs);

                    Set<String> allRefs = getAllSimpleRefs(schemaMapping);
                    simpleRefs.addAll(allRefs);
                }
                simpleRefs.addAll(schemaRefs);
            }

            // getting from  targetSchema.getDiscriminator().getMapping() may work only when its specified and won't consider other cases
            schemas.forEach((candidateName, candidateSchema) -> {
                if (candidateSchema != targetSchema) {
                    Set<String> allSimpleRefs = schemaToAllSimpleRefs.computeIfAbsent(candidateName, k -> getAllSimpleRefs(candidateSchema));

                    if (allSimpleRefs.contains(targetName)) {
                        simpleRefs.add(candidateName);
                        for (String discRef : allSimpleRefs) {
                            Schema discSchema = schemas.get(discRef);
                            Set<String> itemRefs = getSimpleRefFromSchemaRef(discSchema, schemas, schemaToAllSimpleRefs, visited);
                            simpleRefs.addAll(itemRefs);

                            Set<String> propAllRefs = getAllSimpleRefs(discSchema);
                            simpleRefs.addAll(propAllRefs);
                        }
                        simpleRefs.addAll(allSimpleRefs);
                    }
                }
            });
        }

        if (targetSchema.getProperties() != null) {
            targetSchema.getProperties().forEach((propName, prop) -> {
                Set<String> propRefs = getSimpleRefFromSchemaRef(prop, schemas, schemaToAllSimpleRefs, visited);
                simpleRefs.addAll(propRefs);

                Set<String> propAllRefs = getAllSimpleRefs(prop);
                simpleRefs.addAll(propAllRefs);

                if (prop.getItems() != null) {
                    Set<String> itemRefs = getSimpleRefFromSchemaRef(prop.getItems(), schemas, schemaToAllSimpleRefs, visited);
                    simpleRefs.addAll(itemRefs);

                    Set<String> itemAllSimpleRefs = getAllSimpleRefs(prop.getItems());
                    simpleRefs.addAll(itemAllSimpleRefs);
                }
            });
        }

        List<Schema<?>> innerSchemas = getAllInnerSchemas(targetSchema);
        for (Schema<?> innerSchema : innerSchemas) {
            Set<String> innerSchemaRefs = getSimpleRefRecursive(targetName, innerSchema, schemas, schemaToAllSimpleRefs, visited, false);
            simpleRefs.addAll(innerSchemaRefs);

            Set<String> itemAllSimpleRefs = getAllSimpleRefs(innerSchema);
            simpleRefs.addAll(itemAllSimpleRefs);
        }

        Set<String> innerSimpleRefs = new HashSet<>();
        for (String simpleRef : simpleRefs) {
            Schema schemaRef = schemas.get(simpleRef);
            Set<String> innerSchemaRefs = getSimpleRefRecursive(simpleRef, schemaRef, schemas, schemaToAllSimpleRefs, visited, true);
            innerSimpleRefs.addAll(innerSchemaRefs);
        }

        simpleRefs.addAll(innerSimpleRefs);
        return simpleRefs;
    }

    private Set<String> getSimpleRefFromSchemaRef(Schema<?> targetSchema,
                                                  Map<String, Schema> schemas,
                                                  Map<String, Set<String>> discriminatorSchemaRefs,
                                                  Set<String> visited) {
        if (targetSchema.get$ref() == null) {
            return Collections.emptySet();
        }

        final Set<String> refs = new HashSet<>();
        String targetRef = ModelUtils.getSimpleRef(targetSchema.get$ref());
        refs.add(targetRef);

        Schema targetRefSchema = schemas.get(targetRef);
        if (targetRefSchema != null) {
            Set<String> itemsRefs = getSimpleRefRecursive(targetRef, targetRefSchema, schemas, discriminatorSchemaRefs, visited, true);
            refs.addAll(itemsRefs);
        }

        return refs;
    }

    private Set<String> getAllSimpleRefs(Schema req) {
        return getAllSimpleRefs(req, new HashSet<>());
    }

    private Set<String> getAllSimpleRefs(Schema req, Set<Schema<?>> visited) {
        Set<String> itemAllRefs = getAllRefs(req, visited);
        return itemAllRefs.stream()
            .map(ModelUtils::getSimpleRef)
            .collect(Collectors.toSet());
    }

    private Set<String> getAllRefs(Schema req) {
        return getAllRefs(req, new HashSet<>());
    }

    private Set<String> getAllRefs(Schema req, Set<Schema<?>> visited) {
        if (req instanceof ObjectSchema && visited.stream().anyMatch(s -> s == req)) {
            return Collections.emptySet();
        }

        if (req instanceof ObjectSchema) {
            visited.add(req);
        }

        Set<String> refs = new HashSet<>();
        if (req.get$ref() != null) {
            refs.add(req.get$ref());
        }

        List<Schema<?>> innerSchemas = getAllInnerSchemas(req);
        for (Schema<?> innerSchema : innerSchemas) {
            if (innerSchema.get$ref() != null) {
                refs.add(innerSchema.get$ref());
            }

            visited.add(innerSchema);
            Set<String> schemaRefs = getAllRefs(innerSchema, visited);
            refs.addAll(schemaRefs);
        }

        return refs;
    }

    private List<Schema<?>> getAllInnerSchemas(Schema req) {
        List<Schema<?>> schemas = new ArrayList<>();

        Schema referencedSchema = ModelUtils.getReferencedSchema(openAPI, req);
        if (referencedSchema != req) {
            schemas.add(referencedSchema);
        }

        if (req.getAllOf() != null) {
            for (Object s : req.getAllOf()) {
                if (s instanceof Schema<?> schema) {
                    schemas.add(schema);
                }
            }
        }

        if (req.getAnyOf() != null) {
            for (Object s : req.getAnyOf()) {
                if (s instanceof Schema<?> schema) {
                    schemas.add(schema);
                }
            }
        }

        if (req.getOneOf() != null) {
            for (Object s : req.getOneOf()) {
                if (s instanceof Schema<?> schema) {
                    schemas.add(schema);
                }
            }
        }

        if (req.getItems() != null) {
            schemas.add(req.getItems());
        }

        if (req.getAdditionalProperties() instanceof Schema<?> s) {
            schemas.add(s);
        }

        if (req.getAdditionalItems() != null) {
            schemas.add(req.getAdditionalItems());
        }

        return schemas;
    }

    // only access point Before models will be generated
    @Override
    public void processOpenAPI(OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
        setFilteredSchemaComponentsIfEnabled(openAPI);
    }

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        // recursively add import for mapping one type to multiple imports
        List<Map<String, String>> recursiveImports = (List<Map<String, String>>) objs.get("imports");
        if (recursiveImports == null)
            return objs;

        ListIterator<Map<String, String>> listIterator = recursiveImports.listIterator();
        while (listIterator.hasNext()) {
            String _import = listIterator.next().get("import");
            // if the import package happens to be found in the importMapping (key)
            // add the corresponding import package to the list
            if (importMapping.containsKey(_import)) {
                Map<String, String> newImportMap = new HashMap<>();
                newImportMap.put("import", importMapping.get(_import));
                listIterator.add(newImportMap);
            }
        }

        return postProcessModelsEnum(objs);
    }

    protected void handleImplicitHeaders(CodegenOperation operation) {
        if (operation.allParams.isEmpty()) {
            return;
        }
        var copy = new ArrayList<>(operation.allParams);
        operation.allParams.clear();

        for (var p : copy) {
            if (p.isHeaderParam && (params.implicitHeaders || shouldBeImplicitHeader(p))) {
                operation.implicitHeadersParams.add(p);
                operation.headerParams.removeIf(header -> header.baseName.equals(p.baseName));
                LOGGER.info("Update operation [{}]. Remove header [{}] because it's marked to be implicit", operation.operationId, p.baseName);
            } else {
                operation.allParams.add(p);
            }
        }
    }

    protected boolean shouldBeImplicitHeader(CodegenParameter parameter) {
        return params.implicitHeadersRegex != null && params.implicitHeadersRegex.matcher(parameter.baseName).matches();
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        var operations = objs.getOperations();
        var operationList = operations.getOperation();
        for (var op : operationList) {
            handleImplicitHeaders(op);
        }

        record AuthMethodGroup(String name, List<CodegenSecurity> methods) {}

        var authMethods = (List<AuthMethodGroup>) this.vendorExtensions.computeIfAbsent("authMethods", k -> new ArrayList<AuthMethodGroup>());
        var tags = (Set<String>) this.vendorExtensions.computeIfAbsent("tags", k -> new TreeSet<String>());
        for (var op : operationList) {
            if (op.hasAuthMethods) {
                if (params.codegenMode.isServer()) {
                    var operationAuthMethods = new TreeSet<String>();
                    for (var authMethod : op.authMethods) {
                        tags.add(upperCase(toVarName(authMethod.name)));
                        final String authName;
                        if (authMethod.isApiKey || authMethod.isBasic || authMethod.isBasicBearer) {
                            authName = upperCase(toVarName(authMethod.name));
                        } else if (authMethod.isOAuth) {
                            if (authMethod.scopes == null || authMethod.scopes.isEmpty()) {
                                authName = upperCase(toVarName(authMethod.name) + "NoScopes");
                            } else {
                                var scopes = authMethod.scopes.stream()
                                    .map(it -> this.upperCase(toVarName(it.get("scope").toString())))
                                    .sorted()
                                    .collect(Collectors.joining("With"));
                                authName = upperCase(toVarName(authMethod.name) + "With" + scopes);
                            }
                        } else {
                            throw new IllegalStateException();
                        }
                        operationAuthMethods.add(authName);
                        tags.add(upperCase(authName));
                    }
                    var authInterceptorTag = String.join("With", operationAuthMethods);
                    var security = new ArrayList<CodegenSecurity>();
                    for (int i = 0; i < op.authMethods.size(); i++) {
                        var source = op.authMethods.get(i);
                        var scopes = Objects.requireNonNullElse(source.scopes, List.<Map<String, Object>>of()).stream().map(m -> m.get("scope").toString()).toList();
                        var copy = source.filterByScopeNames(scopes);
                        security.add(copy);
                    }

                    tags.add(authInterceptorTag);
                    if (authMethods.stream().noneMatch(a -> a.name.equals(authInterceptorTag))) {
                        authMethods.add(new AuthMethodGroup(authInterceptorTag, security));
                    }


                    op.vendorExtensions.put("authInterceptorTag", authInterceptorTag);
                } else {
                    if (op.authMethods.size() > 1 && params.primaryAuth == null) {
                        var secSchemes = op.authMethods.stream()
                            .map(s -> s.name)
                            .collect(Collectors.toSet());
                        LOGGER.warn("Found multiple securitySchemes {} for {} {} it is recommended to specify preferred securityScheme using `primaryAuth` property, or the first random will be used",
                            secSchemes, op.httpMethod, op.path);
                    }
                }
            }
        }
        this.operationsByClassName.put(objs.getOperations().getClassname(), objs);
        return objs;
    }

    public static boolean isContentJson(CodegenParameter parameter) {
        return parameter.containerType != null
            && (parameter.containerType.startsWith("application/json") || parameter.containerType.startsWith("text/json"))
            || isContentJson(parameter.getContent());
    }

    public static boolean isContentJson(@Nullable Map<String, CodegenMediaType> content) {
        if (content == null) {
            return false;
        }

        return content.keySet().stream().anyMatch(k -> k.startsWith("application/json") || k.startsWith("text/json"));
    }

    public String upperCase(String name) {
        return (name.length() > 0) ? (Character.toUpperCase(name.charAt(0)) + name.substring(1)) : "";
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        objs.put("vendorExtensions", this.vendorExtensions);
        return objs;
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        if (openAPI == null) {
            return;
        }
        if (openAPI.getPaths() != null) {
            for (String pathname : openAPI.getPaths().keySet()) {
                PathItem path = openAPI.getPaths().get(pathname);
                if (path.readOperations() == null) {
                    continue;
                }
                for (Operation operation : path.readOperations()) {
                    LOGGER.info("Processing operation {}", operation.getOperationId());
                    if (hasBodyParameter(operation) || hasFormParameter(operation)) {
                        String defaultContentType = hasFormParameter(operation) ? "application/x-www-form-urlencoded" : "application/json";
                        List<String> consumes = new ArrayList<>(getConsumesInfo(openAPI, operation));
                        String contentType = consumes == null || consumes.isEmpty() ? defaultContentType : consumes.get(0);
                        operation.addExtension("x-contentType", contentType);
                    }
                    String accepts = getAccept(openAPI, operation);
                    operation.addExtension("x-accepts", accepts);

                }
            }
        }
        var securitySchemas = openAPI.getComponents().getSecuritySchemes();
        if (!Objects.requireNonNullElse(securitySchemas, Map.of()).isEmpty()) {
            switch (params.codegenMode) {
                case JAVA_CLIENT -> {
                    var securitySchemaClass = apiFileFolder() + File.separator + "ApiSecurity.java";
                    this.supportingFiles.add(new SupportingFile("javaClientSecuritySchema.mustache", securitySchemaClass));
                }
                case JAVA_SERVER -> {
                    var securitySchemaClass = apiFileFolder() + File.separator + "ApiSecurity.java";
                    this.supportingFiles.add(new SupportingFile("javaServerSecuritySchema.mustache", securitySchemaClass));
                }
                case KOTLIN_CLIENT -> {
                    var securitySchemaClass = apiFileFolder() + File.separator + "ApiSecurity.kt";
                    this.supportingFiles.add(new SupportingFile("kotlinClientSecuritySchema.mustache", securitySchemaClass));
                }
                case KOTLIN_SERVER -> {
                    var securitySchemaClass = apiFileFolder() + File.separator + "ApiSecurity.kt";
                    this.supportingFiles.add(new SupportingFile("kotlinServerSecuritySchema.mustache", securitySchemaClass));
                }
            }

        }
    }

    private static String getAccept(OpenAPI openAPI, Operation operation) {
        String accepts = null;
        String defaultContentType = "application/json";
        Set<String> producesInfo = getProducesInfo(openAPI, operation);
        if (producesInfo != null && !producesInfo.isEmpty()) {
            ArrayList<String> produces = new ArrayList<>(producesInfo);
            StringBuilder sb = new StringBuilder();
            for (String produce : produces) {
                if (defaultContentType.equalsIgnoreCase(produce)) {
                    accepts = defaultContentType;
                    break;
                } else {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append(produce);
                }
            }
            if (accepts == null) {
                accepts = sb.toString();
            }
        } else {
            accepts = defaultContentType;
        }

        return accepts;
    }

    @Override
    protected boolean needToImport(String type) {
        return super.needToImport(type) && !type.contains(".");
    }

    private String transliteIfNeeded(String name) {
        for (int i = 0; i < name.length(); i++) {
            if (Character.UnicodeBlock.of(name.charAt(i)).equals(Character.UnicodeBlock.CYRILLIC)) {
                String cyrillicToLatin = "Russian-Latin/BGN";
                Transliterator.getAvailableIDs();
                Transliterator toLatinTrans = Transliterator.getInstance(cyrillicToLatin);
                return toLatinTrans.transliterate(name);
            }
        }

        return name;
    }

    @Override
    public String sanitizeName(String name, String removeCharRegEx, ArrayList<String> exceptionList) {
        String result = super.sanitizeName(name, removeCharRegEx, exceptionList);
        return transliteIfNeeded(result);
    }

    @Override
    public String toEnumName(CodegenProperty property) {
        String name = transliteIfNeeded(property.name);
        return sanitizeName(camelize(name)) + "Enum";
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.length() == 0) {
            return "EMPTY";
        }

        // for symbol, e.g. $, #
        if (getSymbolName(value) != null) {
            return getSymbolName(value).toUpperCase(Locale.ROOT);
        }

        value = transliteIfNeeded(value);
        String upperSnakeCase = getUpperSnakeCase(value, Locale.ROOT);

        // number
        if ("Integer".equals(datatype) || "Int".equals(datatype) || "Long".equals(datatype) ||
            "Float".equals(datatype) || "Double".equals(datatype) || "BigDecimal".equals(datatype)) {
            String varName = "NUMBER_" + upperSnakeCase;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return varName;
        }

        // string
        String var = upperSnakeCase.replaceAll("\\W+", "_").toUpperCase(Locale.ROOT);
        if (var.matches("\\d.*")) {
            return "_" + var;
        } else {
            return var;
        }
    }

    @Deprecated
    public String toEnumVarNameDeprecated(String value, String datatype) {
        if (value.length() == 0) {
            return "EMPTY";
        }

        // for symbol, e.g. $, #
        if (getSymbolName(value) != null) {
            return getSymbolName(value).toUpperCase(Locale.ROOT);
        }

        value = transliteIfNeeded(value);

        // number
        if ("Integer".equals(datatype) || "Int".equals(datatype) || "Long".equals(datatype) ||
            "Float".equals(datatype) || "Double".equals(datatype) || "BigDecimal".equals(datatype)) {
            String varName = "NUMBER_" + value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return varName;
        }

        // string
        String var = value.replaceAll("\\W+", "_").toUpperCase(Locale.ROOT);
        if (var.matches("\\d.*")) {
            return "_" + var;
        } else {
            return var;
        }
    }

    @Override
    protected List<Map<String, Object>> buildEnumVars(List<Object> values, String dataType) {
        List<Map<String, Object>> enumVars = super.buildEnumVars(values, dataType);

        int truncateIdx = isRemoveEnumValuePrefix()
            ? findCommonPrefixOfVars(values).length()
            : 0;

        for (Object value : values) {
            String enumName = truncateIdx == 0
                ? String.valueOf(value)
                : value.toString().substring(truncateIdx);

            if (enumName.isEmpty()) {
                enumName = value.toString();
            }

            final String finalEnumName = toEnumVarName(enumName, dataType);
            final String finalEnumNameDeprecated = toEnumVarNameDeprecated(enumName, dataType);

            if (!finalEnumNameDeprecated.equals(finalEnumName)) {
                enumVars.stream()
                    .filter(e -> finalEnumName.equals(e.get("name")))
                    .findFirst()
                    .ifPresent(enumVar -> enumVar.put("nameDeprecated", finalEnumNameDeprecated));

            }
        }

        return enumVars;
    }

    @Override
    public ModelsMap postProcessModelsEnum(ModelsMap objs) {
        ModelsMap modelsMap = super.postProcessModelsEnum(objs);

        for (ModelMap mo : objs.getModels()) {
            CodegenModel cm = mo.getModel();

            if (Boolean.TRUE.equals(cm.isEnum) && cm.allowableValues != null) {
                List<Map<String, Object>> enumVars = (List<Map<String, Object>>) cm.allowableValues.get("enumVars");
                if (enumVars != null) {
                    List<Map<String, Object>> enumVarsDeprecated = new ArrayList<>();
                    for (Map<String, Object> enumVar : enumVars) {
                        if (enumVar.containsKey("nameDeprecated")) {
                            enumVarsDeprecated.add(Map.of("name", enumVar.get("nameDeprecated"),
                                "nameNew", enumVar.get("name"),
                                "value", enumVar.get("value")));
                        }
                    }

                    if (!enumVarsDeprecated.isEmpty()) {
                        cm.allowableValues.put("enumVarsDeprecated", enumVarsDeprecated);
                    }
                }
            }
        }

        return modelsMap;
    }

    @Override
    public void updateCodegenPropertyEnum(CodegenProperty var) {
        super.updateCodegenPropertyEnum(var);

        Map<String, Object> allowableValues = var.allowableValues;
        if (var.mostInnerItems != null) {
            allowableValues = var.mostInnerItems.allowableValues;
        }

        if (allowableValues != null) {
            List<Map<String, Object>> enumVars = (List<Map<String, Object>>) allowableValues.get("enumVars");
            if (enumVars != null) {
                List<Map<String, Object>> enumVarsDeprecated = new ArrayList<>();
                for (Map<String, Object> enumVar : enumVars) {
                    if (enumVar.containsKey("nameDeprecated")) {
                        enumVarsDeprecated.add(Map.of("name", enumVar.get("nameDeprecated"),
                            "nameNew", enumVar.get("name"),
                            "value", enumVar.get("value")));
                    }
                }

                if (!enumVarsDeprecated.isEmpty()) {
                    allowableValues.put("enumVarsDeprecated", enumVarsDeprecated);
                }
            }
        }
    }

    @Override
    public String toEnumValue(String value, String datatype) {
        if ("Integer".equals(datatype) || "Int".equals(datatype) || "Double".equals(datatype)) {
            return value;
        } else if ("Long".equals(datatype)) {
            // add l to number, e.g. 2048 => 2048l
            return value + "l";
        } else if ("Float".equals(datatype)) {
            // add f to number, e.g. 3.14 => 3.14f
            return value + "f";
        } else if ("BigDecimal".equals(datatype)) {
            // use BigDecimal String constructor
            return "new BigDecimal(\"" + value + "\")";
        } else {
            return "\"" + escapeText(value) + "\"";
        }
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);
        op.path = sanitizePath(op.path);
        return op;
    }

    @Override
    public void postProcessParameter(CodegenParameter p) {
        // we use a custom version of this function to remove the l, d, and f suffixes from Long/Double/Float
        // defaultValues
        // remove the l because our users will use Long.parseLong(String defaultValue)
        // remove the d because our users will use Double.parseDouble(String defaultValue)
        // remove the f because our users will use Float.parseFloat(String defaultValue)
        // NOTE: for CodegenParameters we DO need these suffixes because those defaultValues are used as java value
        // literals assigned to Long/Double/Float
        if (p.defaultValue == null) {
            return;
        }

        Boolean fixLong = (p.isLong && "l".equals(p.defaultValue.substring(p.defaultValue.length() - 1)));
        Boolean fixDouble = (p.isDouble && "d".equals(p.defaultValue.substring(p.defaultValue.length() - 1)));
        Boolean fixFloat = (p.isFloat && "f".equals(p.defaultValue.substring(p.defaultValue.length() - 1)));
        if (fixLong || fixDouble || fixFloat) {
            p.defaultValue = p.defaultValue.substring(0, p.defaultValue.length() - 1);
        }
    }

    private static CodegenModel reconcileInlineEnums(CodegenModel codegenModel, CodegenModel parentCodegenModel) {
        // This generator uses inline classes to define enums, which breaks when
        // dealing with models that have subTypes. To clean this up, we will analyze
        // the parent and child models, look for enums that match, and remove
        // them from the child models and leave them in the parent.
        // Because the child models extend the parents, the enums will be available via the parent.

        // Only bother with reconciliation if the parent model has enums.
        if (!parentCodegenModel.hasEnums) {
            return codegenModel;
        }

        // Get the properties for the parent and child models
        final List<CodegenProperty> parentModelCodegenProperties = parentCodegenModel.vars;
        List<CodegenProperty> codegenProperties = codegenModel.vars;

        // Iterate over all of the parent model properties
        boolean removedChildEnum = false;
        for (CodegenProperty parentModelCodegenPropery : parentModelCodegenProperties) {
            // Look for enums
            if (parentModelCodegenPropery.isEnum) {
                // Now that we have found an enum in the parent class,
                // and search the child class for the same enum.
                Iterator<CodegenProperty> iterator = codegenProperties.iterator();
                while (iterator.hasNext()) {
                    CodegenProperty codegenProperty = iterator.next();
                    if (codegenProperty.isEnum && codegenProperty.equals(parentModelCodegenPropery)) {
                        // We found an enum in the child class that is
                        // a duplicate of the one in the parent, so remove it.
                        iterator.remove();
                        removedChildEnum = true;
                    }
                }
            }
        }

        if (removedChildEnum) {
            codegenModel.vars = codegenProperties;
        }
        return codegenModel;
    }

    private static String sanitizePackageName(String packageName) {
        packageName = packageName.trim(); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.
        packageName = packageName.replaceAll("[^a-zA-Z0-9_\\.]", "_");
        if (Strings.isNullOrEmpty(packageName)) {
            return "invalidPackageName";
        }
        return packageName;
    }

    private String sanitizePath(String p) {
        //prefer replace a ", instead of a fuLL URL encode for readability
        return p.replaceAll("\"", "%22");
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }

    /*
     * Derive invoker package name based on the input
     * e.g. foo.bar.model => foo.bar
     *
     * @param input API package/model name
     * @return Derived invoker package name based on API package/model name
     */
    private String deriveInvokerPackageName(String input) {
        String[] parts = input.split(Pattern.quote(".")); // Split on period.

        StringBuilder sb = new StringBuilder();
        String delim = "";
        for (String p : Arrays.copyOf(parts, parts.length - 1)) {
            sb.append(delim).append(p);
            delim = ".";
        }
        return sb.toString();
    }

    @Override
    public String toRegularExpression(String pattern) {
        return escapeText(pattern);
    }

    /**
     * Output the Getter name for boolean property, e.g. isActive
     *
     * @param name the name of the property
     * @return getter name based on naming convention
     */
    @Override
    public String toBooleanGetter(String name) {
        return getterAndSetterCapitalize(name);
    }

    @Override
    public String sanitizeTag(String tag) {
//        tag = camelize(underscore(sanitizeName(tag)));
//
//         tag starts with numbers
//        if (tag.matches("^\\d.*")) {
//            tag = "Class" + tag;
//        }
        return tag;
    }

    /**
     * Camelize the method name of the getter and setter
     *
     * @param name string to be camelized
     * @return Camelized string
     */
    @Override
    public String getterAndSetterCapitalize(String name) {
        CamelizeOption lowercaseFirstLetter = CamelizeOption.UPPERCASE_FIRST_CHAR;
        if (name == null || name.length() == 0) {
            return name;
        }
        name = toVarName(name);
        //
        // Let the property name capitalized
        // except when the first letter of the property name is lowercase and the second letter is uppercase
        // Refer to section 8.8: Capitalization of inferred names of the JavaBeans API specification
        // http://download.oracle.com/otn-pub/jcp/7224-javabeans-1.01-fr-spec-oth-JSpec/beans.101.pdf)
        //
        if (name.length() > 1 && Character.isLowerCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            lowercaseFirstLetter = CamelizeOption.LOWERCASE_FIRST_CHAR;
        }
        return camelize(name, lowercaseFirstLetter);
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null) {
            return;
        }

        String javaPostProcessFile = System.getenv("JAVA_POST_PROCESS_FILE");
        if (StringUtils.isEmpty(javaPostProcessFile)) {
            return; // skip if JAVA_POST_PROCESS_FILE env variable is not defined
        }

        // only process files with java extension
        if ("java".equals(FilenameUtils.getExtension(file.toString()))) {
            String command = javaPostProcessFile + " " + file;
            try {
                Process p = Runtime.getRuntime().exec(command);
                p.waitFor();
                int exitValue = p.exitValue();
                if (exitValue != 0) {
                    LOGGER.error("Error running the command ({}). Exit value: {}", command, exitValue);
                } else {
                    LOGGER.info("Successfully executed: {}", command);
                }
            } catch (InterruptedException | IOException e) {
                LOGGER.error("Error running the command ({}). Exception: {}", command, e.getMessage());
                // Restore interrupted state
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    protected void addAdditionPropertiesToCodeGenModel(CodegenModel codegenModel, Schema schema) {
        if (!supportsAdditionalPropertiesWithComposedSchema) {
            // The additional (undeclared) propertiees are modeled in Java as a HashMap.
            //
            // 1. supportsAdditionalPropertiesWithComposedSchema is set to false:
            //    The generated model class extends from the HashMap. That does not work
            //    with composed schemas that also use a discriminator because the model class
            //    is supposed to extend from the generated parent model class.
            // 2. supportsAdditionalPropertiesWithComposedSchema is set to true:
            //    The HashMap is a field.
            super.addAdditionPropertiesToCodeGenModel(codegenModel, schema);
        }

        // See https://github.com/OpenAPITools/openapi-generator/pull/1729#issuecomment-449937728
        var s = ModelUtils.getAdditionalProperties(schema);
        // 's' may be null if 'additionalProperties: false' in the OpenAPI schema.
        if (s != null) {
            codegenModel.additionalPropertiesType = getSchemaType(s);
            addImport(codegenModel, codegenModel.additionalPropertiesType);
        }
    }


    @Override
    protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
        return super.addMustacheLambdas()
            .put("trim", (fragment, out) -> {
                var text = fragment.execute();
                out.write(text.trim());
            })
            .put("classname", (fragment, out) -> {
                var text = fragment.execute();
                out.write(this.upperCase(toVarName(text)));
            })
            .put("javaApiResponses", javaGen(new ApiResponseGenerator()))
            .put("javaModel", javaGen(new ModelGenerator()))
            .put("javaClientApi", javaGen(new ClientApiGenerator()))
            .put("javaClientRequestMappers", javaGen(new ClientRequestMapperGenerator()))
            .put("javaClientApiResponseMapper", javaGen(new ClientResponseMapperGenerator()))
            .put("javaClientSecuritySchema", javaGen(new ClientSecuritySchemaGenerator()))
            .put("javaServerApi", javaGen(new ServerApiGenerator()))
            .put("javaServerApiDelegate", javaGen(new ServerApiDelegateGenerator()))
            .put("javaServerApiModule", javaGen(new ServerApiModuleGenerator()))
            .put("javaServerRequestMappers", javaGen(new ServerRequestMapperGenerator()))
            .put("javaServerResponseMappers", javaGen(new ServerResponseMapperGenerator()))
            .put("javaServerSecuritySchema", javaGen(new ServerSecuritySchemaGenerator()))
            .put("kotlinApiResponses", kotlinGen(new ru.tinkoff.kora.openapi.generator.kotlingen.ApiResponseGenerator()))
            .put("kotlinClientApi", kotlinGen(new ru.tinkoff.kora.openapi.generator.kotlingen.ClientApiGenerator()))
            .put("kotlinClientRequestMappers", kotlinGen(new ru.tinkoff.kora.openapi.generator.kotlingen.ClientRequestMapperGenerator()))
            .put("kotlinClientResponseMappers", kotlinGen(new ru.tinkoff.kora.openapi.generator.kotlingen.ClientResponseMapperGenerator()))
            .put("kotlinClientSecuritySchema", kotlinGen(new ru.tinkoff.kora.openapi.generator.kotlingen.ClientSecuritySchemaGenerator()))
            .put("kotlinModel", kotlinGen(new ru.tinkoff.kora.openapi.generator.kotlingen.ModelGenerator()))
            .put("kotlinServerApi", kotlinGen(new ru.tinkoff.kora.openapi.generator.kotlingen.ServerApiGenerator()))
            ;
    }

    <C, T extends AbstractGenerator<C, JavaFile>> Mustache.Lambda javaGen(T gen) {
        return (frag, out) -> {
            gen.apiPackage = apiPackage;
            gen.modelPackage = modelPackage;
            gen.params = params;
            gen.models = models;
            gen.operationsByClassName = operationsByClassName;
            gen.typeMapping = typeMapping;
            var ctx = frag.context();
            gen.generate((C) ctx).writeTo(out);
        };
    }

    <C, T extends AbstractGenerator<C, FileSpec>> Mustache.Lambda kotlinGen(T gen) {
        return (frag, out) -> {
            gen.apiPackage = apiPackage;
            gen.modelPackage = modelPackage;
            gen.params = params;
            gen.models = models;
            gen.operationsByClassName = operationsByClassName;
            gen.typeMapping = typeMapping;
            var ctx = frag.context();
            gen.generate((C) ctx).writeTo(out);
        };
    }

    @Override
    public void postProcess() {
    }
}
