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

    /**
     * The generated client interface is meant to be injected into application code, which almost
     * always lives in another package, so it has to be public. Without an explicit modifier
     * JavaPoet emits a package-private interface and any usage fails with
     * "PetApi is not public in ...; cannot be accessed from outside package".
     */
    @Test
    void clientApiInterfaceIsPublic() throws Exception {
        var files = generate(
            "petstoreV3_public_api",
            "java-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options()
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

        assertTrue(content.contains("public interface "), content);
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
    void securityConfigUsesDedicatedNamesComponentAndPrefix() throws Exception {
        var files = generate(
            "petstoreV3_security_config_contract",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options()
                .setClientConfigPrefix("clients")
                .setSecurityConfigPrefix("security")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("default SecurityConfig securityConfig("));
        assertTrue(content.indexOf("@DefaultComponent") < content.indexOf("default SecurityConfig securityConfig("));
        assertTrue(content.contains("record SecurityConfig("));
        assertTrue(content.contains("@Generated(\"io.koraframework.openapi.generator.javagen.ClientSecuritySchemaGenerator\")\n  record SecurityConfig("));
        assertTrue(content.contains("SecurityBasicAuthConfig(@Nullable String username,"));
        assertTrue(content.contains("@Nullable String password)"));
        assertTrue(content.contains("record SecurityConfig(@Nullable String apiKeyAuth,"));
        assertTrue(content.contains("@Nullable SecurityBasicAuthConfig basicAuth"));
        assertTrue(content.contains("@Nullable String cookieAuth)"));
        assertTrue(content.contains("mapper.map(config.get(\"security.apiKeyAuth\"))"));
        assertFalse(content.contains("mapper.mapOrThrow"));
        assertTrue(content.contains("config.get(\"security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"security.basicAuth.username\")"));
        assertTrue(content.contains("config.get(\"security.cookieAuth\")"));
        assertFalse(content.contains("config.get(\"clients."));
        assertFalse(content.contains("@ConfigSource"));
        assertFalse(content.contains("default Config config("));
    }

    @Test
    void cookieSecurityIsAddedToRequest() throws Exception {
        var files = generate(
            "petstoreV3_security_cookie_client_interceptor",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_cookie.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("b.header(\"Cookie\", \"X-COOKIE-KEY=\" + CookieAuth)"));
        assertFalse(content.contains("Cookies are not supported yet"));
    }

    @Test
    void securityConfigFallsBackToClientConfigPrefix() throws Exception {
        var files = generate(
            "petstoreV3_security_client_prefix_fallback",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfigPrefix("clients")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("config.get(\"clients.security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"clients.security.basicAuth.username\")"));
    }

    @Test
    void securityConfigFallsBackToClientConfig() throws Exception {
        var files = generate(
            "petstoreV3_security_client_config_fallback",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig("clients.petstore")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("config.get(\"clients.petstore.security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"clients.petstore.security.basicAuth.username\")"));
    }

    @Test
    void clientConfigIsRequiredWhenPrefixIsMissing() {
        var e = assertThrows(IllegalArgumentException.class, () -> generate(
            "petstoreV3_missing_config",
            "java-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig(null)
        ));

        assertTrue(e.getMessage().contains("Missing OpenAPI generator `clientConfig`"));
        assertTrue(e.getMessage().contains("Generation mode `java-client`"));
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
        assertFalse(content.contains("default String message()"));
        assertFalse(content.contains("default @Nullable String details()"));
        assertTrue(content.contains("int statusCode()"));
        assertTrue(content.contains("record GetErrors400ApiResponse(ModelError content) implements GetErrorsModelErrorApiResponse"));
        assertTrue(content.contains("return 400"));
        assertFalse(content.contains("return this.content().details()"));
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
            .filter(path -> path.getFileName().toString().equals("PetDogBreedEnumMapperModule.java"))
            .findFirst()
            .orElseThrow());

        assertFalse(petDogContent.contains("MapperModule"));
        assertTrue(petDogContent.contains("DINGO_DON(Constants.DINGO_DON)"));
        assertTrue(petDogContent.contains("NUMBER_5(Constants.NUMBER_5)"));
        assertTrue(petDogContent.contains("public static final class Constants"));
        assertTrue(petDogContent.contains("public static final String DINGO_DON = \"Dingo-Don\""));
        assertTrue(petDogContent.contains("public static final Integer NUMBER_5 = 5"));
        assertFalse(petDogContent.contains("public static final class JsonWriter"));
        assertFalse(petDogContent.contains("public static final class JsonReader"));
        assertFalse(petDogContent.contains("public static final class StringParameterConverter"));
        assertTrue(petDogContent.contains("* Dingo breed"));
        assertTrue(petDogContent.contains("* enum with int value"));

        assertTrue(moduleContent.contains("public interface PetDogBreedEnumMapperModule"));
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
            .filter(path -> path.getFileName().toString().equals("PetNonReqDoubleEnumMapperModule.java"))
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
        assertTrue(content.contains("return (this.id == id) ? this : new Pet(id, this.nullableType"));
        assertTrue(content.contains("public Pet withNonReqDouble(@Nullable NonReqDoubleEnum nonReqDouble)"));
        assertTrue(content.contains("return (Objects.equals(this.nonReqDouble, nonReqDouble)) ? this : new Pet(this.id, this.nullableType"));
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
    void anonymousSecurityDoesNotRequireClientInterceptor() throws Exception {
        var files = generate(
            "petstoreV3_security_anonymous",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_anonymous.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PublicApi.java"))
            .findFirst()
            .orElseThrow());
        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.indexOf("tag = ApiSecurity.Sec1_Anonymous.class") < apiContent.indexOf("optionalAccess("));
        assertTrue(apiContent.indexOf("tag = ApiSecurity.Sec1.class") < apiContent.indexOf("requiredAccess("));
        assertTrue(securityContent.contains("final class Sec1_Anonymous"));
        assertTrue(securityContent.contains("final class Sec1"));
        assertTrue(apiContent.lastIndexOf("OperationSecuritySchemaTag") < apiContent.indexOf("publicAccess("));
        assertFalse(securityContent.contains("if ()"));
        assertTrue(securityContent.contains("return chain.process(request);"));
    }

    @Test
    void securityDeclarationOrderCanBePreservedForClientInterceptors() throws Exception {
        var defaultFiles = generate(
            "petstoreV3_security_order_default",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_order.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var orderedFiles = generate(
            "petstoreV3_security_order_ordered",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_order.yaml").toExternalForm(),
            new SwaggerParams.Options().setUseSecurityDeclarationOrder(true)
        );

        var defaultApiContent = Files.readString(defaultFiles.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApi.java"))
            .findFirst()
            .orElseThrow());
        var orderedApiContent = Files.readString(orderedFiles.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApi.java"))
            .findFirst()
            .orElseThrow());
        var orderedSecurityContent = Files.readString(orderedFiles.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(defaultApiContent.contains("ApiSecurity.Sec1AndSec2.class"));
        assertFalse(defaultApiContent.contains("ApiSecurity.Sec2AndSec1.class"));
        assertTrue(orderedApiContent.contains("ApiSecurity.Sec1AndSec2.class"));
        assertTrue(orderedApiContent.contains("ApiSecurity.Sec2AndSec1.class"));
        assertTrue(orderedSecurityContent.contains("Sec1AndSec2HttpClientInterceptor"));
        assertTrue(orderedSecurityContent.contains("Sec2AndSec1HttpClientInterceptor"));
    }

    @Test
    void securityTagsUseSchemeNames() throws Exception {
        var files = generate(
            "petstoreV3_security_all_named_tags",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("final class BearerAuth"));
        assertTrue(securityContent.contains("final class ApiKeyAuth"));
        assertTrue(securityContent.contains("final class BasicAuth"));
        assertTrue(securityContent.contains("final class CookieAuth"));
        assertTrue(securityContent.contains("final class OAuth"));
        assertFalse(securityContent.contains("final class bearerAuth"));
        assertTrue(securityContent.contains("final class BearerAuth_ApiKeyAuth_BasicAuth_CookieAuth_OAuth"));
        assertFalse(securityContent.contains("ReadPets"));
        assertFalse(securityContent.contains("WritePets"));
        assertFalse(securityContent.contains("OperationSecuritySchemaTag"));
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
    void bareObjectRequestAndResponseAreGeneratedAsHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_body",
            "java-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("BODY")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.java"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.java"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("StoreInventoryApiResponse storeInventory("));
        assertTrue(apiContent.contains("RawObjectApiResponse rawObject(@Header HttpHeaders additionalHeaders,"));
        assertTrue(apiContent.contains("@Header HttpHeaders additionalHeaders, HttpBodyOutput body)"));
        assertEquals(3, countJavadocReturnTags(apiContent));
        assertTrue(containsMultilineStoreInventoryReturn(apiContent));
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
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("class StoreInventory200ApiResponseMapper"));
        assertFalse(responseMapperContent.contains("public static final class StoreInventory200ApiResponseMapper"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsObjectTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_object",
            "java-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("OBJECT")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.java"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.java"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("StoreInventoryApiResponse storeInventory(@Json Object body)"));
        assertTrue(apiContent.contains("RawObjectApiResponse rawObject(@Json Object body)"));
        assertFalse(apiContent.contains("HttpHeaders additionalHeaders"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse(Object content)"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse(Object content)"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse(Object content)"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse(Object content)"));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse(Object content)"));
        assertTrue(responseMapperContent.contains("@Json HttpClientResponseMapper<Object> delegate"));
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

        assertTrue(apiContent.contains("StoreInventoryApiResponse storeInventory("));
        assertTrue(apiContent.contains("RawObjectApiResponse rawObject(@Header HttpHeaders additionalHeaders,"));
        assertTrue(apiContent.contains("@Header HttpHeaders additionalHeaders, byte[] body)"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse(byte[] content)"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse(byte[] content)"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse(byte[] content)"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse(byte[] content)"));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse(byte[] content)"));
        assertTrue(responseMapperContent.contains("private final HttpClientResponseMapper<byte[]> delegate"));
    }
}
