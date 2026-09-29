package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAPI lets an operation declare responses for status-code ranges ({@code 1XX}, {@code 2XX},
 * {@code 3XX}, {@code 4XX}, {@code 5XX}) in addition to exact codes and {@code default}
 * (see the OpenAPI 3 "Responses Object" spec).
 *
 * <p>Ranges are not exact codes, so they cannot be emitted where an {@code int} status is required
 * ({@code @ResponseCodeMapper.code()}, {@code HttpResponseEntity.of(code, ...)}, the record's
 * {@code statusCode()}). The generator therefore lowers them as follows:
 *
 * <ul>
 *   <li><b>Client</b>: exact codes are registered directly with {@code @ResponseCodeMapper(code = N)};
 *   ranges and the OpenAPI {@code default} response funnel through a single aggregate mapper
 *   registered as {@code @ResponseCodeMapper(code = DEFAULT)} that dispatches on the real status code
 *   to the per-range mappers, falling back to the {@code default} mapper or throwing when none exists.</li>
 *   <li><b>Server</b>: range response records carry a runtime {@code int statusCode} (like {@code default}),
 *   and the response entity is built from {@code statusCode()} rather than the range token.</li>
 * </ul>
 *
 * <p>Each test both inspects the generated sources ({@link #generate}) and compiles the whole output
 * ({@link #process}) so a regression that reintroduces an invalid {@code int} literal fails the build.
 */
public class ResponseRangeCodeTest extends BaseJavaOpenapiTest {

    private static final String SPEC = "/example/petstoreV3_response_ranges.yaml";
    private static final String SPEC_NO_DEFAULT = "/example/petstoreV3_response_ranges_no_default.yaml";
    private static final String SPEC_SUCCESSFUL = "/example/petstoreV3_client_successful_response_ranges.yaml";

    @Test
    void clientLowersRangeCodesToAnAggregateDefaultMapper() throws Exception {
        var files = generate("petstoreV3_response_ranges_client", "java-client",
            getClass().getResource(SPEC).toExternalForm(), new SwaggerParams.Options());

        var responses = read(files, "DefaultApiResponses.java");
        var api = read(files, "DefaultApi.java");
        var mappers = read(files, "DefaultApiClientResponseMappers.java");

        // Range records no longer emit an invalid `return 4XX;` — they carry a runtime status code.
        assertFalse(responses.contains("return 4XX"), responses);
        assertFalse(responses.contains("return 5XX"), responses);
        assertTrue(responses.contains("record ListPets4XXApiResponse(int statusCode,"), responses);
        assertTrue(responses.contains("record ListPets5XXApiResponse(int statusCode,"), responses);

        // Exact code is registered directly; ranges/default are NOT registered as their own code.
        assertTrue(api.contains("code = 200"), api);
        assertFalse(api.contains("code = 4XX"), api);
        assertFalse(api.contains("code = 5XX"), api);
        // Exactly one DEFAULT registration, pointing at the aggregate mapper.
        assertTrue(api.contains("code = ResponseCodeMapper.DEFAULT"), api);
        assertTrue(api.contains("ListPetsDefaultCodeApiResponseMapper.class"), api);
        assertEquals(1, countOccurrences(api, "code = ResponseCodeMapper.DEFAULT"), api);
        assertFalse(api.contains("code = -1"), api);

        // The aggregate mapper dispatches on the real status code and falls back to the `default` mapper.
        assertTrue(mappers.contains("class ListPetsDefaultCodeApiResponseMapper"), mappers);
        assertTrue(mappers.contains("if (code >= 400 && code < 500)"), mappers);
        assertTrue(mappers.contains("if (code >= 500 && code < 600)"), mappers);
        assertTrue(mappers.contains("return this.mapperDefault.apply(response)"), mappers);
        assertFalse(mappers.contains("fromResponse"), mappers);

        // And the whole thing compiles.
        process("petstoreV3_response_ranges_client_compile", "java-client",
            getClass().getResource(SPEC).toExternalForm(), new SwaggerParams.Options());
    }

    @Test
    void aggregateMapperThrowsWhenNoDefaultResponseIsDeclared() throws Exception {
        var files = generate("petstoreV3_response_ranges_no_default_client", "java-client",
            getClass().getResource(SPEC_NO_DEFAULT).toExternalForm(), new SwaggerParams.Options());

        var api = read(files, "DefaultApi.java");
        var mappers = read(files, "DefaultApiClientResponseMappers.java");

        // Both exact codes are registered directly; the aggregate covers the two ranges.
        assertTrue(api.contains("code = 200"), api);
        assertTrue(api.contains("code = 404"), api);
        assertTrue(api.contains("ListPetsDefaultCodeApiResponseMapper.class"), api);

        assertTrue(mappers.contains("if (code >= 400 && code < 500)"), mappers);
        assertTrue(mappers.contains("if (code >= 500 && code < 600)"), mappers);
        // No `default` response, so an unmatched code throws like the runtime does.
        assertTrue(mappers.contains("throw HttpClientResponseException.fromResponse(response)"), mappers);
        assertFalse(mappers.contains("mapperDefault"), mappers);

        process("petstoreV3_response_ranges_no_default_compile", "java-client",
            getClass().getResource(SPEC_NO_DEFAULT).toExternalForm(), new SwaggerParams.Options());
    }

    @Test
    void successfulClientResponseModeDispatchesRangeCodes() throws Exception {
        var spec = getClass().getResource(SPEC_SUCCESSFUL).toExternalForm();
        var options = new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL");
        var files = generate("petstoreV3_client_successful_response_ranges", "java-client", spec, options);

        var api = read(files, "PetsApi.java");
        var mappers = read(files, "PetsApiClientResponseMappers.java");

        assertTrue(api.contains("@Mapping(PetsApiClientResponseMappers.GetPetSuccessfulResponseMapper.class)"), api);
        assertTrue(api.contains("GetPet200ApiResponse getPet("), api);
        assertTrue(api.contains("ListPetsPetApiResponse listPets("), api);
        assertTrue(api.contains("DeletePet2XXApiResponse deletePet("), api);
        assertTrue(api.contains("class PetsApiModelErrorHttpClientResponseException extends HttpClientResponseException"), api);
        assertTrue(api.contains("class PetsApiNoContentHttpClientResponseException extends HttpClientResponseException"), api);
        assertFalse(api.contains("code = 4XX"), api);

        // An exact code wins over the range that contains it.
        assertTrue(mappers.contains("case 404 ->"), mappers);
        assertTrue(mappers.contains("if (_code >= 400 && _code < 500)"), mappers);
        assertTrue(mappers.contains("this.getPet4XXResponseMapper.apply(_bufferedResponse.response())"), mappers);
        assertTrue(mappers.contains("((PetsApiResponses.GetPetApiResponse.GetPet4XXApiResponse) _response).content()"), mappers);
        assertTrue(mappers.contains("if (_code >= 500 && _code < 600)"), mappers);
        assertTrue(mappers.contains("throw new PetsApi.PetsApiNoContentHttpClientResponseException(response.code(), response.headers(), _bufferedResponse.body())"), mappers);
        // A 2XX range is a success: it is returned, not thrown.
        assertTrue(mappers.contains("if (_code >= 200 && _code < 300)"), mappers);
        assertTrue(mappers.contains("return (PetsApiResponses.ListPetsApiResponse.ListPetsPetApiResponse) this.listPets2XXResponseMapper.apply(response)"), mappers);
        assertTrue(mappers.contains("return (PetsApiResponses.DeletePetApiResponse.DeletePet2XXApiResponse) this.deletePet2XXResponseMapper.apply(response)"), mappers);

        process("petstoreV3_client_successful_response_ranges_compile", "java-client", spec, options);
    }

    @Test
    void serverBuildsRangeResponsesFromRuntimeStatusCode() throws Exception {
        var files = generate("petstoreV3_response_ranges_server", "java-server",
            getClass().getResource(SPEC).toExternalForm(), new SwaggerParams.Options());

        var mappers = read(files, "DefaultApiServerResponseMappers.java");

        // The response entity is built from the record's runtime status code, not the range token.
        assertFalse(mappers.contains("HttpResponseEntity.of(4XX,"), mappers);
        assertFalse(mappers.contains("HttpResponseEntity.of(5XX,"), mappers);
        assertTrue(mappers.contains("HttpResponseEntity.of(rs.statusCode(),"), mappers);

        process("petstoreV3_response_ranges_server_compile", "java-server",
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
