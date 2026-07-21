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
}
