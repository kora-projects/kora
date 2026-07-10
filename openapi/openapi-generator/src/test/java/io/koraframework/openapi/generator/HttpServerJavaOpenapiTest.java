package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;

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
}
