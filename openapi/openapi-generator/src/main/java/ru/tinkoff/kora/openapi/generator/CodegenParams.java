package ru.tinkoff.kora.openapi.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
    public static final String CLIENT_CONFIG_PREFIX = "clientConfigPrefix";
    public static final String SECURITY_CONFIG_PREFIX = "securityConfigPrefix";
    public static final String CLIENT_TAGS = "tags";
    public static final String REQUEST_DELEGATE_PARAMS = "requestInDelegateParams";
    public static final String INTERCEPTORS = "interceptors";
    public static final String ADDITIONAL_CONTRACT_ANNOTATIONS = "additionalContractAnnotations";
    public static final String AUTH_AS_METHOD_ARGUMENT = "authAsMethodArgument";
    public static final String FILTER_WITH_MODELS = "filterWithModels";
    public static final String PREFIX_PATH = "prefixPath";
    public static final String DELEGATE_METHOD_BODY_MODE = "delegateMethodBodyMode";
    public static final String IMPLICIT_HEADERS = "implicitHeaders";
    public static final String IMPLICIT_HEADERS_REGEX = "implicitHeadersRegex";
    public static final String FORCE_INCLUDE_OPTIONAL = "forceIncludeOptional";
    
    public CodegenMode codegenMode = CodegenMode.JAVA_CLIENT;
    public boolean enableValidation = false;
    public boolean authAsMethodArgument = false;
    public @Nullable String primaryAuth = null;
    public @Nullable String clientConfigPrefix = null;
    public String securityConfigPrefix = null;
    public Map<String, KoraCodegen.TagClient> clientTags = new HashMap<>();
    public Map<String, List<KoraCodegen.Interceptor>> interceptors = new HashMap<>();
    public Map<String, List<KoraCodegen.AdditionalAnnotation>> additionalContractAnnotations = new HashMap<>();
    public boolean requestInDelegateParams = false;
    public boolean filterWithModels = false;
    public @Nullable String prefixPath = "";
    public DelegateMethodBodyMode delegateMethodBodyMode = DelegateMethodBodyMode.NONE;
    public boolean implicitHeaders = false;
    public @Nullable Pattern implicitHeadersRegex = null;
    public boolean forceIncludeOptional = false;

    static List<CliOption> cliOptions() {
        var cliOptions = new ArrayList<CliOption>();
        cliOptions.add(CliOption.newString(CODEGEN_MODE, "Generation mode (one of java, reactive or kotlin)"));
        cliOptions.add(CliOption.newString(SECURITY_CONFIG_PREFIX, "Config prefix for security config parsers"));
        cliOptions.add(CliOption.newString(PRIMARY_AUTH, "Specify primary HTTP client securityScheme if multiple are available for method"));
        cliOptions.add(CliOption.newString(CLIENT_CONFIG_PREFIX, "Generated client config prefix"));
        cliOptions.add(CliOption.newString(CLIENT_TAGS, "Json containing http client tags configuration for apis"));
        cliOptions.add(CliOption.newString(INTERCEPTORS, "Json containing interceptors for HTTP server/client"));
        cliOptions.add(CliOption.newBoolean(ENABLE_VALIDATION, "Generate validation related annotation on models and controllers"));
        cliOptions.add(CliOption.newBoolean(REQUEST_DELEGATE_PARAMS, "Generate HttpServerRequest parameter in delegate methods"));
        cliOptions.add(CliOption.newString(ADDITIONAL_CONTRACT_ANNOTATIONS, "Additional annotations for HTTP client/server methods"));
        cliOptions.add(CliOption.newBoolean(AUTH_AS_METHOD_ARGUMENT, "HTTP client authorization as method argument"));
        cliOptions.add(CliOption.newBoolean(FILTER_WITH_MODELS, "If enabled then when openapiNormalizer FILTER option is specified, will try to filter not only operations, but all unused models as well"));
        cliOptions.add(CliOption.newString(PREFIX_PATH, "Path prefix for HTTP Server controllers"));
        cliOptions.add(CliOption.newString(DELEGATE_METHOD_BODY_MODE, "Delegate method generation mode"));
        cliOptions.add(CliOption.newString(FORCE_INCLUDE_OPTIONAL, "If enabled forces Nullable and NonRequired fields to be included ALWAYS even if null, can't be enabled with enableJsonNullable simultaneously"));
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
        if (additionalProperties.containsKey(INTERCEPTORS)) {
            var interceptorJson = additionalProperties.get(INTERCEPTORS).toString();
            try {
                params.interceptors = new ObjectMapper().readerFor(TypeFactory.defaultInstance()
                        .constructType(new TypeReference<Map<String, List<KoraCodegen.Interceptor>>>() {}))
                    .readValue(interceptorJson);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if (additionalProperties.containsKey(ADDITIONAL_CONTRACT_ANNOTATIONS)) {
            var json = additionalProperties.get(ADDITIONAL_CONTRACT_ANNOTATIONS).toString();
            try {
                params.additionalContractAnnotations = new ObjectMapper().readerFor(TypeFactory.defaultInstance()
                        .constructType(new TypeReference<Map<String, List<KoraCodegen.AdditionalAnnotation>>>() {}))
                    .readValue(json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if (additionalProperties.containsKey(PRIMARY_AUTH)) {
            params.primaryAuth = additionalProperties.get(PRIMARY_AUTH).toString();
        }
        if (additionalProperties.containsKey(AUTH_AS_METHOD_ARGUMENT)) {
            params.authAsMethodArgument = Boolean.parseBoolean(additionalProperties.get(AUTH_AS_METHOD_ARGUMENT).toString());
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
        return params;
    }
}
