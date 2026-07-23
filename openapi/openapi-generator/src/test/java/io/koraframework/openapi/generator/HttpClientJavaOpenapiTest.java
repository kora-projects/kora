package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class HttpClientJavaOpenapiTest extends BaseJavaOpenapiTest {
    @ParameterizedTest
    @MethodSource("generateParams")
    void test(SwaggerParams params) throws Exception {
        process(
            params.name(),
            "java-client",
            params.spec(),
            params.options()
        );
    }

    @Test
    void clientConfigIsUsedAsSingleConfigPath() throws Exception {
        var files = generate(
            "petstoreV3_single_config",
            "java-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig("httpClient.petstoreV3")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("Api.java"))
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
            "java-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options()
                .setClientConfig(null)
                .setClientConfigPrefix("httpClient")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("Api.java"))
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
            "java-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig(null)
        ));

        assertTrue(e.getMessage().contains("clientConfig is required for java-client"));
        assertTrue(e.getMessage().contains("httpClient.petstoreV3"));
    }

    @Test
    void sameResponseModelGetsSharedInterface() throws Exception {
        var files = generate(
            "petstoreV3_same_response_model",
            "java-client",
            getClass().getResource("/example/petstoreV3_same_response_model.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("ApiResponses.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("sealed interface GetErrorsModelErrorApiResponse extends GetErrorsApiResponse"));
        assertTrue(content.contains("ModelError content()"));
        assertTrue(content.contains("default String message()"));
        assertTrue(content.contains("default @Nullable String details()"));
        assertTrue(content.contains("int _statusCode()"));
        assertTrue(content.contains("record GetErrors400ApiResponse(ModelError content) implements GetErrorsModelErrorApiResponse"));
        assertTrue(content.contains("return 400"));
        assertTrue(content.contains("return this.content().details()"));
    }

    @Test
    void javadocsIncludeOpenapiModelAndOperationMetadata() throws Exception {
        var files = generate(
            "petstoreV2_javadocs",
            "java-client",
            getClass().getResource("/example/petstoreV2.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var petContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.java"))
            .findFirst()
            .orElseThrow());
        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetApi.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(petContent.contains("* Pet - A pet for sale in the pet store"));
        assertTrue(petContent.contains("* @param status pet status in the store"));
        assertTrue(petContent.contains("* @param name name (example: doggie)"));
        assertTrue(apiContent.contains("* POST /pet : Add a new pet to the store"));
        assertTrue(apiContent.contains("* @param body Pet object that needs to be added to the store (required)"));
        assertTrue(apiContent.contains("* @return Invalid input (status code 405)"));
    }

    @Test
    void enumMappersAreGeneratedAsModuleFactories() throws Exception {
        var files = generate(
            "petstoreV3_filter",
            "java-client",
            getClass().getResource("/example/petstoreV3_filter.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var petDogContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetDog.java"))
            .findFirst()
            .orElseThrow());

        var moduleContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("$PetDogBreedEnumMapperModule.java"))
            .findFirst()
            .orElseThrow());

        assertFalse(petDogContent.contains("MapperModule"));
        assertTrue(petDogContent.contains("DINGO_DON(\"Dingo-Don\")"));
        assertTrue(petDogContent.contains("NUMBER_5(5)"));
        assertFalse(petDogContent.contains("Constants."));
        assertFalse(petDogContent.contains("public static final class Constants"));
        assertFalse(petDogContent.contains("public static final class JsonWriter"));
        assertFalse(petDogContent.contains("public static final class JsonReader"));
        assertFalse(petDogContent.contains("public static final class StringParameterConverter"));
        assertTrue(petDogContent.contains("* Dingo breed"));
        assertTrue(petDogContent.contains("* enum with int value"));

        assertTrue(moduleContent.contains("public interface $PetDogBreedEnumMapperModule"));
        assertTrue(moduleContent.contains("@DefaultComponent"));
        assertTrue(moduleContent.contains("default JsonWriter<PetDog.BreedEnum> breedEnumJsonWriter()"));
        assertTrue(moduleContent.contains("default JsonReader<PetDog.BreedEnum> breedEnumJsonReader()"));
        assertTrue(moduleContent.contains("default HttpClientParameterWriter<PetDog.BreedEnum> breedEnumStringParameterConverter()"));
        assertTrue(moduleContent.contains("new EnumJsonWriter<>(PetDog.BreedEnum.values(), PetDog.BreedEnum::getValue, (gen, object) ->"));
        assertTrue(moduleContent.contains("new EnumJsonReader<>(PetDog.BreedEnum.values(), PetDog.BreedEnum::getValue, parser -> switch (parser.currentToken())"));
    }

    @Test
    void enumMappersUseJsonDelegateForNonInlineValueTypes() throws Exception {
        var files = generate(
            "petstoreV3_enum",
            "java-client",
            getClass().getResource("/example/petstoreV3_enum.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var moduleContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("$PetNonReqDoubleEnumMapperModule.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(moduleContent.contains("default JsonWriter<Pet.NonReqDoubleEnum> nonReqDoubleEnumJsonWriter(JsonWriter<Double> delegate)"));
        assertTrue(moduleContent.contains("return new EnumJsonWriter<>(Pet.NonReqDoubleEnum.values(), Pet.NonReqDoubleEnum::getValue, delegate)"));
        assertTrue(moduleContent.contains("default JsonReader<Pet.NonReqDoubleEnum> nonReqDoubleEnumJsonReader(JsonReader<Double> delegate)"));
        assertTrue(moduleContent.contains("return new EnumJsonReader<>(Pet.NonReqDoubleEnum.values(), Pet.NonReqDoubleEnum::getValue, delegate)"));
        assertFalse(moduleContent.contains("parser -> switch"));
    }

    @Test
    void recordsGetWithBuilderMethods() throws Exception {
        var files = generate(
            "petstoreV3_enum",
            "java-client",
            getClass().getResource("/example/petstoreV3_enum.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("public Pet withId(long id)"));
        assertTrue(content.contains("if (this.id == id) return this; return new Pet(id, this.nullableType"));
        assertTrue(content.contains("public Pet withNonReqDouble(@Nullable NonReqDoubleEnum nonReqDouble)"));
        assertTrue(content.contains("if (Objects.equals(this.nonReqDouble, nonReqDouble)) return this; return new Pet(this.id, this.nullableType"));
        assertFalse(content.contains("* (nonReqDouble)"));

        var filesWithDefaults = generate(
            "petstoreV3_types",
            "java-client",
            getClass().getResource("/example/petstoreV3_types.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var contentWithDefaults = Files.readString(filesWithDefaults.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(contentWithDefaults.contains("* (default: 1)"));
    }

    @Test
    void optionalArgsAreGeneratedAsMutableClasses() throws Exception {
        var files = generate(
            "petstoreV3_request_parameters",
            "java-client",
            getClass().getResource("/example/petstoreV3_request_parameters.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApi.java"))
            .findFirst()
            .orElseThrow());
        var optionalArgsContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiListPetsOptArgs.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("PetsApiListPetsOptArgs optionalArguments"));
        assertFalse(apiContent.contains("final class ListPetsOptArgs"));
        assertTrue(optionalArgsContent.contains("public final class PetsApiListPetsOptArgs"));
        assertFalse(optionalArgsContent.contains("record PetsApiListPetsOptArgs"));
        assertTrue(optionalArgsContent.contains("public static PetsApiListPetsOptArgs empty()"));
        assertTrue(optionalArgsContent.contains("public static PetsApiListPetsOptArgs defaults()"));
        assertTrue(optionalArgsContent.contains("private PetsApiListPetsOptArgs("));
        assertTrue(optionalArgsContent.contains("private @Nullable Integer intOptional;"));
        assertTrue(optionalArgsContent.contains("public @Nullable Integer intOptional()"));
        assertTrue(optionalArgsContent.contains("this.intOptional = intOptional;"));
        assertTrue(optionalArgsContent.contains("public PetsApiListPetsOptArgs withIntOptional(Integer intOptional)"));
        assertTrue(optionalArgsContent.contains("return this;"));
    }

    @Test
    void bareObjectPropertiesAreGeneratedAsObject() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_bytes_default",
            "java-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("public record Pet(long id, Object metadata, @Nullable Object optionalMetadata)"));
        assertTrue(content.contains("public Pet withMetadata(Object metadata)"));
        assertTrue(content.contains("public Pet withOptionalMetadata(@Nullable Object optionalMetadata)"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsRawHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_raw",
            "java-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("RAW")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.java"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.java"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("StoreInventoryApiResponse storeInventory(HttpBodyOutput body)"));
        assertTrue(apiContent.contains("RawObjectApiResponse rawObject(HttpBodyOutput body)"));
        assertTrue(responsesContent.contains("sealed interface StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse("));
        assertTrue(responsesContent.contains("HttpBodyInput content) implements StoreInventoryObjectApiResponse"));
        assertTrue(responsesContent.contains("StoreInventory400ApiResponse"));
        assertTrue(responsesContent.contains("ErrorMessage"));
        assertTrue(responsesContent.contains("implements StoreInventory"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse("));
        assertTrue(responsesContent.contains("HttpBodyInput content) implements StoreInventoryObjectApiResponse"));
        assertTrue(responsesContent.contains("sealed interface RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("HttpBodyInput content) implements RawObjectObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("private final HttpClientResponseMapper<HttpBodyInput> delegate"));
        assertTrue(responseMapperContent.contains("private final HttpClientResponseMapper<ErrorMessage> delegate"));
        assertFalse(responseMapperContent.contains("@Json HttpClientResponseMapper<HttpBodyInput>"));
    }

    @Test
    void bareObjectRequestAndResponseUseByteArrayByDefault() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_bytes_default",
            "java-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.java"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.java"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("StoreInventoryApiResponse storeInventory(byte[] body)"));
        assertTrue(apiContent.contains("RawObjectApiResponse rawObject(byte[] body)"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse(byte[] content)"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse(byte[] content)"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse(byte[] content)"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse(byte[] content)"));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse(byte[] content)"));
        assertTrue(responseMapperContent.contains("private final HttpClientResponseMapper<byte[]> delegate"));
    }
}
