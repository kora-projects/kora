package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class HttpServerJavaOpenapiTest extends BaseJavaOpenapiTest {
    @ParameterizedTest
    @MethodSource("generateParams")
    void test(SwaggerParams params) throws Exception {
        process(
            params.name(),
            "java-server",
            params.spec(),
            params.options()
        );
    }

    @Test
    void javadocsIncludeOpenapiOperationMetadata() throws Exception {
        var files = generate(
            "petstoreV2_javadocs",
            "java-server",
            getClass().getResource("/example/petstoreV2.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetApiController.java"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetApiDelegate.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("* POST /pet : Add a new pet to the store"));
        assertTrue(apiContent.contains("* @param body Pet object that needs to be added to the store (required)"));
        assertTrue(apiContent.contains("* @return Invalid input (status code 405)"));
        assertTrue(delegateContent.contains("* POST /pet : Add a new pet to the store"));
        assertTrue(delegateContent.contains("* @param body Pet object that needs to be added to the store (required)"));
        assertTrue(delegateContent.contains("* @return Invalid input (status code 405)"));
    }

    @Test
    void anonymousSecurityDoesNotRequireServerInterceptor() throws Exception {
        var files = generate(
            "petstoreV3_security_anonymous",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_anonymous.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PublicApiController.java"))
            .findFirst()
            .orElseThrow());
        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.indexOf("tag = ApiSecurity.OperationSecuritySchemaTag0.class") < controllerContent.indexOf("optionalAccess("));
        assertTrue(controllerContent.lastIndexOf("OperationSecuritySchemaTag") < controllerContent.indexOf("publicAccess("));
        assertFalse(securityContent.contains("SecurityRequirementTag1"));
        assertTrue(securityContent.contains("return chain.process(request);"));
        assertFalse(securityContent.contains("Unauthorized"));
    }

    @Test
    void serverAuthFallbackUsesUnauthorized() throws Exception {
        var files = generate(
            "petstoreV3_security_api_key",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_api_key.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("throw HttpServerResponseException.of(401, \"Unauthorized\")"));
        assertFalse(securityContent.contains("Forbidden"));
    }

    @Test
    void serverResponseMapperWithoutDelegatesDoesNotGenerateEmptyConstructor() throws Exception {
        var files = generate(
            "petstoreV3_discriminator",
            "java-server",
            getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(responseMapperContent.contains("class PetsPatchApiResponseMapper"));
        assertFalse(responseMapperContent.contains("public PetsPatchApiResponseMapper()"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_body",
            "java-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("BODY")
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.java"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiDelegate.java"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("StoreInventoryApiResponse storeInventory(HttpHeaders _headers,"));
        assertTrue(controllerContent.contains("HttpBodyInput body)"));
        assertTrue(delegateContent.contains("StoreInventoryApiResponse storeInventory(HttpHeaders _headers,"));
        assertTrue(delegateContent.contains("HttpBodyInput body)"));
        assertTrue(controllerContent.contains("RawObjectApiResponse rawObject(HttpHeaders _headers,"));
        assertTrue(delegateContent.contains("RawObjectApiResponse rawObject(HttpHeaders _headers,"));
        assertEquals(3, countJavadocReturnTags(controllerContent));
        assertEquals(3, countJavadocReturnTags(delegateContent));
        assertTrue(containsMultilineStoreInventoryReturn(controllerContent));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse("));
        assertTrue(responsesContent.contains("HttpBodyOutput content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory400ApiResponse(ErrorMessage content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse("));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("HttpBodyOutput content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<HttpBodyOutput>> response200Delegate"));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<ErrorMessage>> response400Delegate"));
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("class StoreInventoryApiResponseMapper"));
        assertFalse(responseMapperContent.contains("public static final class StoreInventoryApiResponseMapper"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsObjectTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_object",
            "java-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("OBJECT")
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.java"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiDelegate.java"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("StoreInventoryApiResponse storeInventory(@Json Object body)"));
        assertTrue(delegateContent.contains("StoreInventoryApiResponse storeInventory(@Json Object body)"));
        assertTrue(controllerContent.contains("RawObjectApiResponse rawObject(@Json Object body)"));
        assertTrue(delegateContent.contains("RawObjectApiResponse rawObject(@Json Object body)"));
        assertFalse(controllerContent.contains("HttpHeaders _headers"));
        assertFalse(delegateContent.contains("HttpHeaders _headers"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse(Object content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse(Object content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse(Object content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse(Object content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse(Object content) implements RawObjectApiResponse"));
        assertTrue(responseMapperContent.contains("@Json HttpServerResponseMapper<HttpResponseEntity<Object>> response200Delegate"));
    }

    @Test
    void bareObjectRequestAndResponseUseByteArrayByDefault() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_bytes_default",
            "java-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.java"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("StoreInventoryApiResponse storeInventory(HttpHeaders _headers,"));
        assertTrue(controllerContent.contains("byte[] body)"));
        assertTrue(controllerContent.contains("RawObjectApiResponse rawObject(HttpHeaders _headers, byte[] body)"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse(byte[] content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse(byte[] content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<byte[]>> response200Delegate"));
    }
}
