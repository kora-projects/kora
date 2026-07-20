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

        assertTrue(e.getMessage().contains("clientConfig is required for kotlin-client"));
        assertTrue(e.getMessage().contains("httpClient.petstoreV3"));
    }

    @Test
    void extensionsCanOverrideClientMapping() throws Exception {
        var files = generate(
            "petstoreV3_kotlin_extensions_mapping",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options()
                .setExtensions("""
                    {
                      "operations": {
                        "createPets": {
                          "clientMapping": {
                            "type": "ru.example.CreatePetsMapper"
                          }
                        }
                      }
                    }
                    """)
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApi.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("import ru.example.CreatePetsMapper"));
        assertTrue(apiContent.contains("@Mapping(value = CreatePetsMapper::class)"));
        assertFalse(apiContent.contains("CreatePets200ApiResponseMapper::class"));
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
        assertTrue(content.contains("public val _statusCode: Int"));
        assertTrue(content.contains("public data class GetErrors400ApiResponse("));
        assertTrue(content.contains(": GetErrorsModelErrorApiResponse"));
        assertTrue(content.contains("get() = 400"));
    }

    @Test
    void successfulClientResponseModeReturnsSuccessAndThrowsTypedException() throws Exception {
        var files = generate(
            "petstoreV3_client_successful_response",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_client_successful_response.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApi.kt"))
            .findFirst()
            .orElseThrow());
        var mapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiClientResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("@Mapping(value = PetsApiClientResponseMappers.CreatePetSuccessfulResponseMapper::class)"));
        assertTrue(apiContent.contains("public fun createPet("));
        assertTrue(apiContent.contains("CreatePet200ApiResponse"));
        assertTrue(apiContent.contains("FindPetPetApiResponse"));
        assertTrue(apiContent.contains("AmbiguousPetApiResponse"));
        assertTrue(apiContent.contains("public class PetsApiCreatePetHttpClientResponseException("));
        assertTrue(apiContent.contains("val response:"));
        assertTrue(apiContent.contains("CreatePetApiResponse"));
        assertTrue(mapperContent.contains("public open class CreatePetSuccessfulResponseMapper("));
        assertTrue(mapperContent.contains("HttpClientResponseMapper<"));
        assertTrue(mapperContent.contains("CreatePet200ApiResponse"));
        assertTrue(mapperContent.contains("400 -> throw PetsApi.PetsApiCreatePetHttpClientResponseException(response.code(), response.headers(), this.createPet400ResponseMapper.apply(response))"));
        assertTrue(mapperContent.contains("FindPetPetApiResponse"));
        assertFalse(apiContent.contains("@Mapping(value = PetsApiClientResponseMappers.AmbiguousPetSuccessfulResponseMapper::class)"));
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
    void bareObjectRequestAndResponseAreGeneratedAsRawHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_raw",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("RAW")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.kt"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());
        var modelContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());
        var errorContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("ErrorMessage.kt"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("public fun storeInventory("));
        assertTrue(apiContent.contains("public fun rawObject("));
        assertTrue(apiContent.contains("@Header"));
        assertTrue(apiContent.contains("additionalHeaders: HttpHeaders"));
        assertTrue(apiContent.contains("body: HttpBodyOutput"));
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
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("public open class StoreInventory200ApiResponseMapper"));
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

        assertTrue(apiContent.contains("public fun storeInventory("));
        assertTrue(apiContent.contains("public fun rawObject("));
        assertTrue(apiContent.contains("@Header"));
        assertTrue(apiContent.contains("additionalHeaders: HttpHeaders"));
        assertTrue(apiContent.contains("body: ByteArray"));
        assertTrue(responsesContent.contains("public val content: ByteArray"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpClientResponseMapper<ByteArray>"));
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("public open class StoreInventory200ApiResponseMapper"));
    }
}
