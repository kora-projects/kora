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

        assertTrue(content.contains("public enum class NonReqDoubleEnum private constructor("));
        assertTrue(content.contains("public val `value`: Double"));
        assertTrue(content.contains("`delegate`: io.koraframework.json.common.JsonWriter<Double>"));
        assertTrue(content.contains("`delegate`: io.koraframework.json.common.JsonReader<Double>"));
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

        assertTrue(apiContent.indexOf("tag = ApiSecurity.OperationSecuritySchemaTag0::class") < apiContent.indexOf("optionalAccess("));
        assertTrue(apiContent.lastIndexOf("OperationSecuritySchemaTag") < apiContent.indexOf("publicAccess("));
        assertFalse(securityContent.contains("if ()"));
        assertFalse(securityContent.contains("Security schema is defined for api but no data was provided"));
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

        // @ConfigSource is rejected by the config processor on a plain class
        assertTrue(content.contains("@ConfigSource"), content);
        assertTrue(content.contains("public data class basicAuthConfig"), content);
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
    }
}
