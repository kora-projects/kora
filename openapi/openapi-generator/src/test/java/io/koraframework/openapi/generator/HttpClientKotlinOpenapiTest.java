package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class HttpClientKotlinOpenapiTest extends BaseKotlinOpenapiTest {
    @ParameterizedTest
    @MethodSource("generateParams")
    void test(SwaggerParams params) throws Exception {
        process(
            params.name(),
            "kotlin-client",
            params.spec(),
            params.options()
        );
    }

    @Test
    void clientConfigIsUsedAsSingleConfigPath() throws Exception {
        var files = generate(
            "petstoreV3_single_config",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig("httpClient.petstoreV3")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("Api.kt"))
            .filter(path -> {
                try {
                    return Files.readString(path).contains("@HttpClient");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("value = \"httpClient.petstoreV3\""));
        assertFalse(content.contains("httpClient.petstoreV3."));
    }

    @Test
    void clientConfigPrefixAppendsLowerCamelClientName() throws Exception {
        var files = generate(
            "petstoreV3_prefix_config",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options()
                .setClientConfig(null)
                .setClientConfigPrefix("httpClient")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("Api.kt"))
            .filter(path -> {
                try {
                    return Files.readString(path).contains("@HttpClient");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("value = \"httpClient.petsApi\""));
    }

    @Test
    void securityConfigUsesDedicatedNamesComponentAndPrefix() throws Exception {
        var files = generate(
            "petstoreV3_security_config_contract",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options()
                .setClientConfigPrefix("clients")
                .setSecurityConfigPrefix("security")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("fun securityConfig("));
        assertTrue(content.indexOf("@DefaultComponent") < content.indexOf("fun securityConfig("));
        assertTrue(content.contains("data class SecurityConfig("));
        assertTrue(content.contains("@Generated(\"io.koraframework.openapi.generator.kotlingen.ClientSecuritySchemaGenerator\")\n  public data class SecurityConfig("));
        assertTrue(content.contains("data class SecurityBasicAuthConfig("));
        assertTrue(content.contains("public val apiKeyAuth: String?,"));
        assertTrue(content.contains("public val basicAuth: SecurityBasicAuthConfig?,"));
        assertTrue(content.contains("public val cookieAuth: String?,"));
        assertTrue(content.contains("public val username: String?,"));
        assertTrue(content.contains("public val password: String?,"));
        assertTrue(content.contains("mapper.map(config.get(\"security.apiKeyAuth\"))"));
        assertFalse(content.contains("mapper.mapOrThrow"));
        assertTrue(content.contains("config.get(\"security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"security.basicAuth.username\")"));
        assertTrue(content.contains("config.get(\"security.cookieAuth\")"));
        assertFalse(content.contains("config.get(\"clients."));
        assertFalse(content.contains("@ConfigSource"));
    }

    @Test
    void cookieSecurityIsAddedToRequest() throws Exception {
        var files = generate(
            "petstoreV3_security_cookie_client_interceptor",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_cookie.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("_securityCookieHeader = if (_securityCookieHeader.isNullOrBlank()) \"X-COOKIE-KEY=\" + CookieAuth"));
        assertTrue(content.contains("b.header(\"Cookie\", _securityCookieHeader)"));
        assertFalse(content.contains("Cookie client authentication is not implemented yet"));
        assertFalse(content.contains("TODO("));
    }

    @Test
    void multipleCookieSecuritySchemesAreCombinedWithExistingCookies() throws Exception {
        var files = generate(
            "petstoreV3_security_cookie_and_client_interceptor",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_cookie_and.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("var _securityCookieHeader = request.headers().getFirst(\"Cookie\")"));
        assertTrue(content.contains("\"X-COOKIE-KEY-1=\" + cookieAuth1"));
        assertTrue(content.contains("_securityCookieHeader + \"; \" + \"X-COOKIE-KEY-2=\" + cookieAuth2"));
        assertTrue(content.contains("b.header(\"Cookie\", _securityCookieHeader)"));
    }

    @Test
    void securityConfigFallsBackToClientConfigPrefix() throws Exception {
        var files = generate(
            "petstoreV3_security_client_prefix_fallback",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfigPrefix("clients")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("config.get(\"clients.security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"clients.security.basicAuth.username\")"));
    }

    @Test
    void securityConfigFallsBackToClientConfig() throws Exception {
        var files = generate(
            "petstoreV3_security_client_config_fallback",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig("clients.petstore")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("config.get(\"clients.petstore.security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"clients.petstore.security.basicAuth.username\")"));
    }

    @Test
    void clientConfigIsRequiredWhenPrefixIsMissing() {
        var e = assertThrows(IllegalArgumentException.class, () -> generate(
            "petstoreV3_missing_config",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig(null)
        ));

        assertTrue(e.getMessage().contains("Missing OpenAPI generator `clientConfig`"));
        assertTrue(e.getMessage().contains("Generation mode `kotlin-client`"));
        assertTrue(e.getMessage().contains("httpClient.petstoreV3"));
    }

    @Test
    void sameResponseModelGetsSharedInterface() throws Exception {
        var files = generate(
            "petstoreV3_same_response_model",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_same_response_model.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("ApiResponses.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("public interface GetErrorsModelErrorApiResponse : GetErrorsApiResponse"));
        assertTrue(content.contains("public val content: ModelError"));
        assertFalse(content.contains("public val message: String"));
        assertFalse(content.contains("public val details: String?"));
        assertTrue(content.contains("public val statusCode: Int"));
        assertTrue(content.contains("public data class GetErrors400ApiResponse("));
        assertTrue(content.contains(": GetErrorsModelErrorApiResponse"));
        assertTrue(content.contains("get() = 400"));
        assertFalse(content.contains("get() = content.details"));
    }

    @Test
    void enumValueTypesSupportDouble() throws Exception {
        var files = generate(
            "petstoreV3_enum",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_enum.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());
        var moduleContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet__NestedEnumMapperModule.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("public enum class NonReqDoubleEnum private constructor("));
        assertTrue(content.contains("public val `value`: Double"));
        assertFalse(content.contains("class JsonWriter"));
        assertTrue(moduleContent.contains("nonReqDoubleEnumJsonWriter("));
        assertTrue(moduleContent.contains("JsonWriter<Double>"));
        assertTrue(moduleContent.contains("JsonReader<Double>"));
    }

    @Test
    void nestedEnumMappersAreAggregatedByModel() throws Exception {
        var files = generate(
            "petstoreV3_validation_nested_enum",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_validation.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var moduleContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetTO__NestedEnumMapperModule.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(moduleContent.contains("public interface PetTO__NestedEnumMapperModule"));
        assertTrue(moduleContent.contains("JsonWriter<PetTO.StatusEnum>"));
        assertTrue(moduleContent.contains("JsonReader<PetTO.StatusEnum>"));
        assertTrue(moduleContent.contains("JsonWriter<PetTO.AvailabilityEnum>"));
        assertTrue(moduleContent.contains("JsonReader<PetTO.AvailabilityEnum>"));
        assertEquals(1, files.stream()
            .filter(file -> file.getName().startsWith("PetTO") && file.getName().endsWith("NestedEnumMapperModule.kt"))
            .count());
    }

    @Test
    void anonymousSecurityDoesNotRequireClientInterceptor() throws Exception {
        var files = generate(
            "petstoreV3_security_anonymous",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_anonymous.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PublicApi.kt"))
            .findFirst()
            .orElseThrow());
        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.indexOf("tag = ApiSecurity.Sec1_Anonymous::class") < apiContent.indexOf("optionalAccess("));
        assertTrue(apiContent.indexOf("tag = ApiSecurity.Sec1::class") < apiContent.indexOf("requiredAccess("));
        assertTrue(securityContent.contains("class Sec1_Anonymous"));
        assertTrue(securityContent.contains("class Sec1"));
        assertTrue(apiContent.lastIndexOf("OperationSecuritySchemaTag") < apiContent.indexOf("publicAccess("));
        assertFalse(securityContent.contains("if ()"));
        assertTrue(securityContent.contains("return chain.process(request)"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_body",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("BODY")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.kt"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());
        var modelContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());
        var errorContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("ErrorMessage.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("public fun storeInventory(@Header additionalHeaders: HttpHeaders, body: HttpBodyOutput): DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(apiContent.contains("public fun rawObject(@Header additionalHeaders: HttpHeaders, body: HttpBodyOutput): DefaultApiResponses.RawObjectApiResponse"));
        assertEquals(3, countJavadocReturnTags(apiContent));
        assertTrue(containsMultilineStoreInventoryReturn(apiContent));
        assertTrue(responsesContent.contains("public sealed interface StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("public data class StoreInventory200ApiResponse("));
        assertTrue(responsesContent.contains("public val content: HttpBodyInput"));
        assertTrue(responsesContent.contains("public data class StoreInventory400ApiResponse("));
        assertTrue(responsesContent.contains("public val content: ErrorMessage"));
        assertTrue(responsesContent.contains("public data class StoreInventory500ApiResponse("));
        assertTrue(responsesContent.contains("public val content: HttpBodyInput"));
        assertTrue(responsesContent.contains("public sealed interface RawObjectApiResponse"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(modelContent.contains("public data class Pet("));
        assertTrue(modelContent.contains("public val metadata: Any"));
        assertTrue(modelContent.contains("public val optionalMetadata: Any? = null"));
        assertTrue(errorContent.contains("public data class ErrorMessage("));
        assertTrue(errorContent.contains("public val message: String"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsObjectTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_object",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("OBJECT")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.kt"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("body: Any"));
        assertTrue(apiContent.contains("DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(apiContent.contains("DefaultApiResponses.RawObjectApiResponse"));
        assertFalse(apiContent.contains("additionalHeaders: HttpHeaders"));
        assertTrue(responsesContent.contains("public val content: Any"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpClientResponseMapper<Any>"));
        assertTrue(responseMapperContent.contains("@Json"));
    }

    @Test
    void bareObjectRequestAndResponseUseByteArrayByDefault() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_bytes_default",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.kt"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("public fun storeInventory(@Header additionalHeaders: HttpHeaders, body: ByteArray): DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(apiContent.contains("public fun rawObject(@Header additionalHeaders: HttpHeaders, body: ByteArray): DefaultApiResponses.RawObjectApiResponse"));
        assertTrue(responsesContent.contains("public val content: ByteArray"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpClientResponseMapper<ByteArray>"));
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("public open class StoreInventory200ApiResponseMapper"));
    }

    @Test
    void basicAuthConfigIsGeneratedAsDataClass() throws Exception {
        var files = generate(
            "petstoreV3_security_basic_data_class",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_basic.yaml").toExternalForm(),
            new SwaggerParams.Options());

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertFalse(content.contains("@ConfigSource"), content);
        assertTrue(content.contains("@DefaultComponent\n  public fun securityConfig("), content);
        assertTrue(content.contains("public data class SecurityBasicAuthConfig"), content);
    }

    @Test
    void securityTagsUseSchemeNames() throws Exception {
        var files = generate(
            "petstoreV3_security_all_named_tags",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("class BearerAuth"));
        assertTrue(securityContent.contains("class ApiKeyAuth"));
        assertTrue(securityContent.contains("class BasicAuth"));
        assertTrue(securityContent.contains("class CookieAuth"));
        assertTrue(securityContent.contains("class OAuth"));
        assertFalse(securityContent.contains("class bearerAuth"));
        assertTrue(securityContent.contains("class BearerAuth_ApiKeyAuth_BasicAuth_CookieAuth_OAuth"));
        assertFalse(securityContent.contains("ReadPets"));
        assertFalse(securityContent.contains("WritePets"));
        assertFalse(securityContent.contains("OperationSecuritySchemaTag"));
    }
}
