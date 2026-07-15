package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpServerKotlinOpenapiTest extends BaseKotlinOpenapiTest {
    @ParameterizedTest
    @MethodSource("generateParams")
    void test(SwaggerParams params) throws Exception {
        process(
            params.name(),
            "kotlin-server",
            params.spec(),
            params.options()
        );
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsRawHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_raw",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("RAW")
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.kt"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiDelegate.kt"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_raw"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("public fun storeInventory("));
        assertTrue(delegateContent.contains("public fun storeInventory("));
        assertTrue(controllerContent.contains("public fun rawObject("));
        assertTrue(delegateContent.contains("public fun rawObject("));
        assertTrue(controllerContent.contains("_headers: HttpHeaders"));
        assertTrue(delegateContent.contains("_headers: HttpHeaders"));
        assertTrue(controllerContent.contains("body: HttpBodyInput"));
        assertTrue(delegateContent.contains("body: HttpBodyInput"));
        assertTrue(responsesContent.contains("public val content: HttpBodyOutput"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responsesContent.contains("public val content: ErrorMessage"));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<HttpBodyOutput>>"));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<ErrorMessage>>"));
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("public open class StoreInventoryApiResponseMapper"));
    }

    @Test
    void anonymousSecurityDoesNotRequireServerInterceptor() throws Exception {
        var files = generate(
            "petstoreV3_security_anonymous",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_security_anonymous.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PublicApiController.kt"))
            .findFirst()
            .orElseThrow());
        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.indexOf("tag = ApiSecurity.OperationSecuritySchemaTag0::class") < controllerContent.indexOf("optionalAccess("));
        assertTrue(controllerContent.lastIndexOf("OperationSecuritySchemaTag") < controllerContent.indexOf("publicAccess("));
        assertFalse(securityContent.contains("SecurityRequirementTag1"));
        assertTrue(securityContent.contains("return chain.process(request)"));
        assertFalse(securityContent.contains("Unauthorized"));
    }

    @Test
    void securityRequirementModeStandardRequiresAllSchemasInOneRequirement() throws Exception {
        var files = generate(
            "petstoreV3_security_multi_standard",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_security_multi.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("AuthData"));
        assertTrue(securityContent.contains("if (headerAuth1 != null && queryAuth != null)"));
    }

    @Test
    void securityRequirementModeAlwaysOrSplitsSchemasInOneRequirement() throws Exception {
        var files = generate(
            "petstoreV3_security_multi_always_or",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_security_multi.yaml").toExternalForm(),
            new SwaggerParams.Options().setSecurityRequirementMode("ALWAYS_OR")
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertFalse(securityContent.contains("AuthData"));
        assertFalse(securityContent.contains("if (headerAuth1 != null && queryAuth != null)"));
        assertTrue(securityContent.contains("extract(request, headerAuth1)"));
        assertTrue(securityContent.contains("extract(request, queryAuth)"));
    }

    @Test
    void bareObjectRequestAndResponseUseByteArrayByDefault() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_bytes_default",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.kt"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("public fun storeInventory("));
        assertTrue(controllerContent.contains("public fun rawObject("));
        assertTrue(controllerContent.contains("_headers: HttpHeaders"));
        assertTrue(controllerContent.contains("body: ByteArray"));
        assertTrue(responsesContent.contains("public val content: ByteArray"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<ByteArray>>"));
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("public open class StoreInventoryApiResponseMapper"));
    }
}
