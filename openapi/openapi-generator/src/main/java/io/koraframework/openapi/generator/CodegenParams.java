package io.koraframework.openapi.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.jspecify.annotations.Nullable;
import org.openapitools.codegen.CliOption;

import java.util.*;
import java.util.regex.Pattern;

public class CodegenParams {
    public static final String CODEGEN_MODE = "mode";
    public static final String ENABLE_VALIDATION = "enableServerValidation";
    public static final String DISCRIMINATOR_CASE_SENSITIVE = "discriminatorCaseSensitive";
    public static final String PRIMARY_AUTH = "primaryAuth";
    public static final String CLIENT_CONFIG = "clientConfig";
    public static final String CLIENT_CONFIG_PREFIX = "clientConfigPrefix";
    public static final String SECURITY_CONFIG_PREFIX = "securityConfigPrefix";
    public static final String CLIENT_TAGS = "tags";
    public static final String REQUEST_DELEGATE_PARAMS = "requestInDelegateParams";
    public static final String EXTENSIONS = "extensions";
    public static final String SERVER_CONFIG_PREFIX = "serverConfigPrefix";
    public static final String AUTH_AS_METHOD_ARGUMENT = "authAsMethodArgument";
    public static final String FILTER_WITH_MODELS = "filterWithModels";
    public static final String PREFIX_PATH = "prefixPath";
    public static final String DELEGATE_METHOD_BODY_MODE = "delegateMethodBodyMode";
    public static final String IMPLICIT_HEADERS = "implicitHeaders";
    public static final String IMPLICIT_HEADERS_REGEX = "implicitHeadersRegex";
    public static final String FORCE_INCLUDE_OPTIONAL = "forceIncludeOptional";
    public static final String RAW_BODY_MODE = "rawBodyMode";
    public static final String USE_SECURITY_DECLARATION_ORDER = "useSecurityDeclarationOrder";
    public static final String SECURITY_REQUIREMENT_MODE = "securityRequirementMode";
    public static final String CLIENT_RESPONSE_MODE = "clientResponseMode";
    
    public CodegenMode codegenMode = CodegenMode.JAVA_CLIENT;
    public boolean enableValidation = false;
    public boolean authAsMethodArgument = false;
    public @Nullable String primaryAuth = null;
    public @Nullable String clientConfig = null;
    public @Nullable String clientConfigPrefix = null;
    public String securityConfigPrefix = null;
    public Map<String, KoraCodegen.TagClient> clientTags = new HashMap<>();
    public GeneratorExtensions extensions = new GeneratorExtensions(null, new HashMap<>(), new HashMap<>());
    public boolean requestInDelegateParams = false;
    public boolean filterWithModels = false;
    public @Nullable String prefixPath = "";
    public String serverConfigPrefix = "httpServer.controller.%{ControllerTypeNameInCamelCase}";
    public DelegateMethodBodyMode delegateMethodBodyMode = DelegateMethodBodyMode.NONE;
    public boolean implicitHeaders = false;
    public @Nullable Pattern implicitHeadersRegex = null;
    public boolean forceIncludeOptional = false;
    public RawBodyMode rawBodyMode = RawBodyMode.BYTES;
    public boolean useSecurityDeclarationOrder = false;
    public SecurityRequirementMode securityRequirementMode = SecurityRequirementMode.STANDARD;
    public ClientResponseMode clientResponseMode = ClientResponseMode.SEALED;

    static List<CliOption> cliOptions() {
        var cliOptions = new ArrayList<CliOption>();
        cliOptions.add(CliOption.newString(CODEGEN_MODE, "Generation mode (one of java, reactive or kotlin)"));
        cliOptions.add(CliOption.newString(SECURITY_CONFIG_PREFIX, "Config prefix for security config parsers"));
        cliOptions.add(CliOption.newString(PRIMARY_AUTH, "Specify primary HTTP client securityScheme if multiple are available for method"));
        cliOptions.add(CliOption.newString(CLIENT_CONFIG, "Generated client config path"));
        cliOptions.add(CliOption.newString(CLIENT_CONFIG_PREFIX, "Generated client config prefix for per-client config paths"));
        cliOptions.add(CliOption.newString(CLIENT_TAGS, "Json containing http client tags configuration for apis"));
        cliOptions.add(CliOption.newString(EXTENSIONS, "Json containing generator extensions for annotations and interceptors"));
        cliOptions.add(CliOption.newString(SERVER_CONFIG_PREFIX, "Generated server controller config prefix for extension annotation substitution"));
        cliOptions.add(CliOption.newBoolean(ENABLE_VALIDATION, "Generate validation related annotation on models and controllers"));
        cliOptions.add(CliOption.newBoolean(REQUEST_DELEGATE_PARAMS, "Generate HttpServerRequest parameter in delegate methods"));
        cliOptions.add(CliOption.newBoolean(AUTH_AS_METHOD_ARGUMENT, "HTTP client authorization as method argument"));
        cliOptions.add(CliOption.newBoolean(FILTER_WITH_MODELS, "If enabled then when openapiNormalizer FILTER option is specified, will try to filter not only operations, but all unused models as well"));
        cliOptions.add(CliOption.newString(PREFIX_PATH, "Path prefix for HTTP Server controllers"));
        cliOptions.add(CliOption.newString(DELEGATE_METHOD_BODY_MODE, "Delegate method generation mode"));
        cliOptions.add(CliOption.newString(FORCE_INCLUDE_OPTIONAL, "If enabled forces Nullable and NonRequired fields to be included ALWAYS even if null, can't be enabled with enableJsonNullable simultaneously"));
        cliOptions.add(CliOption.newString(RAW_BODY_MODE, "Bare object request and response body mode (one of BYTES, RAW)"));
        cliOptions.add(CliOption.newBoolean(USE_SECURITY_DECLARATION_ORDER, "Use OpenAPI security requirement declaration order when generating auth tags and interceptors"));
        cliOptions.add(CliOption.newString(SECURITY_REQUIREMENT_MODE, "Security requirement interpretation mode (one of STANDARD, ALWAYS_OR)"));
        cliOptions.add(CliOption.newString(CLIENT_RESPONSE_MODE, "HTTP client response generation mode (one of SEALED, SUCCESSFUL)"));
        return cliOptions;
    }

    static CodegenParams parse(Map<String, Object> additionalProperties) {
        var params = new CodegenParams();
        if (additionalProperties.containsKey(CODEGEN_MODE)) {
            params.codegenMode = CodegenMode.ofMode(additionalProperties.get(CODEGEN_MODE).toString());
        }
        if (additionalProperties.containsKey(CLIENT_TAGS)) {
            var clientTagsJson = additionalProperties.get(CLIENT_TAGS).toString();
            try {
                params.clientTags = new ObjectMapper().readerFor(TypeFactory.defaultInstance().constructMapType(Map.class, String.class, KoraCodegen.TagClient.class)).readValue(clientTagsJson);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if (additionalProperties.containsKey(EXTENSIONS)) {
            params.extensions = parseExtensions(additionalProperties.get(EXTENSIONS).toString());
        }
        if (additionalProperties.containsKey(PRIMARY_AUTH)) {
            params.primaryAuth = additionalProperties.get(PRIMARY_AUTH).toString();
        }
        if (additionalProperties.containsKey(AUTH_AS_METHOD_ARGUMENT)) {
            params.authAsMethodArgument = Boolean.parseBoolean(additionalProperties.get(AUTH_AS_METHOD_ARGUMENT).toString());
        }
        if (additionalProperties.containsKey(CLIENT_CONFIG)) {
            params.clientConfig = additionalProperties.get(CLIENT_CONFIG).toString();
        }
        if (additionalProperties.containsKey(CLIENT_CONFIG_PREFIX)) {
            params.clientConfigPrefix = additionalProperties.get(CLIENT_CONFIG_PREFIX).toString();
        }
        if (additionalProperties.containsKey(SECURITY_CONFIG_PREFIX)) {
            params.securityConfigPrefix = additionalProperties.get(SECURITY_CONFIG_PREFIX).toString();
        }
        if (additionalProperties.containsKey(ENABLE_VALIDATION) && params.codegenMode.isServer()) {
            params.enableValidation = Boolean.parseBoolean(additionalProperties.get(ENABLE_VALIDATION).toString());
        }
        if (additionalProperties.containsKey(REQUEST_DELEGATE_PARAMS) && params.codegenMode.isServer()) {
            params.requestInDelegateParams = Boolean.parseBoolean(additionalProperties.get(REQUEST_DELEGATE_PARAMS).toString());
        }
        if (additionalProperties.containsKey(FILTER_WITH_MODELS)) {
            params.filterWithModels = Boolean.parseBoolean(additionalProperties.get(FILTER_WITH_MODELS).toString());
        }
        if (additionalProperties.containsKey(PREFIX_PATH)) {
            params.prefixPath = additionalProperties.get(PREFIX_PATH).toString();
        }
        if (additionalProperties.containsKey(SERVER_CONFIG_PREFIX)) {
            params.serverConfigPrefix = additionalProperties.get(SERVER_CONFIG_PREFIX).toString();
        }
        if (additionalProperties.containsKey(DELEGATE_METHOD_BODY_MODE)) {
            params.delegateMethodBodyMode = DelegateMethodBodyMode.of(additionalProperties.get(DELEGATE_METHOD_BODY_MODE).toString());
        }
        if (additionalProperties.containsKey(IMPLICIT_HEADERS)) {
            params.implicitHeaders = Boolean.parseBoolean(additionalProperties.get(IMPLICIT_HEADERS).toString());
        }
        if (additionalProperties.containsKey(IMPLICIT_HEADERS_REGEX)) {
            params.implicitHeadersRegex = Optional.ofNullable(additionalProperties.get(IMPLICIT_HEADERS_REGEX).toString())
                .filter(s -> !s.isBlank())
                .map(s -> Pattern.compile(((String) s)))
                .orElse(null);
        }
        if (additionalProperties.containsKey(FORCE_INCLUDE_OPTIONAL)) {
            params.forceIncludeOptional = Boolean.parseBoolean(additionalProperties.get(FORCE_INCLUDE_OPTIONAL).toString());
        }
        if (additionalProperties.containsKey(RAW_BODY_MODE)) {
            params.rawBodyMode = RawBodyMode.of(additionalProperties.get(RAW_BODY_MODE).toString());
        }
        if (additionalProperties.containsKey(USE_SECURITY_DECLARATION_ORDER)) {
            params.useSecurityDeclarationOrder = Boolean.parseBoolean(additionalProperties.get(USE_SECURITY_DECLARATION_ORDER).toString());
        }
        if (additionalProperties.containsKey(SECURITY_REQUIREMENT_MODE)) {
            params.securityRequirementMode = SecurityRequirementMode.of(additionalProperties.get(SECURITY_REQUIREMENT_MODE).toString());
        }
        if (additionalProperties.containsKey(CLIENT_RESPONSE_MODE)) {
            params.clientResponseMode = ClientResponseMode.of(additionalProperties.get(CLIENT_RESPONSE_MODE).toString());
        }
        return params;
    }

    private static GeneratorExtensions parseExtensions(String json) {
        var objectMapper = new ObjectMapper();
        try {
            var root = objectMapper.readTree(json);
            return new GeneratorExtensions(
                parseExtension(root.get("*")),
                parseExtensionMap(root.get("tags")),
                parseExtensionMap(root.get("operations"))
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, GeneratorExtension> parseExtensionMap(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return new HashMap<>();
        }
        var result = new HashMap<String, GeneratorExtension>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), parseExtension(entry.getValue())));
        return result;
    }

    private static @Nullable GeneratorExtension parseExtension(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new GeneratorExtension(
            parseStringList(node.get("additionalMethodAnnotations")),
            parseStringList(node.get("additionalTypeAnnotations")),
            parseStringList(node.get("additionalModelTypeAnnotations")),
            parseStringList(node.get("additionalEnumTypeAnnotations")),
            text(node.get("interceptorType")),
            parseStringList(node.get("interceptorTag")),
            parseClientMapping(node.get("clientMapping"))
        );
    }

    private static @Nullable ClientMapping parseClientMapping(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        var type = text(node.get("type"));
        if (type == null || type.isBlank()) {
            return null;
        }
        return new ClientMapping(type);
    }

    private static @Nullable String text(@Nullable JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static List<String> parseStringList(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            var result = new ArrayList<String>();
            node.forEach(value -> result.add(value.asText()));
            return result;
        }
        return List.of(node.asText());
    }

    public record GeneratorExtensions(@Nullable GeneratorExtension global,
                                      Map<String, GeneratorExtension> tags,
                                      Map<String, GeneratorExtension> operations) {}

    public record GeneratorExtension(List<String> additionalMethodAnnotations,
                                     List<String> additionalTypeAnnotations,
                                     List<String> additionalModelTypeAnnotations,
                                     List<String> additionalEnumTypeAnnotations,
                                     @Nullable String interceptorType,
                                     List<String> interceptorTag,
                                     @Nullable ClientMapping clientMapping) {}

    public record ClientMapping(String type) {}

    public enum RawBodyMode {
        BYTES,
        RAW;

        public static RawBodyMode of(String value) {
            return RawBodyMode.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public enum ClientResponseMode {
        SEALED,
        SUCCESSFUL;

        public static ClientResponseMode of(String value) {
            return ClientResponseMode.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public enum SecurityRequirementMode {
        STANDARD,
        ALWAYS_OR;

        public static SecurityRequirementMode of(String value) {
            return SecurityRequirementMode.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
