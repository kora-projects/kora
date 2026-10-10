package io.koraframework.openapi.generator;

import io.koraframework.http.client.common.exception.HttpClientResponseException;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.http.client.common.response.SimpleHttpClientResponse;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.client.symbol.processor.HttpClientSymbolProcessorProvider;
import io.koraframework.json.ksp.JsonSymbolProcessorProvider;
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider;
import io.koraframework.ksp.common.KotlinCompilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HttpClientKotlinOpenapiTest extends BaseKotlinOpenapiTest {
    @Test
    void mapResponseWithTypedValuesIsAJsonMap() throws Exception {
        var files = generate(
            "petstoreV3_map_response_kotlin_client",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_map_response.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var mappers = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.kt"))
            .findFirst()
            .orElseThrow());
        var responses = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());

        // a map with typed additionalProperties is a JSON map, not a raw body
        assertTrue(responses.contains("public val content: Map<String, Int>,"), responses);
        assertTrue(mappers.contains("@param:Json\n    public val `delegate`: HttpClientResponseMapper<Map<String, Int>>"), mappers);
    }

    @Test
    void enumsCompileWithoutRedundantConversionWarnings() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_enum.yaml").toExternalForm();
        var kc = process("petstoreV3_enum", "kotlin-client", spec, new SwaggerParams.Options());

        assertTrue(kc.getCompilerMessages().stream().noneMatch(m -> m.toLowerCase().contains("redundant call of conversion method")), () -> String.join("\n", kc.getCompilerMessages()));
    }

    @Test
    void discriminatorModelsCompileWithoutWarnings() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm();
        var kc = process("petstoreV3_discriminator_no_warnings", "kotlin-client", spec, new SwaggerParams.Options());
        assertNoWarningsInGeneratedSources(kc);
    }

    @Test
    void successfulResponseModeCompilesWithoutWarnings() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_client_successful_response.yaml").toExternalForm();
        var kc = process("petstoreV3_client_successful_response_no_warnings", "kotlin-client", spec, new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL"));
        assertNoWarningsInGeneratedSources(kc);
    }

    @Test
    void authorizationHeaderCarriesItsScheme() throws Exception {
        var files = generate(
            "petstoreV3_security_all_scheme",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("b.header(\"Authorization\", \"Bearer \" + bearerAuth)"), content);
        assertTrue(content.contains("b.header(\"Authorization\", \"Basic \" + basicAuth)"), content);
        assertTrue(content.contains("b.header(\"Authorization\", \"Bearer \" + oAuth)"), content);
        assertTrue(content.contains("b.header(\"X-API-KEY\", apiKeyAuth)"), content);
    }

    @Test
    void modelEnumsAndDefaultsAreTyped() throws Exception {
        var files = generate(
            "petstoreV3_model_enums_defaults_types",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_model_enums_defaults.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        java.util.function.Function<String, String> read = name -> {
            try {
                return Files.readString(files.stream().map(java.io.File::toPath).filter(p -> p.getFileName().toString().equals(name)).findFirst().orElseThrow());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };

        var accountStatus = read.apply("AccountStatus.kt");
        assertTrue(accountStatus.contains("enum class AccountStatus "), accountStatus);

        var holder = read.apply("Holder.kt");
        assertTrue(holder.contains("public val spec: Spec,"), holder);
        assertTrue(holder.contains("public val labels: Map<String, String> = mapOf(),"), holder);
        assertTrue(holder.contains("public val type: TypeEnum = TypeEnum.RAW,"), holder);
        assertTrue(holder.contains("public val status: AccountStatus = AccountStatus.CLOSED,"), holder);
        assertTrue(holder.contains("public val signers: List<List<SignersEnum>>? = null"), holder);

        var step = read.apply("Step.kt");
        assertTrue(step.contains("public val conclusion: ConclusionEnum? = null,"), step);
        assertTrue(step.contains("public val conclusions: List<ConclusionsEnum>? = null"), step);
    }

    @ParameterizedTest
    @MethodSource("generateParams")
    void test(SwaggerParams params) throws Exception {
        process(
            params.name(),
            "kotlin-client",
            params.spec(),
            params.options()
        );
    }

    @Test
    void requestMappersAreNotGeneratedWhenEmpty() throws Exception {
        var files = generate(
            "petstoreV3_discriminator_no_request_mappers",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        assertTrue(files.stream().noneMatch(file -> file.getName().endsWith("ClientRequestMappers.kt")));
    }

    @Test
    void multipartFormWritesArraysAsRepeatedParts() throws Exception {
        var files = generate(
            "petstoreV3_form_multipart_client_types",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_form.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientRequestMappers.kt"))
            .findFirst()
            .orElseThrow());

        // an int array is written one part per element through an element-typed writer
        var intArray = nestedClass(content, "FormMultipartFormDataWithIntArrayPatchFormParamRequestMapper");
        assertTrue(intArray.contains("countsConverter: HttpClientParameterWriter<Int>"));
        assertTrue(intArray.contains("for (item in it)"));
        assertTrue(intArray.contains("l.add(FormMultipart.data(\"counts\", countsConverter.convert(item)))"));

        // the whole-list single-part form must be gone
        assertFalse(content.contains("countsConverter.convert(value.counts)"));
    }

    private static String nestedClass(String content, String name) {
        var start = content.indexOf("class " + name);
        assertTrue(start > 0, () -> name + " was not generated");
        var end = content.indexOf("class ", start + 1);
        return end < 0 ? content.substring(start) : content.substring(start, end);
    }

    @Test
    void clientConfigIsUsedAsSingleConfigPath() throws Exception {
        var files = generate(
            "petstoreV3_single_config",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig("httpClient.petstoreV3")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("Api.kt"))
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
            "kotlin-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options()
                .setClientConfig(null)
                .setClientConfigPrefix("httpClient")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("Api.kt"))
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
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options()
                .setClientConfigPrefix("clients")
                .setSecurityConfigPrefix("security")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("fun securityConfig("));
        assertTrue(content.indexOf("@DefaultComponent") < content.indexOf("fun securityConfig("));
        assertTrue(content.contains("data class SecurityConfig("));
        assertTrue(content.contains("@Generated(\"io.koraframework.openapi.generator.kotlingen.ClientSecuritySchemaGenerator\")\n  public data class SecurityConfig("));
        assertTrue(content.contains("data class SecurityBasicAuthConfig("));
        assertTrue(content.contains("public val apiKeyAuth: String?,"));
        assertTrue(content.contains("public val basicAuth: SecurityBasicAuthConfig?,"));
        assertTrue(content.contains("public val cookieAuth: String?,"));
        assertTrue(content.contains("public val username: String?,"));
        assertTrue(content.contains("public val password: String?,"));
        assertTrue(content.contains("mapper.map(config.get(\"security.apiKeyAuth\"))"));
        assertFalse(content.contains("mapper.mapOrThrow"));
        assertTrue(content.contains("config.get(\"security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"security.basicAuth.username\")"));
        assertTrue(content.contains("config.get(\"security.cookieAuth\")"));
        assertFalse(content.contains("config.get(\"clients."));
        assertFalse(content.contains("@ConfigSource"));
    }

    @Test
    void openIdConnectSecuritySendsBearerAuthorizationHeader() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_security_openid.yaml").toExternalForm();
        process("petstoreV3_security_openid", "kotlin-client", spec, new SwaggerParams.Options());

        var files = generate("petstoreV3_security_openid", "kotlin-client", spec, new SwaggerParams.Options());
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("b.header(\"Authorization\", \"Bearer \" + openIdAuth)"), content);
    }

    @Test
    void cookieSecurityIsAddedToRequest() throws Exception {
        var files = generate(
            "petstoreV3_security_cookie_client_interceptor",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_cookie.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("_securityCookieHeader = if (_securityCookieHeader.isNullOrBlank()) \"X-COOKIE-KEY=\" + CookieAuth"));
        assertTrue(content.contains("b.header(\"Cookie\", _securityCookieHeader)"));
        assertFalse(content.contains("Cookie client authentication is not implemented yet"));
        assertFalse(content.contains("TODO("));
    }

    @Test
    void multipleCookieSecuritySchemesAreCombinedWithExistingCookies() throws Exception {
        var files = generate(
            "petstoreV3_security_cookie_and_client_interceptor",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_cookie_and.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("var _securityCookieHeader = request.headers().getFirst(\"Cookie\")"));
        assertTrue(content.contains("\"X-COOKIE-KEY-1=\" + cookieAuth1"));
        assertTrue(content.contains("_securityCookieHeader + \"; \" + \"X-COOKIE-KEY-2=\" + cookieAuth2"));
        assertTrue(content.contains("b.header(\"Cookie\", _securityCookieHeader)"));
    }

    @Test
    void securityConfigFallsBackToClientConfigPrefix() throws Exception {
        var files = generate(
            "petstoreV3_security_client_prefix_fallback",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfigPrefix("clients")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("config.get(\"clients.security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"clients.security.basicAuth.username\")"));
    }

    @Test
    void securityConfigFallsBackToClientConfig() throws Exception {
        var files = generate(
            "petstoreV3_security_client_config_fallback",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig("clients.petstore")
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("config.get(\"clients.petstore.security.apiKeyAuth\")"));
        assertTrue(content.contains("config.get(\"clients.petstore.security.basicAuth.username\")"));
    }

    @Test
    void clientConfigIsRequiredWhenPrefixIsMissing() {
        var e = assertThrows(IllegalArgumentException.class, () -> generate(
            "petstoreV3_missing_config",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientConfig(null)
        ));

        assertTrue(e.getMessage().contains("Missing OpenAPI generator `clientConfig`"));
        assertTrue(e.getMessage().contains("Generation mode `kotlin-client`"));
        assertTrue(e.getMessage().contains("httpClient.petstoreV3"));
    }

    @Test
    void successfulResponseMappersBuildIntoAGraph() throws Exception {
        var name = "petstoreV3_client_successful_response_graph";
        var files = generate(
            name,
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_client_successful_response.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL")
        );
        var kc = new KotlinCompilation();
        var sources = kc.getBaseDir().resolve("sources");
        for (var file : files) {
            var target = sources.resolve(openapiSourcesDir.relativize(file.toPath()));
            Files.createDirectories(target.getParent());
            Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            if (target.toString().endsWith(".kt")) {
                kc.withSrc(target);
            }
        }
        var apiPackage = "io.koraframework.openapi.generator." + name + ".kotlin_client.api";
        var app = sources.resolve("TestApp.kt");
        Files.writeString(app, """
            package %s

            @io.koraframework.common.annotation.KoraApp
            interface TestApp {
                @io.koraframework.common.annotation.Root
                fun root(
                    createPet: PetsApiClientResponseMappers.CreatePetSuccessfulResponseMapper,
                    findPet: PetsApiClientResponseMappers.FindPetSuccessfulResponseMapper,
                    partialPet: PetsApiClientResponseMappers.PartialPetSuccessfulResponseMapper,
                    ambiguousPet: PetsApiClientResponseMappers.AmbiguousPetSuccessfulResponseMapper,
                ) = ""
            }
            """.formatted(apiPackage));
        kc.withSrc(app);

        assertDoesNotThrow(() -> kc
            .withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpClientSymbolProcessorProvider(), new KoraAppProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile());
    }

    @Test
    void formRequestMappersBuildIntoAGraph() throws Exception {
        var name = "petstoreV3_form_parts_graph";
        var files = generate(
            name,
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_form_parts.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var kc = new KotlinCompilation();
        var sources = kc.getBaseDir().resolve("sources");
        for (var file : files) {
            var target = sources.resolve(openapiSourcesDir.relativize(file.toPath()));
            Files.createDirectories(target.getParent());
            Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            if (target.toString().endsWith(".kt")) {
                kc.withSrc(target);
            }
        }
        var mappers = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientRequestMappers.kt"))
            .findFirst()
            .orElseThrow());
        // an inline enum is a plain String field and is written as is
        assertFalse(mappers.contains("kindConverter"), mappers);
        // a $ref enum keeps its own untagged writer
        assertTrue(mappers.contains("statusConverter: HttpClientParameterWriter<Status>"), mappers);
        assertFalse(mappers.contains("@Json\n    public val statusConverter"), mappers);
        // a model part is written as JSON
        assertTrue(mappers.contains("@Json\n    public val metaConverter: HttpClientParameterWriter<Meta>"), mappers);
        assertTrue(mappers.contains("@Json\n    public val metasConverter: HttpClientParameterWriter<Meta>"), mappers);
        // an explicit JSON encoding is honoured, a part with a non-JSON encoding asks for an untagged writer
        assertTrue(mappers.contains("@Json\n    public val jsonMetaConverter: HttpClientParameterWriter<Meta>"), mappers);
        assertTrue(mappers.contains("public val plainMetaConverter: HttpClientParameterWriter<Meta>"), mappers);
        assertFalse(mappers.contains("@Json\n    public val plainMetaConverter"), mappers);
        assertTrue(mappers.contains("public val xmlMetasConverter: HttpClientParameterWriter<Meta>"), mappers);
        assertFalse(mappers.contains("@Json\n    public val xmlMetasConverter"), mappers);
        // the untagged writer is a default component that delegates to the @Json one, so the graph builds without an own writer
        var formParts = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiFormPartsModule.kt"))
            .findFirst()
            .orElseThrow()).replaceAll("\\s+", " ");
        assertTrue(formParts.contains("@DefaultComponent public fun metaFormPartWriter(@Json jsonWriter: HttpClientParameterWriter<Meta>): HttpClientParameterWriter<Meta>"), formParts);
        assertTrue(formParts.contains("uploadPet.plainMeta (text/plain), uploadPet.xmlMetas (text/xml)"), formParts);

        var apiPackage = "io.koraframework.openapi.generator." + name + ".kotlin_client.api";
        var app = sources.resolve("TestApp.kt");
        Files.writeString(app, """
            package %s

            @io.koraframework.common.annotation.KoraApp
            interface TestApp : io.koraframework.http.client.common.request.mapper.HttpClientParameterWriterModule {
                @io.koraframework.common.annotation.Root
                fun root(
                    submitPet: DefaultApiClientRequestMappers.SubmitPetFormParamRequestMapper,
                    uploadPet: DefaultApiClientRequestMappers.UploadPetFormParamRequestMapper,
                ) = ""
            }
            """.formatted(apiPackage));
        kc.withSrc(app);

        assertDoesNotThrow(() -> kc
            .withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpClientSymbolProcessorProvider(), new KoraAppProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile());
    }

    @Test
    void successfulClientResponseModeReturnsSuccessAndThrowsTypedException() throws Exception {
        var files = generate(
            "petstoreV3_client_successful_response",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_client_successful_response.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApi.kt"))
            .findFirst()
            .orElseThrow());
        var mapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiClientResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("@Mapping(value = PetsApiClientResponseMappers.CreatePetSuccessfulResponseMapper::class)"));
        assertTrue(apiContent.contains("public fun createPet("));
        assertTrue(apiContent.contains("CreatePet200ApiResponse"));
        assertTrue(apiContent.contains("FindPetPetApiResponse"));
        assertTrue(apiContent.contains("AmbiguousPetApiResponse"));
        assertTrue(apiContent.contains("@Mapping(value = PetsApiClientResponseMappers.AmbiguousPetSuccessfulResponseMapper::class)"));
        assertTrue(apiContent.contains("public class PetsApiModelErrorHttpClientResponseException("));
        assertTrue(apiContent.contains("public val content: ModelError"));
        assertTrue(apiContent.contains("body: ByteArray"));
        assertTrue(apiContent.contains("HttpClientResponseException(code, headers, body)"));
        assertFalse(apiContent.contains("PetsApiCreatePetHttpClientResponseException"));
        assertFalse(apiContent.contains("PetsApiFindPetHttpClientResponseException"));
        assertFalse(apiContent.contains("PetsApiAmbiguousPetHttpClientResponseException"));
        assertTrue(mapperContent.contains("public open class CreatePetSuccessfulResponseMapper("));
        assertTrue(mapperContent.contains("HttpClientResponseMapper<"));
        assertTrue(mapperContent.contains("CreatePet200ApiResponse"));
        assertTrue(mapperContent.contains("val _bufferedResponse = bufferedResponse(response)"));
        assertTrue(mapperContent.contains("this.createPet400ResponseMapper.apply(_bufferedResponse.response)"));
        assertTrue(mapperContent.contains("throw responseException(response, _bufferedResponse.body, e)"));
        assertTrue(mapperContent.contains("throw PetsApi.PetsApiModelErrorHttpClientResponseException"));
        assertTrue(mapperContent.contains("(_response as PetsApiResponses.CreatePetApiResponse.CreatePet400ApiResponse).content"));
        assertTrue(mapperContent.contains("SimpleHttpClientResponse(response.code(), response.headers(), HttpBody.of(contentType, bytes))"));
        assertTrue(mapperContent.contains("FindPetPetApiResponse"));
        assertTrue(mapperContent.contains("public open class AmbiguousPetSuccessfulResponseMapper("));
        assertTrue(mapperContent.contains("(_response as PetsApiResponses.AmbiguousPetApiResponse.AmbiguousPet400ApiResponse).content"));
    }

    @Test
    void successfulClientResponseModeReturnsSuccessOfDefaultOnlyOperation() throws Exception {
        var name = "petstoreV3_client_successful_response_default_only";
        var files = generate(
            name,
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_client_successful_response_default_only.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL")
        );
        var kc = new KotlinCompilation();
        var sources = kc.getBaseDir().resolve("sources");
        for (var file : files) {
            var target = sources.resolve(openapiSourcesDir.relativize(file.toPath()));
            Files.createDirectories(target.getParent());
            Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            if (target.toString().endsWith(".kt")) {
                kc.withSrc(target);
            }
        }
        var cl = kc
            .withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpClientSymbolProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile();

        var packageName = "io.koraframework.openapi.generator." + name + ".kotlin_client";
        var pet = cl.loadClass(packageName + ".model.Pet").getConstructors()[0].newInstance(1L, "Rex");
        HttpClientResponseMapper<Object> petMapper = response -> pet;
        var mappers = packageName + ".api.PetsApiClientResponseMappers$";
        var defaultMapper = cl.loadClass(mappers + "GetPet0ApiResponseMapper").getConstructors()[0].newInstance(petMapper);
        var mapper = (HttpClientResponseMapper<?>) cl.loadClass(mappers + "GetPetSuccessfulResponseMapper").getConstructors()[0].newInstance(defaultMapper);

        var ok = mapper.apply(new SimpleHttpClientResponse(200, HttpHeaders.of(), HttpBody.of("application/json", "{}".getBytes(StandardCharsets.UTF_8))));
        assertEquals(packageName + ".api.PetsApiResponses$GetPetApiResponse", ok.getClass().getName());

        var error = assertThrows(HttpClientResponseException.class, () -> mapper.apply(new SimpleHttpClientResponse(500, HttpHeaders.of(), HttpBody.of("application/json", "{}".getBytes(StandardCharsets.UTF_8)))));
        assertEquals(500, error.getCode());
    }

    @Test
    void sameResponseModelGetsSharedInterface() throws Exception {
        var files = generate(
            "petstoreV3_same_response_model",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_same_response_model.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().endsWith("ApiResponses.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("public interface GetErrorsModelErrorApiResponse : GetErrorsApiResponse"));
        assertTrue(content.contains("public val content: ModelError"));
        assertFalse(content.contains("public val message: String"));
        assertFalse(content.contains("public val details: String?"));
        assertTrue(content.contains("public val statusCode: Int"));
        assertTrue(content.contains("public data class GetErrors400ApiResponse("));
        assertTrue(content.contains(": GetErrorsModelErrorApiResponse"));
        assertTrue(content.contains("get() = 400"));
        assertFalse(content.contains("get() = content.details"));
    }

    @Test
    void enumValueTypesSupportDouble() throws Exception {
        var files = generate(
            "petstoreV3_enum",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_enum.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());
        var moduleContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet__NestedEnumMapperModule.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("public enum class NonReqDoubleEnum private constructor("));
        assertTrue(content.contains("public val `value`: Double"));
        assertFalse(content.contains("class JsonWriter"));
        assertTrue(moduleContent.contains("nonReqDoubleEnumJsonWriter("));
        assertTrue(moduleContent.contains("JsonWriter<Double>"));
        assertTrue(moduleContent.contains("JsonReader<Double>"));
    }

    @Test
    void nestedEnumMappersAreAggregatedByModel() throws Exception {
        var files = generate(
            "petstoreV3_validation_nested_enum",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_validation.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var moduleContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetTO__NestedEnumMapperModule.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(moduleContent.contains("public interface PetTO__NestedEnumMapperModule"));
        assertTrue(moduleContent.contains("JsonWriter<PetTO.StatusEnum>"));
        assertTrue(moduleContent.contains("JsonReader<PetTO.StatusEnum>"));
        assertTrue(moduleContent.contains("JsonWriter<PetTO.AvailabilityEnum>"));
        assertTrue(moduleContent.contains("JsonReader<PetTO.AvailabilityEnum>"));
        assertEquals(1, files.stream()
            .filter(file -> file.getName().startsWith("PetTO") && file.getName().endsWith("NestedEnumMapperModule.kt"))
            .count());
    }

    @Test
    void anonymousSecurityDoesNotRequireClientInterceptor() throws Exception {
        var files = generate(
            "petstoreV3_security_anonymous",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_anonymous.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PublicApi.kt"))
            .findFirst()
            .orElseThrow());
        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.indexOf("tag = ApiSecurity.Sec1_Anonymous::class") < apiContent.indexOf("optionalAccess("));
        assertTrue(apiContent.indexOf("tag = ApiSecurity.Sec1::class") < apiContent.indexOf("requiredAccess("));
        assertTrue(securityContent.contains("class Sec1_Anonymous"));
        assertTrue(securityContent.contains("class Sec1"));
        assertTrue(apiContent.lastIndexOf("OperationSecuritySchemaTag") < apiContent.indexOf("publicAccess("));
        assertFalse(securityContent.contains("if ()"));
        assertTrue(securityContent.contains("return chain.process(request)"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_body",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("BODY")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.kt"))
            .findFirst()
            .orElseThrow());
        var responsesContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());
        var modelContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());
        var errorContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("ErrorMessage.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("public fun storeInventory(@Header additionalHeaders: HttpHeaders, body: HttpBodyOutput): DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(apiContent.contains("public fun rawObject(@Header additionalHeaders: HttpHeaders, body: HttpBodyOutput): DefaultApiResponses.RawObjectApiResponse"));
        assertEquals(3, countJavadocReturnTags(apiContent));
        assertTrue(containsMultilineStoreInventoryReturn(apiContent));
        assertTrue(responsesContent.contains("public sealed interface StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("public data class StoreInventory200ApiResponse("));
        assertTrue(responsesContent.contains("public val content: HttpBodyInput"));
        assertTrue(responsesContent.contains("public data class StoreInventory400ApiResponse("));
        assertTrue(responsesContent.contains("public val content: ErrorMessage"));
        assertTrue(responsesContent.contains("public data class StoreInventory500ApiResponse("));
        assertTrue(responsesContent.contains("public val content: HttpBodyInput"));
        assertTrue(responsesContent.contains("public sealed interface RawObjectApiResponse"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(modelContent.contains("public data class Pet("));
        assertTrue(modelContent.contains("public val metadata: Any"));
        assertTrue(modelContent.contains("public val optionalMetadata: Any? = null"));
        assertTrue(errorContent.contains("public data class ErrorMessage("));
        assertTrue(errorContent.contains("public val message: String"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsObjectTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_object",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("OBJECT")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.kt"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("body: Any"));
        assertTrue(apiContent.contains("DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(apiContent.contains("DefaultApiResponses.RawObjectApiResponse"));
        assertFalse(apiContent.contains("additionalHeaders: HttpHeaders"));
        assertTrue(responsesContent.contains("public val content: Any"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpClientResponseMapper<Any>"));
        assertTrue(responseMapperContent.contains("@param:Json"));
    }

    @Test
    void bareObjectRequestAndResponseUseByteArrayByDefault() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_bytes_default",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApi.kt"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("public fun storeInventory(@Header additionalHeaders: HttpHeaders, body: ByteArray): DefaultApiResponses.StoreInventoryApiResponse"));
        assertTrue(apiContent.contains("public fun rawObject(@Header additionalHeaders: HttpHeaders, body: ByteArray): DefaultApiResponses.RawObjectApiResponse"));
        assertTrue(responsesContent.contains("public val content: ByteArray"));
        assertTrue(responsesContent.contains("public data class RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("public data class RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpClientResponseMapper<ByteArray>"));
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("public open class StoreInventory200ApiResponseMapper"));
    }

    @Test
    void basicAuthConfigIsGeneratedAsDataClass() throws Exception {
        var files = generate(
            "petstoreV3_security_basic_data_class",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_basic.yaml").toExternalForm(),
            new SwaggerParams.Options());

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertFalse(content.contains("@ConfigSource"), content);
        assertTrue(content.contains("@DefaultComponent\n  public fun securityConfig("), content);
        assertTrue(content.contains("public data class SecurityBasicAuthConfig"), content);
    }

    @Test
    void securityTagsUseSchemeNames() throws Exception {
        var files = generate(
            "petstoreV3_security_all_named_tags",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("class BearerAuth"));
        assertTrue(securityContent.contains("class ApiKeyAuth"));
        assertTrue(securityContent.contains("class BasicAuth"));
        assertTrue(securityContent.contains("class CookieAuth"));
        assertTrue(securityContent.contains("class OAuth"));
        assertFalse(securityContent.contains("class bearerAuth"));
        assertTrue(securityContent.contains("class BearerAuth_ApiKeyAuth_BasicAuth_CookieAuth_OAuth"));
        assertFalse(securityContent.contains("ReadPets"));
        assertFalse(securityContent.contains("WritePets"));
        assertFalse(securityContent.contains("OperationSecuritySchemaTag"));
    }

    @Test
    void base64JsonBodiesBuildIntoAGraph() throws Exception {
        var name = "petstoreV3_byte_json_body_client_graph";
        var files = generate(
            name,
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_byte_json_body.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var kc = new KotlinCompilation();
        var sources = kc.getBaseDir().resolve("sources");
        for (var file : files) {
            var target = sources.resolve(openapiSourcesDir.relativize(file.toPath()));
            Files.createDirectories(target.getParent());
            Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            if (target.toString().endsWith(".kt")) {
                kc.withSrc(target);
            }
        }
        var api = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("BytesApi.kt"))
            .findFirst()
            .orElseThrow());
        assertTrue(api.contains("fun postInlineBytes(@Json body: ByteArray)"), api);
        assertTrue(api.contains("fun postRefBytes(@Json body: ByteArray)"), api);

        var app = sources.resolve("TestApp.kt");
        Files.writeString(app, """
            package io.koraframework.openapi.generator.%s.kotlin_client.api

            @io.koraframework.common.annotation.KoraApp
            interface TestApp : io.koraframework.json.common.JsonModule {
                @io.koraframework.common.annotation.Root
                fun root(
                    inline: BytesApiClientResponseMappers.PostInlineBytes200ApiResponseMapper,
                    ref: BytesApiClientResponseMappers.PostRefBytes200ApiResponseMapper,
                ) = ""
            }
            """.formatted(name));
        kc.withSrc(app);

        assertDoesNotThrow(() -> kc
            .withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpClientSymbolProcessorProvider(), new KoraAppProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile());
    }

    @Test
    void securedOperationsWithNonCamelCaseOrMissingOperationIdAreIntercepted() throws Exception {
        var files = generate(
            "petstoreV3_security_operation_id",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_operation_id.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApi.kt"))
            .findFirst()
            .orElseThrow());

        // list_admin_users, get-admin-opsec, adminCamel and two operations without operationId; ping has `security: []`
        assertEquals(5, content.split("ApiSecurity.BearerAuth::class", -1).length - 1, content);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void securitySchemeNamesAreSanitizedToIdentifiers(boolean authAsArg) throws Exception {
        process(
            "petstoreV3_security_scheme_names",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_security_scheme_names.yaml").toExternalForm(),
            new SwaggerParams.Options().setAuthAsArg(authAsArg)
        );

        if (authAsArg) {
            var apiContent = readGenerated("PetsApi.kt");
            assertTrue(apiContent.contains("\"X-API-KEY\""), apiContent);
            assertTrue(apiContent.contains("partnerToken"), apiContent);
            assertTrue(apiContent.contains("jwtBearer"), apiContent);
        } else {
            var securityContent = readGenerated("ApiSecurity.kt");
            assertTrue(securityContent.contains("\"X-API-KEY\""), securityContent);
            assertTrue(securityContent.contains("\"test.security.api-key\""), securityContent);
            assertTrue(securityContent.contains("\"test.security.partner.token\""), securityContent);
            assertTrue(securityContent.contains("partnerTokenTokenProvider"), securityContent);
        }
    }

    @Test
    void optionalArgsOverloadsPassFormParam() throws Exception {
        process(
            "petstoreV3_form_optional_args",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_form_optional_args.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
    }

    @Test
    void defaultTagWithOnlyHttpClientTag() throws Exception {
        process(
            "petstoreV3_only_http_client_tag",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_request_parameters.yaml").toExternalForm(),
            new SwaggerParams.Options().setTags("""
                {"*": {"httpClientTag": "java.lang.String"}}
                """)
        );

        var apiContent = readGenerated("PetsApi.kt");
        assertTrue(apiContent.contains("httpClientTag"), apiContent);
        assertFalse(apiContent.contains("telemetryTag"), apiContent);
    }

    @Test
    void defaultTagWithOnlyTelemetryTag() throws Exception {
        process(
            "petstoreV3_only_telemetry_tag",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_request_parameters.yaml").toExternalForm(),
            new SwaggerParams.Options().setTags("""
                {"*": {"telemetryTag": "java.lang.String"}}
                """)
        );

        var apiContent = readGenerated("PetsApi.kt");
        assertTrue(apiContent.contains("telemetryTag"), apiContent);
        assertFalse(apiContent.contains("httpClientTag"), apiContent);
    }

    private String readGenerated(String fileName) throws Exception {
        try (var files = Files.walk(openapiSourcesDir)) {
            return Files.readString(files
                .filter(path -> path.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow());
        }
    }

    @Test
    void uppercaseResponseHeaderNamesAreCamelCase() throws Exception {
        var files = generate(
            "petstoreV3_responses_uppercase_headers",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_responses.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());

        // X-API-VERSION and X-RATE-LIMIT
        assertTrue(content.contains("xApiVersion"), content);
        assertTrue(content.contains("xRateLimit"), content);
        assertFalse(content.contains("X_API_VERSION"), content);
        assertFalse(content.contains("xAPIVERSION"), content);
    }
}
