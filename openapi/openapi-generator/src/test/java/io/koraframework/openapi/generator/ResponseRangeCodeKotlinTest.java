package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kotlin counterpart of {@link ResponseRangeCodeTest}: OpenAPI status-code ranges ({@code 4XX},
 * {@code 5XX}, ...) are lowered the same way for the Kotlin generators — exact codes register
 * directly, ranges and {@code default} funnel through one aggregate {@code DEFAULT} mapper that
 * dispatches on the real status code, and range response data classes carry a runtime
 * {@code statusCode}. Each test inspects the generated Kotlin and compiles the whole output.
 */
public class ResponseRangeCodeKotlinTest extends BaseKotlinOpenapiTest {

    private static final String SPEC = "/example/petstoreV3_response_ranges.yaml";
    private static final String SPEC_NO_DEFAULT = "/example/petstoreV3_response_ranges_no_default.yaml";

    @Test
    void clientLowersRangeCodesToAnAggregateDefaultMapper() throws Exception {
        var files = generate("petstoreV3_response_ranges_kt_client", "kotlin-client",
            getClass().getResource(SPEC).toExternalForm(), new SwaggerParams.Options());

        var responses = read(files, "DefaultApiResponses.kt");
        var api = read(files, "DefaultApi.kt");
        var mappers = read(files, "DefaultApiClientResponseMappers.kt");

        // Range data classes carry a runtime status code instead of emitting an invalid `return 4XX`.
        assertFalse(responses.contains("return 4XX"), responses);
        assertFalse(responses.contains("return 5XX"), responses);
        assertTrue(responses.contains("class ListPets4XXApiResponse("), responses);
        assertTrue(responses.contains("class ListPets5XXApiResponse("), responses);
        assertTrue(responses.contains("override val statusCode: Int"), responses);

        // Exact code registered directly; a single DEFAULT registration points at the aggregate mapper.
        assertTrue(api.contains("code = 200"), api);
        assertFalse(api.contains("code = 4XX"), api);
        assertFalse(api.contains("code = 5XX"), api);
        assertTrue(api.contains("code = ResponseCodeMapper.DEFAULT"), api);
        assertTrue(api.contains("ListPetsDefaultCodeApiResponseMapper::class"), api);
        assertEquals(1, countOccurrences(api, "code = ResponseCodeMapper.DEFAULT"), api);
        assertFalse(api.contains("code = -1"), api);

        // The aggregate mapper dispatches on the real status code and falls back to the `default` mapper.
        assertTrue(mappers.contains("class ListPetsDefaultCodeApiResponseMapper"), mappers);
        assertTrue(mappers.contains("if (code >= 400 && code < 500)"), mappers);
        assertTrue(mappers.contains("if (code >= 500 && code < 600)"), mappers);
        assertTrue(mappers.contains("return this.mapperDefault.apply(response)"), mappers);
        assertFalse(mappers.contains("fromResponse"), mappers);

        process("petstoreV3_response_ranges_kt_client_compile", "kotlin-client",
            getClass().getResource(SPEC).toExternalForm(), new SwaggerParams.Options());
    }

    @Test
    void aggregateMapperThrowsWhenNoDefaultResponseIsDeclared() throws Exception {
        var files = generate("petstoreV3_response_ranges_kt_no_default_client", "kotlin-client",
            getClass().getResource(SPEC_NO_DEFAULT).toExternalForm(), new SwaggerParams.Options());

        var api = read(files, "DefaultApi.kt");
        var mappers = read(files, "DefaultApiClientResponseMappers.kt");

        assertTrue(api.contains("code = 200"), api);
        assertTrue(api.contains("code = 404"), api);
        assertTrue(api.contains("ListPetsDefaultCodeApiResponseMapper::class"), api);

        assertTrue(mappers.contains("if (code >= 400 && code < 500)"), mappers);
        assertTrue(mappers.contains("if (code >= 500 && code < 600)"), mappers);
        assertTrue(mappers.contains("throw HttpClientResponseException.fromResponse(response)"), mappers);
        assertFalse(mappers.contains("mapperDefault"), mappers);

        process("petstoreV3_response_ranges_kt_no_default_compile", "kotlin-client",
            getClass().getResource(SPEC_NO_DEFAULT).toExternalForm(), new SwaggerParams.Options());
    }

    @Test
    void serverBuildsRangeResponsesFromRuntimeStatusCode() throws Exception {
        var files = generate("petstoreV3_response_ranges_kt_server", "kotlin-server",
            getClass().getResource(SPEC).toExternalForm(), new SwaggerParams.Options());

        var mappers = read(files, "DefaultApiServerResponseMappers.kt");

        assertFalse(mappers.contains("HttpResponseEntity.of(4XX,"), mappers);
        assertFalse(mappers.contains("HttpResponseEntity.of(5XX,"), mappers);
        assertTrue(mappers.contains("HttpResponseEntity.of(rs.statusCode,"), mappers);

        process("petstoreV3_response_ranges_kt_server_compile", "kotlin-server",
            getClass().getResource(SPEC).toExternalForm(), new SwaggerParams.Options());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private static String read(java.util.List<File> files, String fileName) throws Exception {
        return Files.readString(files.stream()
            .map(File::toPath)
            .filter(path -> path.getFileName().toString().equals(fileName))
            .findFirst()
            .orElseThrow(() -> new AssertionError(fileName + " was not generated")));
    }
}
