package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class HttpServerKotlinOpenapiTest extends BaseKotlinOpenapiTest {

    @Test
    void multipartFileFormParamDoesNotAskForAConverterItNeverUses() throws Exception {
        var files = generate(
            "petstoreV3_form_multipart",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_form.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerRequestMappers.kt"))
            .findFirst()
            .orElseThrow());

        // KotlinPoet may escape the parameter name, so the assertion only looks at the assigned part
        var singleFile = nestedClass(content, "FormMultipartFormDataWithObjectPatchFormParamRequestMapper");
        assertTrue(singleFile.contains("= _part"));
        assertFalse(singleFile.contains("HttpServerParameterReader"));

        var fileArray = nestedClass(content, "FormMultipartFormDataWithArrayPatchFormParamRequestMapper");
        assertTrue(fileArray.contains(".add(_part)"));
        assertFalse(fileArray.contains("HttpServerParameterReader"));

        // a url-encoded form still converts every non-string parameter
        var urlEncoded = nestedClass(content, "FormUrlencodedObjectPatchFormParamRequestMapper");
        assertTrue(urlEncoded.contains("providedConverter: HttpServerParameterReader<Boolean>"));
    }

    private static String nestedClass(String content, String name) {
        var start = content.indexOf("class " + name);
        assertTrue(start > 0, () -> name + " was not generated");
        var end = content.indexOf("class ", start + 1);
        return end < 0 ? content.substring(start) : content.substring(start, end);
    }

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
    void serverAuthFallbackUsesUnauthorized() throws Exception {
        var files = generate(
            "petstoreV3_security_api_key",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_security_api_key.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("throw HttpServerResponseException.of(401, \"Unauthorized\")"));
        assertFalse(securityContent.contains("Forbidden"));
    }

    @Test
    void serverResponseMapperWithoutDelegatesDoesNotGenerateEmptyConstructor() throws Exception {
        var files = generate(
            "petstoreV3_discriminator",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(responseMapperContent.contains("public open class PetsPatchApiResponseMapper :"));
        assertFalse(responseMapperContent.contains("PetsPatchApiResponseMapper()"));
        assertTrue(responseMapperContent.contains("val headers = HttpHeaders.empty()"));
        assertFalse(responseMapperContent.contains("val headers = HttpHeaders.of()"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_body",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("BODY")
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.kt"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiDelegate.kt"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());
        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("public fun storeInventory(_headers: HttpHeaders, body: HttpBodyInput): DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(delegateContent.contains("public fun storeInventory(_headers: HttpHeaders, body: HttpBodyInput): DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(controllerContent.contains("public fun rawObject(_headers: HttpHeaders, body: HttpBodyInput): DefaultApiResponses.RawObjectApiResponse"));
        assertTrue(delegateContent.contains("public fun rawObject(_headers: HttpHeaders, body: HttpBodyInput): DefaultApiResponses.RawObjectApiResponse"));
        assertEquals(3, countJavadocReturnTags(controllerContent));
        assertEquals(3, countJavadocReturnTags(delegateContent));
        assertTrue(containsMultilineStoreInventoryReturn(controllerContent));
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
    void bareObjectRequestAndResponseAreGeneratedAsObjectTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_object",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("OBJECT")
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.kt"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiDelegate.kt"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("body: Any"));
        assertTrue(delegateContent.contains("body: Any"));
        assertTrue(controllerContent.contains("DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(delegateContent.contains("DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(controllerContent.contains("DefaultApiResponses.RawObjectApiResponse"));
        assertTrue(delegateContent.contains("DefaultApiResponses.RawObjectApiResponse"));
        assertFalse(controllerContent.contains("_headers: HttpHeaders"));
        assertFalse(delegateContent.contains("_headers: HttpHeaders"));
        assertTrue(responsesContent.contains("public val content: Any"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<Any>>"));
        assertTrue(responseMapperContent.contains("@Json"));
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

        assertTrue(controllerContent.contains("public fun storeInventory(_headers: HttpHeaders, body: ByteArray): DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(controllerContent.contains("public fun rawObject(_headers: HttpHeaders, body: ByteArray): DefaultApiResponses.RawObjectApiResponse"));
        assertTrue(responsesContent.contains("public val content: ByteArray"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<ByteArray>>"));
    }
}
