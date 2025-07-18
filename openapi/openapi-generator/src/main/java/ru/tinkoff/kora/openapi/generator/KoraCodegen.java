package ru.tinkoff.kora.openapi.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.ibm.icu.text.Transliterator;
import com.samskivert.mustache.Mustache;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import jakarta.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
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

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openapitools.codegen.utils.StringUtils.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class KoraCodegen extends DefaultCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(KoraCodegen.class);

    public enum Mode {
        JAVA_CLIENT("java-client"),
        JAVA_SERVER("java-server"),
        JAVA_ASYNC_CLIENT("java-async-client"),
        JAVA_ASYNC_SERVER("java-async-server"),
        JAVA_REACTIVE_CLIENT("java-reactive-client"),
        JAVA_REACTIVE_SERVER("java-reactive-server"),
        KOTLIN_CLIENT("kotlin-client"),
        KOTLIN_SERVER("kotlin-server"),
        KOTLIN_SUSPEND_CLIENT("kotlin-suspend-client"),
        KOTLIN_SUSPEND_SERVER("kotlin-suspend-server");

        private final String mode;

        Mode(String mode) {
            this.mode = mode;
        }

        public String getMode() {
            return mode;
        }

        public static Mode ofMode(String option) {
            for (Mode value : Mode.values()) {
                if (value.getMode().equals(option)) {
                    return value;
                }
            }

            final List<String> modes = Arrays.stream(Mode.values())
                .map(Mode::getMode)
                .toList();
            throw new UnsupportedOperationException("Unknown Mode is provided: " + option + ", available modes: " + modes);
        }

        public boolean isServer() {
            return switch (this) {
                case JAVA_SERVER, JAVA_ASYNC_SERVER, JAVA_REACTIVE_SERVER, KOTLIN_SERVER, KOTLIN_SUSPEND_SERVER -> true;
                default -> false;
            };
        }

        public boolean isClient() {
            return switch (this) {
                case JAVA_CLIENT, JAVA_ASYNC_CLIENT, JAVA_REACTIVE_CLIENT, KOTLIN_CLIENT, KOTLIN_SUSPEND_CLIENT -> true;
                default -> false;
            };
        }

        public boolean isJava() {
            return this != KOTLIN_CLIENT && this != KOTLIN_SERVER && this != KOTLIN_SUSPEND_CLIENT && this != KOTLIN_SUSPEND_SERVER;
        }

        public boolean isKotlin() {
            return !isJava();
        }
    }

    public enum DelegateMethodBodyMode {
        NONE("none"),
        THROW_EXCEPTION("throwException");

        private final String mode;

        DelegateMethodBodyMode(String mode) {
            this.mode = mode;
        }

        public String getMode() {
            return mode;
        }

        public boolean isDelegateMethodBodyModeNeedsDefaultComponent() {
            return this == THROW_EXCEPTION;
        }

        public static DelegateMethodBodyMode of(String option) {
            for (DelegateMethodBodyMode value : DelegateMethodBodyMode.values()) {
                if (value.getMode().equals(option)) {
                    return value;
                }
            }

            final List<String> modes = Arrays.stream(Mode.values())
                .map(Mode::getMode)
                .toList();
            throw new UnsupportedOperationException("Unknown DelegateMethodBodyMode is provided: " + option + ", available modes: " + modes);
        }
    }

    record TagClient(@Nullable String httpClientTag, @Nullable String telemetryTag) {}

    record Interceptor(@Nullable String type, @Nullable Object tag) {}

    record AdditionalAnnotation(@Nullable String annotation) {}

    @Override
    public String getName() {
        return "kora";
    }

    record CodegenParams(
        Mode codegenMode,
        String jsonAnnotation,
        boolean enableValidation,
        boolean authAsMethodArgument,
        @Nullable String primaryAuth,
        @Nullable String clientConfigPrefix,
        String securityConfigPrefix,
        Map<String, TagClient> clientTags,
        Map<String, List<Interceptor>> interceptors,
        Map<String, List<AdditionalAnnotation>> additionalContractAnnotations,
        boolean requestInDelegateParams,
        boolean enableJsonNullable,
        boolean filterWithModels,
        @Nullable String prefixPath,
        DelegateMethodBodyMode delegateMethodBodyMode,
        boolean implicitHeaders,
        @Nullable Pattern implicitHeadersRegex
    ) {
        static List<CliOption> cliOptions() {
            var cliOptions = new ArrayList<CliOption>();
            cliOptions.add(CliOption.newString(CODEGEN_MODE, "Generation mode (one of java, reactive or kotlin)"));
            cliOptions.add(CliOption.newString(SECURITY_CONFIG_PREFIX, "Config prefix for security config parsers"));
            cliOptions.add(CliOption.newString(PRIMARY_AUTH, "Specify primary HTTP client securityScheme if multiple are available for method"));
            cliOptions.add(CliOption.newString(CLIENT_CONFIG_PREFIX, "Generated client config prefix"));
            cliOptions.add(CliOption.newString(JSON_ANNOTATION, "Json annotation tag to place on body and other json related params"));
            cliOptions.add(CliOption.newString(CLIENT_TAGS, "Json containing http client tags configuration for apis"));
            cliOptions.add(CliOption.newString(INTERCEPTORS, "Json containing interceptors for HTTP server/client"));
            cliOptions.add(CliOption.newBoolean(ENABLE_VALIDATION, "Generate validation related annotation on models and controllers"));
            cliOptions.add(CliOption.newBoolean(REQUEST_DELEGATE_PARAMS, "Generate HttpServerRequest parameter in delegate methods"));
            cliOptions.add(CliOption.newString(ADDITIONAL_CONTRACT_ANNOTATIONS, "Additional annotations for HTTP client/server methods"));
            cliOptions.add(CliOption.newBoolean(AUTH_AS_METHOD_ARGUMENT, "HTTP client authorization as method argument"));
            cliOptions.add(CliOption.newBoolean(ENABLE_JSON_NULLABLE, "If enabled then wraps Nullable and NonRequired fields with JsonNullable type"));
            cliOptions.add(CliOption.newBoolean(FILTER_WITH_MODELS, "If enabled then when openapiNormalizer FILTER option is specified, will try to filter not only operations, but all unused models as well"));
            cliOptions.add(CliOption.newString(PREFIX_PATH, "Path prefix for HTTP Server controllers"));
            cliOptions.add(CliOption.newString(DELEGATE_METHOD_BODY_MODE, "Delegate method generation mode"));
            return cliOptions;
        }

        static CodegenParams parse(Map<String, Object> additionalProperties) {
            var codegenMode = Mode.JAVA_CLIENT;
            var jsonAnnotation = "ru.tinkoff.kora.json.common.annotation.Json";
            var enableServerValidation = false;
            var authAsMethodArgument = false;
            var primaryAuth = (String) null;
            var clientConfigPrefix = (String) null;
            var securityConfigPrefix = (String) null;
            var clientTags = new HashMap<String, TagClient>();
            var interceptors = new HashMap<String, List<Interceptor>>();
            var additionalContractAnnotations = new HashMap<String, List<AdditionalAnnotation>>();
            var requestInDelegateParams = false;
            var enableJsonNullable = false;
            var filterWithModels = false;
            String prefixPath = null;
            var delegateMethodBodyMode = DelegateMethodBodyMode.NONE;
            boolean implicitHeaders = false;
            Pattern implicitHeadersRegex = null;

            if (additionalProperties.containsKey(CODEGEN_MODE)) {
                codegenMode = Mode.ofMode(additionalProperties.get(CODEGEN_MODE).toString());
            }
            if (additionalProperties.containsKey(JSON_ANNOTATION)) {
                jsonAnnotation = additionalProperties.get(JSON_ANNOTATION).toString();
            }
            if (additionalProperties.containsKey(CLIENT_TAGS)) {
                var clientTagsJson = additionalProperties.get(CLIENT_TAGS).toString();
                try {
                    clientTags = new ObjectMapper().readerFor(TypeFactory.defaultInstance().constructMapType(Map.class, String.class, TagClient.class)).readValue(clientTagsJson);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            if (additionalProperties.containsKey(INTERCEPTORS)) {
                var interceptorJson = additionalProperties.get(INTERCEPTORS).toString();
                try {
                    interceptors = new ObjectMapper().readerFor(TypeFactory.defaultInstance()
                            .constructType(new TypeReference<Map<String, List<Interceptor>>>() {}))
                        .readValue(interceptorJson);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            if (additionalProperties.containsKey(ADDITIONAL_CONTRACT_ANNOTATIONS)) {
                var json = additionalProperties.get(ADDITIONAL_CONTRACT_ANNOTATIONS).toString();
                try {
                    additionalContractAnnotations = new ObjectMapper().readerFor(TypeFactory.defaultInstance()
                            .constructType(new TypeReference<Map<String, List<AdditionalAnnotation>>>() {}))
                        .readValue(json);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            if (additionalProperties.containsKey(PRIMARY_AUTH)) {
                primaryAuth = additionalProperties.get(PRIMARY_AUTH).toString();
            }
            if (additionalProperties.containsKey(AUTH_AS_METHOD_ARGUMENT)) {
                authAsMethodArgument = Boolean.parseBoolean(additionalProperties.get(AUTH_AS_METHOD_ARGUMENT).toString());
            }
            if (additionalProperties.containsKey(CLIENT_CONFIG_PREFIX)) {
                clientConfigPrefix = additionalProperties.get(CLIENT_CONFIG_PREFIX).toString();
            }
            if (additionalProperties.containsKey(SECURITY_CONFIG_PREFIX)) {
                securityConfigPrefix = additionalProperties.get(SECURITY_CONFIG_PREFIX).toString();
            }
            if (additionalProperties.containsKey(ENABLE_VALIDATION) && codegenMode.isServer()) {
                enableServerValidation = Boolean.parseBoolean(additionalProperties.get(ENABLE_VALIDATION).toString());
            }
            if (additionalProperties.containsKey(REQUEST_DELEGATE_PARAMS) && codegenMode.isServer()) {
                requestInDelegateParams = Boolean.parseBoolean(additionalProperties.get(REQUEST_DELEGATE_PARAMS).toString());
            }
            if (additionalProperties.containsKey(ENABLE_JSON_NULLABLE)) {
                enableJsonNullable = Boolean.parseBoolean(additionalProperties.get(ENABLE_JSON_NULLABLE).toString());
            }
            if (additionalProperties.containsKey(FILTER_WITH_MODELS)) {
                filterWithModels = Boolean.parseBoolean(additionalProperties.get(FILTER_WITH_MODELS).toString());
            }
            if (additionalProperties.containsKey(PREFIX_PATH)) {
                prefixPath = additionalProperties.get(PREFIX_PATH).toString();
            }
            if (additionalProperties.containsKey(DELEGATE_METHOD_BODY_MODE)) {
                delegateMethodBodyMode = DelegateMethodBodyMode.of(additionalProperties.get(DELEGATE_METHOD_BODY_MODE).toString());
            }
            if (additionalProperties.containsKey(IMPLICIT_HEADERS)) {
                implicitHeaders = Boolean.parseBoolean(additionalProperties.get(IMPLICIT_HEADERS).toString());
            }
            if (additionalProperties.containsKey(IMPLICIT_HEADERS_REGEX)) {
                implicitHeadersRegex = Optional.ofNullable(additionalProperties.get(IMPLICIT_HEADERS_REGEX).toString())
                    .filter(s -> !s.isBlank())
                    .map(s -> Pattern.compile(((String) s)))
                    .orElse(null);
            }

            return new CodegenParams(codegenMode, jsonAnnotation, enableServerValidation, authAsMethodArgument, primaryAuth, clientConfigPrefix,
                securityConfigPrefix, clientTags, interceptors, additionalContractAnnotations, requestInDelegateParams, enableJsonNullable,
                filterWithModels, prefixPath, delegateMethodBodyMode, implicitHeaders, implicitHeadersRegex);
        }

        void processAdditionalProperties(Map<String, Object> additionalProperties) {
            additionalProperties.put("hasSecurityConfigPrefix", securityConfigPrefix != null);
            additionalProperties.put("requestInDelegateParams", requestInDelegateParams);
            additionalProperties.put("prefixPath", prefixPath);
            additionalProperties.put("hasDelegateMethodBodyMode", delegateMethodBodyMode != DelegateMethodBodyMode.NONE);
            additionalProperties.put("isThrowExceptionDelegateMethodBodyMode", delegateMethodBodyMode == DelegateMethodBodyMode.THROW_EXCEPTION);
            additionalProperties.put("isDelegateMethodBodyModeNeedsDefaultComponent", delegateMethodBodyMode.isDelegateMethodBodyModeNeedsDefaultComponent());

            switch (codegenMode) {
                case JAVA_CLIENT -> {
                    additionalProperties.put("isClient", true);
                    additionalProperties.put("isBlocking", true);
                }
                case JAVA_REACTIVE_CLIENT, JAVA_ASYNC_CLIENT -> {
                    additionalProperties.put("isClient", true);
                    additionalProperties.put("isAsync", this.codegenMode == Mode.JAVA_ASYNC_CLIENT);
                    additionalProperties.put("isReactive", this.codegenMode == Mode.JAVA_REACTIVE_CLIENT);
                }
                case JAVA_SERVER -> {
                    additionalProperties.put("isClient", false);
                    additionalProperties.put("isBlocking", true);
                }
                case JAVA_REACTIVE_SERVER, JAVA_ASYNC_SERVER -> {
                    additionalProperties.put("isClient", false);
                    additionalProperties.put("isAsync", this.codegenMode == Mode.JAVA_ASYNC_SERVER);
                    additionalProperties.put("isReactive", this.codegenMode == Mode.JAVA_REACTIVE_SERVER);
                }
                case KOTLIN_CLIENT, KOTLIN_SUSPEND_CLIENT -> {
                    additionalProperties.put("isClient", true);
                    additionalProperties.put("isSuspend", this.codegenMode == Mode.KOTLIN_SUSPEND_CLIENT);
                }
                case KOTLIN_SERVER, KOTLIN_SUSPEND_SERVER -> {
                    additionalProperties.put("isClient", false);
                    additionalProperties.put("isSuspend", this.codegenMode == Mode.KOTLIN_SUSPEND_SERVER);
                }
            }
        }
    }


    public static final String CODEGEN_MODE = "mode";
    public static final String JSON_ANNOTATION = "jsonAnnotation";
    public static final String ENABLE_VALIDATION = "enableServerValidation";
    public static final String OBJECT_TYPE = "objectType";
    public static final String DISABLE_HTML_ESCAPING = "disableHtmlEscaping";
    public static final String IGNORE_ANYOF_IN_ENUM = "ignoreAnyOfInEnum";
    public static final String ADDITIONAL_MODEL_TYPE_ANNOTATIONS = "additionalModelTypeAnnotations";
    public static final String ADDITIONAL_ENUM_TYPE_ANNOTATIONS = "additionalEnumTypeAnnotations";
    public static final String DISCRIMINATOR_CASE_SENSITIVE = "discriminatorCaseSensitive";
    public static final String PRIMARY_AUTH = "primaryAuth";
    public static final String CLIENT_CONFIG_PREFIX = "clientConfigPrefix";
    public static final String SECURITY_CONFIG_PREFIX = "securityConfigPrefix";
    public static final String CLIENT_TAGS = "tags";
    public static final String REQUEST_DELEGATE_PARAMS = "requestInDelegateParams";
    public static final String INTERCEPTORS = "interceptors";
    public static final String ADDITIONAL_CONTRACT_ANNOTATIONS = "additionalContractAnnotations";
    public static final String AUTH_AS_METHOD_ARGUMENT = "authAsMethodArgument";
    public static final String ENABLE_JSON_NULLABLE = "enableJsonNullable";
    public static final String FILTER_WITH_MODELS = "filterWithModels";
    public static final String PREFIX_PATH = "prefixPath";
    public static final String DELEGATE_METHOD_BODY_MODE = "delegateMethodBodyMode";
    public static final String IMPLICIT_HEADERS = "implicitHeaders";
    public static final String IMPLICIT_HEADERS_REGEX = "implicitHeadersRegex";

    protected String invokerPackage = "org.openapitools";
    protected boolean fullJavaUtil;
    protected boolean discriminatorCaseSensitive = true; // True if the discriminator value lookup should be case-sensitive.
    protected boolean disableHtmlEscaping = false;
    protected String booleanGetterPrefix = "get";
    protected boolean ignoreAnyOfInEnum = false;
    private String objectType = "java.lang.Object";
    protected List<String> additionalModelTypeAnnotations = new LinkedList<>();
    protected List<String> additionalEnumTypeAnnotations = new LinkedList<>();

    private CodegenParams params;

    public KoraCodegen() {
        super();
        apiPackage = "org.openapitools.api";
        modelPackage = "org.openapitools.model";
        invokerPackage = "org.openapitools.api";

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


        instantiationTypes.put("array", "ArrayList");
        instantiationTypes.put("set", "LinkedHashSet");
        instantiationTypes.put("map", "HashMap");

        // Dates - RFC3339
        typeMapping.put("date", "java.time.LocalDate");
        typeMapping.put("time", "java.time.OffsetTime");
        typeMapping.put("duration", "java.time.Duration");
        typeMapping.put("DateTime", "java.time.OffsetDateTime");

        typeMapping.put("UUID", "java.util.UUID");
        typeMapping.put("URI", "java.net.URI");
        typeMapping.put("BigDecimal", "java.math.BigDecimal");

        importMapping.put("BigDecimal", "java.math.BigDecimal");
        importMapping.put("UUID", "java.util.UUID");
        importMapping.put("URI", "java.net.URI");
        importMapping.put("OffsetDateTime", "java.time.OffsetDateTime");
        importMapping.put("Map", "java.util.Map");
        importMapping.put("Array", "java.util.List");
        importMapping.put("HashMap", "java.util.HashMap");
        importMapping.put("ArrayList", "java.util.ArrayList");
        importMapping.put("List", "java.util.*");
        importMapping.put("Set", "java.util.*");
        importMapping.put("LinkedHashSet", "java.util.LinkedHashSet");

        cliOptions.add(new CliOption(CodegenConstants.INVOKER_PACKAGE, CodegenConstants.INVOKER_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.MODEL_PACKAGE, CodegenConstants.MODEL_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.API_PACKAGE, CodegenConstants.API_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.API_PACKAGE, CodegenConstants.API_PACKAGE_DESC));

        cliOptions.add(CliOption.newBoolean(DISABLE_HTML_ESCAPING, "Disable HTML escaping of JSON strings when using gson (needed to avoid problems with byte[] fields)", disableHtmlEscaping));
        cliOptions.add(CliOption.newBoolean(IGNORE_ANYOF_IN_ENUM, "Ignore anyOf keyword in enum", ignoreAnyOfInEnum));
        cliOptions.add(CliOption.newString(ADDITIONAL_MODEL_TYPE_ANNOTATIONS, "Additional annotations for model type(class level annotations)"));
        cliOptions.add(CliOption.newString(ADDITIONAL_ENUM_TYPE_ANNOTATIONS, "Additional annotations for enum type(class level annotations)"));
        cliOptions.addAll(CodegenParams.cliOptions());
    }

    @Override
    public void processOpts() {
        super.processOpts();
        if (StringUtils.isEmpty(System.getenv("JAVA_POST_PROCESS_FILE"))) {
            LOGGER.info("Environment variable JAVA_POST_PROCESS_FILE not defined so the Java code may not be properly formatted. To define it, try 'export JAVA_POST_PROCESS_FILE=\"/usr/local/bin/clang-format -i\"' (Linux/Mac)");
            LOGGER.info("NOTE: To enable file post-processing, 'enablePostProcessFile' must be set to `true` (--enable-post-process-file for CLI).");
        }

        if (additionalProperties.containsKey(OBJECT_TYPE)) {
            this.objectType = additionalProperties.get(OBJECT_TYPE).toString();
        }
        params = CodegenParams.parse(additionalProperties);
        params.processAdditionalProperties(additionalProperties);
        switch (params.codegenMode) {
            case JAVA_CLIENT -> {
                modelTemplateFiles.put("javaModel.mustache", ".java");
                apiTemplateFiles.put("javaClientApi.mustache", ".java");
                apiTemplateFiles.put("javaApiResponses.mustache", "Responses.java");
                apiTemplateFiles.put("javaClientResponseMappers.mustache", "ClientResponseMappers.java");
                apiTemplateFiles.put("javaClientRequestMappers.mustache", "ClientRequestMappers.java");
            }
            case JAVA_REACTIVE_CLIENT, JAVA_ASYNC_CLIENT -> {
                modelTemplateFiles.put("javaModel.mustache", ".java");
                apiTemplateFiles.put("javaClientApi.mustache", ".java");
                apiTemplateFiles.put("javaApiResponses.mustache", "Responses.java");
                apiTemplateFiles.put("javaClientAsyncResponseMappers.mustache", "ClientResponseMappers.java");
                apiTemplateFiles.put("javaClientRequestMappers.mustache", "ClientRequestMappers.java");
            }
            case JAVA_SERVER, JAVA_REACTIVE_SERVER, JAVA_ASYNC_SERVER -> {
                apiTemplateFiles.put("javaServerApi.mustache", "Controller.java");
                apiTemplateFiles.put("javaServerApiDelegate.mustache", "Delegate.java");
                apiTemplateFiles.put("javaApiResponses.mustache", "Responses.java");
                apiTemplateFiles.put("javaServerRequestMappers.mustache", "ServerRequestMappers.java");
                apiTemplateFiles.put("javaServerResponseMappers.mustache", "ServerResponseMappers.java");
                modelTemplateFiles.put("javaModel.mustache", ".java");

                if (isNeedGenerateModule()) {
                    apiTemplateFiles.put("javaServerApiModule.mustache", "Module.java");
                }
            }
            case KOTLIN_CLIENT, KOTLIN_SUSPEND_CLIENT -> {
                modelTemplateFiles.put("kotlinModel.mustache", ".kt");
                apiTemplateFiles.put("kotlinClientApi.mustache", ".kt");
                apiTemplateFiles.put("kotlinApiResponses.mustache", "Responses.kt");
                if (params.codegenMode == Mode.KOTLIN_SUSPEND_CLIENT) {
                    apiTemplateFiles.put("kotlinClientAsyncResponseMappers.mustache", "ClientResponseMappers.kt");
                } else {
                    apiTemplateFiles.put("kotlinClientResponseMappers.mustache", "ClientResponseMappers.kt");
                }
                apiTemplateFiles.put("kotlinClientRequestMappers.mustache", "ClientRequestMappers.kt");
            }
            case KOTLIN_SERVER, KOTLIN_SUSPEND_SERVER -> {
                modelTemplateFiles.put("kotlinModel.mustache", ".kt");
                apiTemplateFiles.put("kotlinServerApi.mustache", "Controller.kt");
                apiTemplateFiles.put("kotlinServerApiDelegate.mustache", "Delegate.kt");
                apiTemplateFiles.put("kotlinApiResponses.mustache", "Responses.kt");
                apiTemplateFiles.put("kotlinServerRequestMappers.mustache", "ServerRequestMappers.kt");
                apiTemplateFiles.put("kotlinServerResponseMappers.mustache", "ServerResponseMappers.kt");

                if (isNeedGenerateModule()) {
                    apiTemplateFiles.put("kotlinServerApiModule.mustache", "Module.kt");
                }
            }
        }
        this.vendorExtensions.put("allowAspects", params.enableValidation() || !params.additionalContractAnnotations.isEmpty());

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
            typeMapping.put("file", "ByteArray");
            if (additionalProperties.containsKey(OBJECT_TYPE)) {
                typeMapping.put("AnyType", objectType);
                typeMapping.put("object", objectType);
            } else {
                typeMapping.put("AnyType", "kotlin.Any");
                typeMapping.put("object", "kotlin.Any");
            }
            typeMapping.put("array", "List");
            typeMapping.put("set", "Set");
            typeMapping.put("map", "Map");
            typeMapping.put("int", "Int");
            typeMapping.put("integer", "Int");
            typeMapping.put("ByteArray", "ByteArray");
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
            typeMapping.put("AnyType", objectType);
            typeMapping.put("object", objectType);
            typeMapping.put("array", "java.util.List");
            typeMapping.put("set", "java.util.Set");
            typeMapping.put("map", "java.util.Map");
            typeMapping.put("file", "byte[]");
        }

        if (additionalProperties.containsKey(DISABLE_HTML_ESCAPING)) {
            this.setDisableHtmlEscaping(Boolean.parseBoolean(additionalProperties.get(DISABLE_HTML_ESCAPING).toString()));
        }
        additionalProperties.put(DISABLE_HTML_ESCAPING, disableHtmlEscaping);

        if (additionalProperties.containsKey(IGNORE_ANYOF_IN_ENUM)) {
            this.setIgnoreAnyOfInEnum(Boolean.parseBoolean(additionalProperties.get(IGNORE_ANYOF_IN_ENUM).toString()));
        }
        additionalProperties.put(IGNORE_ANYOF_IN_ENUM, ignoreAnyOfInEnum);

        if (additionalProperties.containsKey(ADDITIONAL_MODEL_TYPE_ANNOTATIONS)) {
            var additionalAnnotationsList = additionalProperties.get(ADDITIONAL_MODEL_TYPE_ANNOTATIONS).toString();

            this.setAdditionalModelTypeAnnotations(Arrays.asList(additionalAnnotationsList.split(";")));
        }

        if (additionalProperties.containsKey(ADDITIONAL_ENUM_TYPE_ANNOTATIONS)) {
            var additionalAnnotationsList = additionalProperties.get(ADDITIONAL_ENUM_TYPE_ANNOTATIONS).toString();

            this.setAdditionalEnumTypeAnnotations(Arrays.asList(additionalAnnotationsList.split(";")));
        }

        if (additionalProperties.containsKey(CodegenConstants.API_PACKAGE)) {
            // guess from api package
            var derivedInvokerPackage = deriveInvokerPackageName((String) additionalProperties.get(CodegenConstants.API_PACKAGE));
            this.additionalProperties.put(CodegenConstants.INVOKER_PACKAGE, derivedInvokerPackage);
            this.setInvokerPackage((String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE));
            LOGGER.info("Invoker Package Name, originally not set, is now derived from api package name: {}", derivedInvokerPackage);
        } else if (additionalProperties.containsKey(CodegenConstants.MODEL_PACKAGE)) {
            // guess from model package
            String derivedInvokerPackage = deriveInvokerPackageName((String) additionalProperties.get(CodegenConstants.MODEL_PACKAGE));
            this.additionalProperties.put(CodegenConstants.INVOKER_PACKAGE, derivedInvokerPackage);
            this.setInvokerPackage((String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE));
            LOGGER.info("Invoker Package Name, originally not set, is now derived from model package name: {}",
                derivedInvokerPackage);
        } else {
            //not set, use default to be passed to template
            additionalProperties.put(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        }

        if (!additionalProperties.containsKey(CodegenConstants.MODEL_PACKAGE)) {
            additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);
        }

        if (!additionalProperties.containsKey(CodegenConstants.API_PACKAGE)) {
            additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);
        }


        if (additionalProperties.containsKey(DISCRIMINATOR_CASE_SENSITIVE)) {
            this.setDiscriminatorCaseSensitive(Boolean.parseBoolean(additionalProperties.get(DISCRIMINATOR_CASE_SENSITIVE).toString()));
        } else {
            // By default, the discriminator lookup should be case sensitive. There is nothing in the OpenAPI specification
            // that indicates the lookup should be case insensitive. However, some implementations perform
            // a case-insensitive lookup.
            this.setDiscriminatorCaseSensitive(Boolean.TRUE);
        }
        additionalProperties.put(DISCRIMINATOR_CASE_SENSITIVE, this.discriminatorCaseSensitive);

        importMapping.put("List", "java.util.List");
        importMapping.put("Set", "java.util.Set");

        this.sanitizeConfig();

        importMapping.put("Arrays", "java.util.Arrays");
        importMapping.put("Objects", "java.util.Objects");
    }

    @Override
    public Map<String, ModelsMap> updateAllModels(Map<String, ModelsMap> objs) {
        objs = super.updateAllModels(objs);
        var allModels = getAllModels(objs);
        for (var model : allModels.values()) {
            model.vendorExtensions.put("x-enable-validation", params.enableValidation);

            // All-vars visit
            for (var variable : model.allVars) {
                if (params.enableValidation) {
                    if (variable.getRef() != null) {
                        var variableModelField = allModels.get(variable.openApiType);
                        if (variableModelField != null && !variableModelField.isEnum) {
                            variable.vendorExtensions.put("x-has-valid-model", true);
                        }
                    }
                    this.visitVariableValidation(variable, variable.openApiType, variable.dataFormat, variable.vendorExtensions);
                }

                if (variable.isNullable && !variable.required) {
                    if (params.enableJsonNullable) {
                        variable.vendorExtensions.put("x-json-nullable", true);
                    } else {
                        variable.vendorExtensions.put("x-json-include-always", true);
                        //TODO remove in 2.0 and make default behavior that ENABLE_JSON_NULLABLE is enabled
                        LOGGER.warn("Detected isNullable and NonRequired field: {}#{}\nYou may want add option '{}' in configOptions to treat it as JsonNullable<T>, this will be default behavior in 2.0",
                            model.name, variable.name, ENABLE_JSON_NULLABLE);
                    }
                }
            }

            // Required-vars visit
            for (var variable : model.requiredVars) {
                if (params.enableValidation) {
                    if (variable.getRef() != null) {
                        var variableModelField = allModels.get(variable.openApiType);
                        if (variableModelField != null && !variableModelField.isEnum) {
                            variable.vendorExtensions.put("x-has-valid-model", true);
                        }
                    }
                    this.visitVariableValidation(variable, variable.openApiType, variable.dataFormat, variable.vendorExtensions);
                }
            }

            // Optional-vars visit
            for (var variable : model.optionalVars) {
                if (params.enableValidation) {
                    if (variable.getRef() != null) {
                        var variableModelField = allModels.get(variable.openApiType);
                        if (variableModelField != null && !variableModelField.isEnum) {
                            variable.vendorExtensions.put("x-has-valid-model", true);
                        }
                    }
                    this.visitVariableValidation(variable, variable.openApiType, variable.dataFormat, variable.vendorExtensions);
                }

                if (variable.isNullable && !variable.required) {
                    if (params.enableJsonNullable) {
                        variable.vendorExtensions.put("x-json-nullable", true);
                    } else {
                        variable.vendorExtensions.put("x-json-include-always", true);
                        //TODO remove in 2.0 and make default behavior that ENABLE_JSON_NULLABLE is enabled
                        LOGGER.warn("Detected isNullable and NonRequired field: {}#{}\nYou may want add option '{}' in configOptions to treat it as JsonNullable<T>, this will be default behavior in 2.0",
                            model.name, variable.name, ENABLE_JSON_NULLABLE);
                    }
                }
            }

            if (model.discriminator != null) {
                var map = model.discriminator.getMappedModels().stream()
                    .collect(Collectors.toMap(CodegenDiscriminator.MappedModel::getModelName, m -> List.of(m.getMappingName()), (l1, l2) -> {
                        var l = new ArrayList<String>(l1.size() + l2.size());
                        l.addAll(l1);
                        l.addAll(l2);
                        return l;
                    }, LinkedHashMap::new));
                model.allVars.removeIf(p -> p.name.equals(model.discriminator.getPropertyName()));
                var discriminatorProperty = new CodegenProperty();
                discriminatorProperty.name = model.discriminator.getPropertyName();
                discriminatorProperty.baseName = model.discriminator.getPropertyBaseName();
                discriminatorProperty.openApiType = model.discriminator.getPropertyType();
                if (model.discriminator.getMapping() != null) {
                    discriminatorProperty._enum = new ArrayList<>(model.discriminator.getMapping().keySet());
                } else {
                    discriminatorProperty._enum = new ArrayList<>();
                    for (var mappedModel : model.discriminator.getMappedModels()) {
                        discriminatorProperty._enum.add(mappedModel.getMappingName());
                    }
                }
                discriminatorProperty.allowableValues = new LinkedHashMap<>();
                ArrayList<Map<String, ?>> enumVars = new ArrayList<>();
                ArrayList<Map<String, ?>> enumVarsDeprecated = new ArrayList<>();
                discriminatorProperty.allowableValues.put("enumVars", enumVars);
                discriminatorProperty.allowableValues.put("enumVarsDeprecated", enumVarsDeprecated);
                for (var enumValue : discriminatorProperty._enum) {
                    var enumVar = toEnumVarName(enumValue, "String");
                    var enumStr = toEnumValue(enumValue, "String");
                    enumVars.add(Map.of("name", enumVar, "value", enumStr));

                    var enumVarDeprecated = toEnumVarNameDeprecated(enumValue, "String");
                    if (!enumVarDeprecated.equals(enumVar)) {
                        enumVarsDeprecated.add(Map.of("name", enumVarDeprecated,
                            "nameNew", enumVar,
                            "value", enumStr));
                    }
                }
                discriminatorProperty.datatypeWithEnum = toEnumName(discriminatorProperty);
                discriminatorProperty.dataType = "String";
                discriminatorProperty.isDiscriminator = true;
                discriminatorProperty.required = true;
                for (var mappedModel : model.discriminator.getMappedModels()) {
                    var childModel = allModels.get(mappedModel.getModelName());
                    if (childModel == null) {
                        throw new IllegalArgumentException("Child model '%s' not found, it is probably a free form object and is ignored by OpenAPI generator"
                            .formatted(mappedModel.getModelName()));
                    }

                    childModel.parentModel = model;
                    childModel.parent = model.classname;
                    var mappings = map.get(mappedModel.getModelName());
                    childModel.allVars.removeIf(p -> p.name.equals(model.discriminator.getPropertyName()));
                    childModel.optionalVars.removeIf(p -> p.name.equals(model.discriminator.getPropertyName()));
                    childModel.requiredVars.removeIf(p -> p.name.equals(model.discriminator.getPropertyName()));
                    var property = discriminatorProperty.clone();
                    childModel.allVars.add(0, property);
                    childModel.requiredVars.add(0, property);

                    for (CodegenProperty prop : childModel.optionalVars) {
                        if (prop.isOverridden != null && prop.isOverridden) {
                            if (model.optionalVars.stream().noneMatch(p -> p.name.equals(prop.name))) {
                                prop.isOverridden = false;
                            }
                        }
                    }

                    for (CodegenProperty prop : childModel.requiredVars) {
                        if (prop.isOverridden != null) {
                            boolean haveReqVar = model.requiredVars.stream().anyMatch(p -> p.name.equals(prop.name));
                            if (prop.isOverridden && !haveReqVar) {
                                prop.isOverridden = false;
                            } else {
                                prop.isOverridden = haveReqVar;
                            }
                        }
                    }

                    if (mappings.size() == 1) {
                        var enumValue = "%s.%s.%s".formatted(model.classname, discriminatorProperty.datatypeWithEnum, toEnumVarName(mappings.get(0), "String"));
                        property.vendorExtensions.put("x-discriminator-single", enumValue);
                        childModel.vendorExtensions.put("x-discriminator-single", enumValue);
                        var enumStringValue = "%s.%s.Constants.%s".formatted(model.classname, discriminatorProperty.datatypeWithEnum, toEnumVarName(mappings.get(0), "String"));
                        childModel.vendorExtensions.put("x-discriminator-value", params.codegenMode.isJava()
                            ? enumStringValue
                            : "[" + enumStringValue + "]");
                        var valuesCheck = new StringBuilder();
                        valuesCheck.append("if (").append(discriminatorProperty.name).append(" != ").append(enumValue).append(") {\n");
                        if (params.codegenMode.isKotlin()) {
                            valuesCheck.append("  throw IllegalArgumentException");
                        } else {
                            valuesCheck.append("  throw new IllegalArgumentException");
                        }
                        valuesCheck.append("(\"Unexpected value '\" + ").append(discriminatorProperty.name).append(" + \"' for variable ").append(discriminatorProperty.name).append("\");\n")
                            .append("}\n");
                        childModel.vendorExtensions.put("x-discriminator-values-check", valuesCheck.toString().indent(4));
                    } else {
                        assert mappings.size() > 1;
                        var discriminatorValue = mappings.stream()
                            .<String>map(it -> "%s.%s.Constants.%s".formatted(model.classname, discriminatorProperty.datatypeWithEnum, toEnumVarName(it, "String")))
                            .collect(Collectors.joining(
                                ",\n  ",
                                params.codegenMode.isJava() ? "{\n  " : "[\n  ",
                                params.codegenMode.isJava() ? "\n}" : "\n]"
                            ));
                        childModel.vendorExtensions.put("x-discriminator-value", discriminatorValue);
                        childModel.vendorExtensions.put("x-discriminator-keys", discriminatorValue);
                        var valuesCheck = new StringBuilder();
                        var discriminatorValueCheck = mappings.stream()
                            .map(it -> "%s != %s.%s.%s".formatted(discriminatorProperty.name, model.classname, discriminatorProperty.datatypeWithEnum, toEnumVarName(it, "String")))
                            .collect(Collectors.joining("\n  && "));
                        valuesCheck.append("if (").append(discriminatorValueCheck).append(") {\n");
                        if (params.codegenMode.isKotlin()) {
                            valuesCheck.append("  throw IllegalArgumentException");
                        } else {
                            valuesCheck.append("  throw new IllegalArgumentException");
                        }
                        valuesCheck.append("(\"Unexpected value '\" + ").append(discriminatorProperty.name).append(" + \"' for variable ").append(discriminatorProperty.name).append("\");\n")
                            .append("}\n");
                        childModel.vendorExtensions.put("x-discriminator-values-check", valuesCheck.toString().indent(4));
                    }
                }

                model.vendorExtensions.put("x-discriminator-property", discriminatorProperty);
                model.discriminator.getVendorExtensions().put("x-discriminator-property", discriminatorProperty);

                var uniqueMappedModels = model.discriminator.getMappedModels().stream().map(CodegenDiscriminator.MappedModel::getModelName).collect(Collectors.toSet());
                model.vendorExtensions.put("x-unique-mapped-models", uniqueMappedModels);
                model.discriminator.getVendorExtensions().put("x-unique-mapped-models", uniqueMappedModels);
            }

            if (params.codegenMode.isJava()) {
                for (var requiredVar : model.allVars) {
                    if (!requiredVar.required) {
                        continue;
                    }
                    if (requiredVar.isInteger) {
                        if (!typeMapping.containsKey("Integer") && !typeMapping.containsKey(Integer.class.getCanonicalName())) {
                            requiredVar.dataType = "int";
                            requiredVar.datatypeWithEnum = "int";
                        }
                    }
                    if (requiredVar.isLong) {
                        if (!typeMapping.containsKey("Long") && !typeMapping.containsKey(Long.class.getCanonicalName())) {
                            requiredVar.dataType = "long";
                            requiredVar.datatypeWithEnum = "long";
                        }
                    }
                    if (requiredVar.isFloat) {
                        if (!typeMapping.containsKey("Float") && !typeMapping.containsKey(Float.class.getCanonicalName())) {
                            requiredVar.dataType = "float";
                            requiredVar.datatypeWithEnum = "float";
                        }
                    }
                    if (requiredVar.isDouble) {
                        if (!typeMapping.containsKey("Double") && !typeMapping.containsKey(Double.class.getCanonicalName())) {
                            requiredVar.dataType = "double";
                            requiredVar.datatypeWithEnum = "double";
                        }
                    }
                    if (requiredVar.isBoolean) {
                        if (!typeMapping.containsKey("Boolean") && !typeMapping.containsKey(Boolean.class.getCanonicalName())) {
                            requiredVar.dataType = "boolean";
                            requiredVar.datatypeWithEnum = "boolean";
                        }
                    }
                }
            }
        }
        return objs;
    }

    private String getUpperSnakeCase(String value, Locale locale) {
        return Arrays.stream(value.split("[^a-zA-Z0-9]"))
            .map(String::strip)
            .flatMap(s -> Arrays.stream(s.split("(?<=[\\d+])(?=[A-Za-z])|(?<=[a-z])(?=[A-Z\\d])|(?<=[A-Z])(?=[A-Z][a-z])|( +)"))) // too much, don't guarantee how it works
            .map(String::strip)
            .map(s -> s.toUpperCase(locale))
            .collect(Collectors.joining("_"));
    }

    private <T extends IJsonSchemaValidationProperties> void visitVariableValidation(T variable, @Nullable String type, @Nullable String dataFormat, Map<String, Object> vendorExtensions) {
        dataFormat = Objects.requireNonNullElse(dataFormat, "");
        if (variable.getMinimum() != null || variable.getMaximum() != null) {
            vendorExtensions.put("x-has-min-max", true);
            if (variable.getMinimum() != null) {
                if (!variable.getMinimum().contains(".")) {
                    variable.setMinimum(variable.getMinimum() + ".0");
                }
            } else {
                switch (type) {
                    case "integer" -> {
                        switch (dataFormat) {
                            case "int64", "" -> variable.setMinimum(Long.MIN_VALUE + ".0");
                            case "int32" -> variable.setMinimum(Integer.MIN_VALUE + ".0");
                        }
                    }
                    case "number" -> {
                        switch (dataFormat) {
                            case "double", "" -> variable.setMinimum("Double.MIN_VALUE");
                            case "float" -> variable.setMinimum("Float.MIN_VALUE");
                        }
                    }
                }
            }
            if (variable.getMaximum() != null) {
                if (!variable.getMaximum().contains(".")) {
                    variable.setMaximum(variable.getMaximum() + ".0");
                }
            } else {
                switch (type) {
                    case "integer" -> {
                        switch (dataFormat) {
                            case "int64", "" -> variable.setMaximum(Long.MAX_VALUE + ".0");
                            case "int32" -> variable.setMaximum(Integer.MAX_VALUE + ".0");
                        }
                    }
                    case "number" -> {
                        switch (dataFormat) {
                            case "double", "" -> variable.setMaximum("Double.MAX_VALUE");
                            case "float" -> variable.setMaximum("Float.MAX_VALUE");
                        }
                    }
                }
            }
        }
        if (variable.getMinLength() != null || variable.getMaxLength() != null) {
            vendorExtensions.put("x-has-min-max-length", true);
            if (variable.getMinLength() == null) {
                variable.setMinLength(0);
            }
            if (variable.getMaxLength() == null) {
                variable.setMaxLength(Integer.MAX_VALUE);
            }
        }
        if (variable.getMaxItems() != null || variable.getMinItems() != null) {
            vendorExtensions.put("x-has-min-max-items", true);
            if (variable.getMinItems() == null) {
                variable.setMinItems(0);
            }
            if (variable.getMaxItems() == null) {
                variable.setMaxItems(Integer.MAX_VALUE);
            }
        }
        if (variable.getPattern() != null) {
            vendorExtensions.put("x-has-pattern", true);
        }
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        objs = super.postProcessAllModels(objs);
        objs = updateAllModels(objs);

        if (!additionalModelTypeAnnotations.isEmpty()) {
            for (var modelName : objs.keySet()) {
                var models = (Map<String, Object>) objs.get(modelName);
                models.put(ADDITIONAL_MODEL_TYPE_ANNOTATIONS, additionalModelTypeAnnotations);
            }
        }

        if (!additionalEnumTypeAnnotations.isEmpty()) {
            for (var modelName : objs.keySet()) {
                var models = (Map<String, Object>) objs.get(modelName);
                models.put(ADDITIONAL_ENUM_TYPE_ANNOTATIONS, additionalEnumTypeAnnotations);
            }
        }
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

        this.setInvokerPackage(sanitizePackageName(invokerPackage));
        if (additionalProperties.containsKey(CodegenConstants.INVOKER_PACKAGE)) {
            this.additionalProperties.put(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
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
            var items = getSchemaItems((ArraySchema) schema);
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
                if (params.enableJsonNullable) {
                    if (property.required) {
                        return getSchemaType(target) + "<String, ru.tinkoff.kora.json.common.JsonNullable<" + getTypeDeclaration(inner) + ">>";
                    } else {
                        return getSchemaType(target) + "<String, ru.tinkoff.kora.json.common.JsonNullable<" + getTypeDeclaration(inner) + ">>?";
                    }
                } else {
                    return getSchemaType(target) + "<String, " + getTypeDeclaration(inner) + "?>";
                }
            } else if (property.isNullable && !property.required && params.enableJsonNullable) {
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
                    ? "kotlin.collections.setOf<%s>("
                    : "java.util.Set.<%s>of(";
            } else {
                pattern = params.codegenMode.isKotlin()
                    ? "kotlin.collections.listOf<%s>("
                    : "java.util.List.<%s>of(";
            }

            Schema<?> itemOriginal = getSchemaItems((ArraySchema) schema);
            Schema itemSchema = ModelUtils.getReferencedSchema(this.openAPI, itemOriginal);
            String typeDeclaration = getTypeDeclaration(ModelUtils.unaliasSchema(this.openAPI, itemOriginal));
            Object java8obj = additionalProperties.get("java8");
            if (java8obj != null) {
                Boolean java8 = Boolean.valueOf(java8obj.toString());
                if (java8 != null && java8) {
                    typeDeclaration = "";
                }
            }

            var builder = new StringBuilder(String.format(Locale.ROOT, pattern, typeDeclaration));

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

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        if (!fullJavaUtil) {
            if ("array".equals(property.containerType)) {
                model.imports.add("ArrayList");
            } else if ("set".equals(property.containerType)) {
                model.imports.add("LinkedHashSet");
            } else if ("map".equals(property.containerType)) {
                model.imports.add("HashMap");
            }
        }

        if (!BooleanUtils.toBoolean(model.isEnum)) {
            // needed by all pojos, but not enums
            model.imports.add("ApiModelProperty");
            model.imports.add("ApiModel");
        }
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

    protected void handleImplicitHeaders(CodegenOperation operation, boolean implicitHeaders, @Nullable Pattern implicitHeadersRegex) {
        if (operation.allParams.isEmpty()) {
            return;
        }

        final ArrayList<CodegenParameter> copy = new ArrayList<>(operation.allParams);
        operation.allParams.clear();

        for (CodegenParameter p : copy) {
            if (p.isHeaderParam && (implicitHeaders || shouldBeImplicitHeader(p, implicitHeadersRegex))) {
                operation.implicitHeadersParams.add(p);
                operation.headerParams.removeIf(header -> header.baseName.equals(p.baseName));
                LOGGER.info("Update operation [{}]. Remove header [{}] because it's marked to be implicit", operation.operationId, p.baseName);
            } else {
                operation.allParams.add(p);
            }
        }

        if (!operation.implicitHeadersParams.isEmpty()) {
            operation.vendorExtensions.put("x-has-implicit-headers", true);
        }

        operation.hasParams = !operation.allParams.isEmpty();
    }

    protected boolean shouldBeImplicitHeader(CodegenParameter parameter, @Nullable Pattern implicitHeadersRegex) {
        return implicitHeadersRegex != null && implicitHeadersRegex.matcher(parameter.baseName).matches();
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        // Remove imports of List, ArrayList, Map and HashMap as they are
        // imported in the template already.
        var imports = (List<Map<String, String>>) objs.get("imports");
        var pattern = Pattern.compile("java\\.util\\.(List|ArrayList|Map|HashMap)");
        for (var itr = imports.iterator(); itr.hasNext(); ) {
            var itrImport = itr.next().get("import");
            if (pattern.matcher(itrImport).matches()) {
                itr.remove();
            }
        }

        var httpClientAnnotationParams = new HashMap<String, String>();

        record AuthMethodGroup(String name, List<CodegenSecurity> methods) {}

        var authMethods = (List<AuthMethodGroup>) this.vendorExtensions.computeIfAbsent("authMethods", k -> new ArrayList<AuthMethodGroup>());
        var tags = (Set<String>) this.vendorExtensions.computeIfAbsent("tags", k -> new TreeSet<String>());
        var operations = (Map<String, Object>) objs.get("operations");
        if (params.clientConfigPrefix != null) {
            httpClientAnnotationParams.put("configPath", "\"" + params.clientConfigPrefix + "." + operations.get("classname") + "\"");
        }
        var operationList = (List<CodegenOperation>) operations.get("operation");
        for (var op : operationList) {
            handleImplicitHeaders(op, params.implicitHeaders, params.implicitHeadersRegex);
            var operationImports = new TreeSet<String>();
            for (var p : op.allParams) {
                if (importMapping.containsKey(p.dataType)) {
                    operationImports.add(importMapping.get(p.dataType));
                }
            }

            TagClient tagClient = null;
            for (var entry : params.clientTags.entrySet()) {
                if (op.tags.stream().anyMatch(t -> t.getName().equals(entry.getKey()))) {
                    tagClient = entry.getValue();
                    break;
                }
            }
            if (tagClient == null) {
                tagClient = params.clientTags.get("*");
            }
            if (tagClient != null) {
                String suffix = params.codegenMode.isKotlin() ? "::class" : ".class";
                if (tagClient.httpClientTag != null) {
                    String httpTag = tagClient.httpClientTag.endsWith(suffix) ? tagClient.httpClientTag : tagClient.httpClientTag + suffix;
                    httpTag = (params.codegenMode.isKotlin()) ? "[" + httpTag + "]" : httpTag;
                    httpClientAnnotationParams.put("httpClientTag", httpTag);
                }
                if (tagClient.telemetryTag != null) {
                    String telemetryTag = tagClient.telemetryTag.endsWith(suffix) ? tagClient.telemetryTag : tagClient.telemetryTag + suffix;
                    telemetryTag = (params.codegenMode.isKotlin()) ? "[" + telemetryTag + "]" : telemetryTag;
                    httpClientAnnotationParams.put("telemetryTag", telemetryTag);
                }
            }

            List<Interceptor> interceptors = null;
            for (var entry : params.interceptors.entrySet()) {
                if (op.tags.stream().anyMatch(t -> t.getName().equals(entry.getKey()))) {
                    interceptors = entry.getValue();
                    break;
                }
            }
            if (interceptors == null) {
                interceptors = params.interceptors.get("*");
            }
            if (interceptors != null && !interceptors.isEmpty()) {
                final String serverDefault = "ru.tinkoff.kora.http.server.common.HttpServerInterceptor";
                final String clientDefault = "ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor";

                var interceptorTemplates = interceptors.stream()
                    .filter(i -> i.type != null || i.tag != null)
                    .map(i -> {
                        String implValue;
                        if (i.type() == null) {
                            implValue = (params.codegenMode.isClient())
                                ? clientDefault
                                : serverDefault;
                        } else {
                            implValue = i.type();
                        }

                        String suffix = params.codegenMode.isKotlin() ? "::class" : ".class";
                        String impl = implValue.endsWith(suffix) ? implValue : implValue + suffix;
                        List<String> tag;
                        if (i.tag() == null) {
                            tag = null;
                        } else if (i.tag() instanceof String ts) {
                            tag = List.of(ts);
                        } else if (i.tag() instanceof List tls) {
                            tag = tls.stream()
                                .<String>map(Object::toString)
                                .toList();
                        } else {
                            throw new IllegalArgumentException("Unknown interceptors tag value: " + i.tag());
                        }

                        if (tag == null || tag.isEmpty()) {
                            return Map.of("interceptorImpl", impl);
                        } else {
                            String interTags = tag.stream()
                                .map(t -> t.endsWith(suffix) ? t : t + suffix)
                                .collect(Collectors.joining(", "));

                            return Map.of(
                                "interceptorImpl", impl,
                                "interceptorTags", interTags
                            );
                        }
                    }).toList();

                if (!interceptorTemplates.isEmpty()) {
                    objs.put("koraInterceptors", interceptorTemplates);
                }
            }

            List<AdditionalAnnotation> additionalContractAnnotations = null;
            for (var entry : params.additionalContractAnnotations.entrySet()) {
                if (op.tags.stream().anyMatch(t -> t.getName().equals(entry.getKey()))) {
                    additionalContractAnnotations = entry.getValue();
                    break;
                }
            }
            if (additionalContractAnnotations == null) {
                additionalContractAnnotations = params.additionalContractAnnotations.get("*");
            }
            if (additionalContractAnnotations != null && !additionalContractAnnotations.isEmpty()) {
                List<String> annotations = additionalContractAnnotations.stream()
                    .map(AdditionalAnnotation::annotation)
                    .filter(a -> a != null && !a.isBlank())
                    .toList();

                if (!annotations.isEmpty()) {
                    objs.put("koraAdditionalContractAnnotations", annotations);
                }
            }

            op.vendorExtensions.put("requestInDelegateParams", params.requestInDelegateParams);
            op.vendorExtensions.put("x-java-import", operationImports);
            var multipartForm = op.consumes != null && op.consumes.stream()
                .map(m -> m.get("mediaType"))
                .anyMatch("multipart/form-data"::equalsIgnoreCase);
            var urlEncodedForm = op.consumes != null && op.consumes.stream()
                .map(m -> m.get("mediaType"))
                .anyMatch("application/x-www-form-urlencoded"::equalsIgnoreCase);
            op.vendorExtensions.put("multipartForm", multipartForm);
            op.vendorExtensions.put("urlEncodedForm", urlEncodedForm);
            for (var response : op.responses) {
                var hasData = response.hasHeaders || response.isDefault || response.dataType != null;
                response.vendorExtensions.put("jsonTag", params.jsonAnnotation);
                response.vendorExtensions.put("hasData", hasData);
                if (response.isBinary && !"byte[]".equals(response.dataType) && !"ByteBuffer".equals(response.dataType) && !"String".equals(response.dataType)) {
                    response.vendorExtensions.put("isBinaryUnknownType", true);
                }
            }
            if (op.bodyParam != null) {
                if (op.bodyParam.isBinary) {
                    op.bodyParam.vendorExtensions.put("hasMapperTag", false);
                } else if (isContentJson(op.bodyParam)) {
                    op.bodyParam.vendorExtensions.put("hasMapperTag", true);
                    op.bodyParam.vendorExtensions.put("mapperTag", params.jsonAnnotation);
                }
                for (var param : op.allParams) {
                    if (param.isBodyParam && param.isBinary) {
                        op.bodyParam.vendorExtensions.put("hasMapperTag", false);
                    } else if (param.isBodyParam && (isContentJson(param))) {
                        param.vendorExtensions.put("hasMapperTag", true);
                        param.vendorExtensions.put("mapperTag", params.jsonAnnotation);
                    }
                }
            }
            var formParamsWithMappers = new ArrayList<Map<String, Object>>();
            for (var formParam : op.formParams) {
                if (formParam.isModel) {
                    formParam.vendorExtensions.put("requiresMapper", true);
                    var type = allModels.stream()
                        .filter(m -> m.getModel().name.equals(formParam.dataType))
                        .findFirst()
                        .map(m -> m.get("importPath").toString())
                        .orElseThrow();

                    if (isContentJson(formParam)) {
                        formParam.vendorExtensions.put("mapperTag", params.jsonAnnotation);
                        formParamsWithMappers.add(new HashMap<>(Map.of(
                            "paramName", formParam.paramName,
                            "requireTag", true,
                            "mapperTag", params.jsonAnnotation,
                            "paramType", type,
                            "last", false
                        )));
                    } else {
                        formParamsWithMappers.add(new HashMap<>(Map.of(
                            "paramName", formParam.paramName,
                            "requireTag", false,
                            "paramType", type,
                            "last", false
                        )));
                    }
                }
            }
            if (!formParamsWithMappers.isEmpty()) {
                formParamsWithMappers.get(formParamsWithMappers.size() - 1).put("last", true);
                op.vendorExtensions.put("requiresFormParamMappers", true);
                op.vendorExtensions.put("formParamMappers", formParamsWithMappers);
            }
            for (var response : op.responses) {
                if (isContentJson(response.getContent())) {
                    response.vendorExtensions.put("hasMapperTag", true);
                    response.vendorExtensions.put("mapperTag", params.jsonAnnotation);
                }
            }
            int lastIdx = 0;
            for (int i = op.responses.size() - 1; i >= 0; i--) {
                var response = op.responses.get(i);
                if (response.dataType != null) {
                    lastIdx = i;
                    response.vendorExtensions.put("hasMore", false);
                    break;
                }
            }
            for (int i = 0; i < lastIdx; i++) {
                var response = op.responses.get(i);
                response.vendorExtensions.put("hasMore", true);
            }
            for (var response : op.responses) {
                if (response.isBinary) {
                    var i = response.getContent().keySet().iterator();
                    if (i.hasNext()) {
                        response.vendorExtensions.put("contentType", i.next());
                    } else {
                        response.vendorExtensions.put("contentType", "application/octet-stream");
                    }
                }
            }
            op.vendorExtensions.put("singleResponse", op.responses.size() == 1);
            for (var response : op.responses) {
                response.vendorExtensions.put("singleResponse", op.responses.size() == 1);
            }
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
                        copy.vendorExtensions.put("isLast", i == op.authMethods.size() - 1);
                        copy.vendorExtensions.put("isFirst", i == 0);
                        copy.vendorExtensions.put("hasScopes", copy.scopes != null && !copy.scopes.isEmpty());
                    }

                    tags.add(authInterceptorTag);
                    if (authMethods.stream().noneMatch(a -> a.name.equals(authInterceptorTag))) {
                        authMethods.add(new AuthMethodGroup(authInterceptorTag, security));
                    }


                    op.vendorExtensions.put("authInterceptorTag", authInterceptorTag);
                } else {
                    if (op.authMethods.size() == 1 || params.primaryAuth == null) {
                        if (op.authMethods.size() > 1) {
                            Set<String> secSchemes = op.authMethods.stream()
                                .map(s -> s.name)
                                .collect(Collectors.toSet());

                            LOGGER.warn("Found multiple securitySchemes {} for {} {} it is recommended to specify preferred securityScheme using `primaryAuth` property, or the first random will be used",
                                secSchemes, op.httpMethod, op.path);
                        }

                        CodegenSecurity authMethod = op.authMethods.get(0);
                        if (params.authAsMethodArgument) {
                            CodegenParameter fakeAuthParameter = getAuthArgumentParameter(authMethod, op.allParams);
                            op.allParams.add(fakeAuthParameter);
                        } else {
                            var authName = camelize(toVarName(authMethod.name));
                            tags.add(upperCase(authName));
                            op.vendorExtensions.put("authInterceptorTag", authName);
                        }
                    } else {
                        CodegenSecurity authMethod = op.authMethods.stream()
                            .filter(a -> a.name.equals(params.primaryAuth))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Can't find OpenAPI securitySchema named: " + params.primaryAuth));

                        if (params.authAsMethodArgument) {
                            CodegenParameter fakeAuthParameter = getAuthArgumentParameter(authMethod, op.allParams);
                            op.allParams.add(fakeAuthParameter);
                        } else {
                            var authName = camelize(toVarName(authMethod.name));
                            tags.add(upperCase(authName));
                            op.vendorExtensions.put("authInterceptorTag", authName);
                        }
                    }
                }
            }
            if (params.enableValidation) {
                List<AdditionalAnnotation> additionalAnnotations = List.of();
                for (Tag tag : op.tags) {
                    additionalAnnotations = params.additionalContractAnnotations.get(tag.getName());
                    if (additionalAnnotations != null) {
                        break;
                    }
                }
                if (additionalAnnotations == null) {
                    additionalAnnotations = params.additionalContractAnnotations.get("*");
                }
                op.vendorExtensions.put("allowAspects", params.enableValidation() || !additionalAnnotations.isEmpty());

                for (var p : op.allParams) {
                    var validation = false;
                    if (p.isModel) {
                        for (var variable : p.vars) {
                            if (variable.hasValidation) {
                                validation = true;
                                break;
                            }
                        }
                        if (!validation) {
                            var model = allModels.stream()
                                .map(mm -> mm.get("model"))
                                .map(CodegenModel.class::cast)
                                .filter(m -> m.name.equals(p.dataType))
                                .findFirst();

                            if (model.isPresent()) {
                                for (var child : Objects.requireNonNullElse(model.get().children, List.<CodegenModel>of())) {
                                    for (var variable : child.vars) {
                                        if (variable.hasValidation) {
                                            validation = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } else if (p.isArray) {
                        if (p.hasValidation) {
                            validation = true;
                        }
                    } else {
                        if (p.hasValidation) {
                            validation = true;
                        }
                    }
                    if (validation) {
                        p.vendorExtensions.put("x-validate", true);
                        op.vendorExtensions.put("x-validate", true);
                        var type = p.getSchema() != null ? p.getSchema().openApiType : null;
                        visitVariableValidation(p, type, p.dataFormat, p.vendorExtensions);
                    }
                }
            }
            if (params.codegenMode.isJava()) {
                for (var allParam : op.allParams) {
                    if (!allParam.required) {
                        continue;
                    }
                    if (allParam.isInteger) {
                        if (!typeMapping.containsKey("Integer") && !typeMapping.containsKey(Integer.class.getCanonicalName())) {
                            allParam.dataType = "int";
                        }
                    }
                    if (allParam.isLong) {
                        if (!typeMapping.containsKey("Long") && !typeMapping.containsKey(Long.class.getCanonicalName())) {
                            allParam.dataType = "long";
                        }
                    }
                    if (allParam.isFloat) {
                        if (!typeMapping.containsKey("Float") && !typeMapping.containsKey(Float.class.getCanonicalName())) {
                            allParam.dataType = "float";
                        }
                    }
                    if (allParam.isDouble) {
                        if (!typeMapping.containsKey("Double") && !typeMapping.containsKey(Double.class.getCanonicalName())) {
                            allParam.dataType = "double";
                        }
                    }
                    if (allParam.isBoolean) {
                        if (!typeMapping.containsKey("Boolean") && !typeMapping.containsKey(Boolean.class.getCanonicalName())) {
                            allParam.dataType = "boolean";
                        }
                    }
                }
            }

            if (params.codegenMode.isClient()) {
                var requiredParams = new ArrayList<CodegenParameter>();
                var optionalParams = new ArrayList<CodegenParameter>();
                for (var param : op.allParams) {
                    if (param.isHeaderParam && params.implicitHeaders) {
                        continue;
                    }

                    if (param.notRequiredOrIsNullable() && !param.isPathParam) {
                        optionalParams.add(param);
                        param.vendorExtensions.put("x-optional-params", optionalParams);
                        op.vendorExtensions.put("x-have-optional", true);
                    } else {
                        requiredParams.add(param);
                        param.vendorExtensions.put("x-required-params", requiredParams);
                    }
                }

                op.vendorExtensions.put("x-required-params", requiredParams);
                op.vendorExtensions.put("x-optional-params", optionalParams);
            }
        }
        if (params.codegenMode.isClient()) {
            var annotationParams = httpClientAnnotationParams.entrySet()
                .stream()
                .map(e -> e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining(", ", "(", ")"));
            objs.put("annotationParams", annotationParams);
        }
        return objs;
    }

    private static String getAuthName(String name, List<CodegenParameter> parameters) {
        for (CodegenParameter parameter : parameters) {
            if (name.equals(parameter.paramName)) {
                return getAuthName("_" + name, parameters);
            }
        }

        return name;
    }

    private CodegenParameter getAuthArgumentParameter(CodegenSecurity authMethod, List<CodegenParameter> parameters) {
        CodegenParameter fakeAuthParameter = new CodegenParameter();

        String authName = getAuthName(authMethod.name, parameters);

        fakeAuthParameter.paramName = authName;
        fakeAuthParameter.baseName = authName;
        fakeAuthParameter.nameInLowerCase = authName.toLowerCase(Locale.ROOT);
        if (authMethod.isKeyInQuery) {
            fakeAuthParameter.isQueryParam = true;
        } else if (authMethod.isKeyInHeader) {
            fakeAuthParameter.isHeaderParam = true;
        } else if (authMethod.isKeyInCookie) {
            fakeAuthParameter.isCookieParam = true;
        } else if (authMethod.isOAuth
                   || authMethod.isOpenId
                   || authMethod.isBasicBearer
                   || authMethod.isBasic
                   || authMethod.isBasicBasic) {
            fakeAuthParameter.isHeaderParam = true;

            for (CodegenParameter parameter : parameters) {
                if ("Authorization".equalsIgnoreCase(parameter.paramName)) {
                    throw new IllegalArgumentException("Authorization argument as method parameter can't be set, cause parameter named 'Authorization' already is present");
                }
            }

            fakeAuthParameter.paramName = "Authorization";
            fakeAuthParameter.baseName = "Authorization";
            fakeAuthParameter.nameInLowerCase = "Authorization".toLowerCase(Locale.ROOT);
        } else {
            throw new IllegalStateException("Auth argument can be in Query, Header or Cookie, but wasn't unknown");
        }

        fakeAuthParameter.dataType = "String";
        fakeAuthParameter.baseType = "String";
        fakeAuthParameter.description = authMethod.description;
        fakeAuthParameter.unescapedDescription = authMethod.description;
        fakeAuthParameter.required = true;
        fakeAuthParameter.isString = true;
        fakeAuthParameter.isNull = false;
        fakeAuthParameter.isNullable = false;

        Schema schema = SchemaTypeUtil.createSchema("String", null);
        CodegenProperty codegenProperty = fromProperty(authName, schema);
        fakeAuthParameter.setSchema(codegenProperty);
        return fakeAuthParameter;
    }

    private CodegenProperty getFakeCodegenPropertyString(String name) {
        CodegenProperty fakeProperty = new CodegenProperty();

        fakeProperty.name = name;
        fakeProperty.baseName = name;
        fakeProperty.baseType = "String";
        fakeProperty.dataType = "String";
        fakeProperty.datatypeWithEnum = "String";
        fakeProperty.nameInLowerCase = name.toLowerCase(Locale.ROOT);
        fakeProperty.required = true;
        fakeProperty.isString = true;
        fakeProperty.isNull = false;
        fakeProperty.isNullable = false;

        return fakeProperty;
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

        if (ignoreAnyOfInEnum) {
            // Alter OpenAPI schemas ignore anyOf keyword if it consist of an enum. Example:
            //     anyOf:
            //     - type: string
            //       enum:
            //       - ENUM_A
            //       - ENUM_B
            Stream.concat(
                    Stream.of(openAPI.getComponents().getSchemas()),
                    openAPI.getComponents().getSchemas().values().stream()
                        .filter(schema -> schema.getProperties() != null)
                        .map(Schema::getProperties))
                .forEach(schemas -> schemas.replaceAll(
                    (name, s) -> Stream.of(s)
                        .filter(schema -> schema instanceof ComposedSchema)
                        .map(schema -> (ComposedSchema) schema)
                        .filter(schema -> Objects.nonNull(schema.getAnyOf()))
                        .flatMap(schema -> schema.getAnyOf().stream())
                        .filter(schema -> Objects.nonNull(schema.getEnum()))
                        .findFirst()
                        .orElse((Schema) s)));
        }
        var securitySchemas = openAPI.getComponents().getSecuritySchemes();
        if (!Objects.requireNonNullElse(securitySchemas, Map.of()).isEmpty()) {
            switch (params.codegenMode) {
                case JAVA_CLIENT, JAVA_ASYNC_CLIENT, JAVA_REACTIVE_CLIENT -> {
                    var securitySchemaClass = apiFileFolder() + File.separator + "ApiSecurity.java";
                    this.supportingFiles.add(new SupportingFile("javaClientSecuritySchema.mustache", securitySchemaClass));
                }
                case JAVA_SERVER, JAVA_ASYNC_SERVER, JAVA_REACTIVE_SERVER -> {
                    var securitySchemaClass = apiFileFolder() + File.separator + "ApiSecurity.java";
                    this.supportingFiles.add(new SupportingFile("javaServerSecuritySchema.mustache", securitySchemaClass));
                }
                case KOTLIN_CLIENT, KOTLIN_SUSPEND_CLIENT -> {
                    var securitySchemaClass = apiFileFolder() + File.separator + "ApiSecurity.kt";
                    this.supportingFiles.add(new SupportingFile("kotlinClientSecuritySchema.mustache", securitySchemaClass));
                }
                case KOTLIN_SERVER, KOTLIN_SUSPEND_SERVER -> {
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

    public String getInvokerPackage() {
        return invokerPackage;
    }

    public void setInvokerPackage(String invokerPackage) {
        this.invokerPackage = invokerPackage;
    }


    private String sanitizePath(String p) {
        //prefer replace a ", instead of a fuLL URL encode for readability
        return p.replaceAll("\"", "%22");
    }

    /**
     * Set whether discriminator value lookup is case-sensitive or not.
     *
     * @param discriminatorCaseSensitive true if the discriminator value lookup should be case sensitive.
     */
    public void setDiscriminatorCaseSensitive(boolean discriminatorCaseSensitive) {
        this.discriminatorCaseSensitive = discriminatorCaseSensitive;
    }

    public void setDisableHtmlEscaping(boolean disabled) {
        this.disableHtmlEscaping = disabled;
    }

    public void setIgnoreAnyOfInEnum(boolean ignoreAnyOfInEnum) {
        this.ignoreAnyOfInEnum = ignoreAnyOfInEnum;
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
        return booleanGetterPrefix + getterAndSetterCapitalize(name);
    }

    @Override
    public String sanitizeTag(String tag) {
        tag = camelize(underscore(sanitizeName(tag)));

        // tag starts with numbers
        if (tag.matches("^\\d.*")) {
            tag = "Class" + tag;
        }
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


    public void setAdditionalModelTypeAnnotations(final List<String> additionalModelTypeAnnotations) {
        this.additionalModelTypeAnnotations = additionalModelTypeAnnotations;
    }

    public void setAdditionalEnumTypeAnnotations(final List<String> additionalEnumTypeAnnotations) {
        this.additionalEnumTypeAnnotations = additionalEnumTypeAnnotations;
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
            ;
    }

    @Override
    public void postProcess() {
    }

    private boolean isNeedGenerateModule() {
        return params.delegateMethodBodyMode.isDelegateMethodBodyModeNeedsDefaultComponent();
    }
}
