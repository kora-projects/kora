package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void extensionsUseServerConfigPathInAnnotations() throws Exception {
        var files = generate(
            "petstoreV3_server_extensions",
            "java-server",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options()
                .setExtensions("""
                    {
                      "*": {
                        "additionalMethodAnnotations": [
                          "@java.lang.SuppressWarnings(\\"%{configPath}\\")"
                        ]
                      }
                    }
                    """)
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiController.java"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiDelegate.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("@SuppressWarnings(\"httpServer.controller.petsApiController\")"));
        assertTrue(delegateContent.contains("@SuppressWarnings(\"httpServer.controller.petsApiController\")"));
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
    void securityRequirementModeStandardRequiresAllSchemasInOneRequirement() throws Exception {
        var files = generate(
            "petstoreV3_security_multi_standard",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_multi.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("AuthData"));
        assertTrue(securityContent.contains("if (headerAuth1 != null && queryAuth != null)"));
    }

    @Test
    void securityRequirementModeAlwaysOrSplitsSchemasInOneRequirement() throws Exception {
        var files = generate(
            "petstoreV3_security_multi_always_or",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_multi.yaml").toExternalForm(),
            new SwaggerParams.Options().setSecurityRequirementMode("ALWAYS_OR")
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertFalse(securityContent.contains("AuthData"));
        assertFalse(securityContent.contains("if (headerAuth1 != null && queryAuth != null)"));
        assertTrue(securityContent.contains("extract(request, headerAuth1)"));
        assertTrue(securityContent.contains("extract(request, queryAuth)"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsRawHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_raw",
            "java-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("RAW")
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.java"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiDelegate.java"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("StoreInventoryApiResponse storeInventory("));
        assertTrue(delegateContent.contains("StoreInventoryApiResponse storeInventory("));
        assertTrue(controllerContent.contains("RawObjectApiResponse rawObject("));
        assertTrue(delegateContent.contains("RawObjectApiResponse rawObject("));
        assertTrue(controllerContent.contains("HttpHeaders _headers"));
        assertTrue(delegateContent.contains("HttpHeaders _headers"));
        assertTrue(controllerContent.contains("HttpBodyInput body"));
        assertTrue(delegateContent.contains("HttpBodyInput body"));
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
        assertTrue(responseMapperContent.contains("class StoreInventoryApiResponseMapper implements HttpServerResponseMapper"));
        assertFalse(responseMapperContent.contains("final class StoreInventoryApiResponseMapper"));
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

        assertTrue(controllerContent.contains("StoreInventoryApiResponse storeInventory("));
        assertTrue(controllerContent.contains("RawObjectApiResponse rawObject("));
        assertTrue(controllerContent.contains("HttpHeaders _headers"));
        assertTrue(controllerContent.contains("byte[] body"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse(byte[] content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse(byte[] content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<byte[]>> response200Delegate"));
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("class StoreInventoryApiResponseMapper implements HttpServerResponseMapper"));
        assertFalse(responseMapperContent.contains("final class StoreInventoryApiResponseMapper"));
    }
}
