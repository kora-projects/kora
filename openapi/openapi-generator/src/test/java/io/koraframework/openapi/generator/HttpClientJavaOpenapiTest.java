package io.koraframework.openapi.generator;

import io.koraframework.http.client.common.exception.HttpClientResponseException;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.http.client.common.response.SimpleHttpClientResponse;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.annotation.processor.common.JavaCompilation;
import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.http.client.annotation.processor.HttpClientAnnotationProcessor;
import io.koraframework.json.annotation.processor.JsonAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.annotation.processor.ValidAnnotationProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.Diagnostic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class HttpClientJavaOpenapiTest extends BaseJavaOpenapiTest {
    @Test
    void mapResponseWithTypedValuesIsAJsonMap() throws Exception {
        var files = generate(
            "petstoreV3_map_response_java_client",
            "java-client",
            getClass().getResource("/example/petstoreV3_map_response.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var mappers = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientResponseMappers.java"))
            .findFirst()
            .orElseThrow());
        var responses = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.java"))
            .findFirst()
            .orElseThrow());

        // a map with typed additionalProperties is a JSON map, not a raw body
        assertTrue(responses.contains("record GetInventoryApiResponse(Map<String, Integer> content)"), responses);
        assertTrue(mappers.contains("@Json HttpClientResponseMapper<Map<String, Integer>> delegate"), mappers);
    }

    @Test
    void authorizationHeaderCarriesItsScheme() throws Exception {
        var files = generate(
            "petstoreV3_security_all_scheme",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_all.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("b.header(\"authorization\", \"Bearer \" + bearerAuth);"), content);
        assertTrue(content.contains("b.header(\"authorization\", \"Basic \" + basicAuth);"), content);
        assertTrue(content.contains("b.header(\"authorization\", \"Bearer \" + oAuth);"), content);
        assertTrue(content.contains("b.header(\"X-API-KEY\", apiKeyAuth);"), content);
    }

    @Test
    void objectQueryParameterFailsWithClearError() {
        var e = assertThrows(Exception.class, () -> generate(
            "petstoreV3_deep_object_query",
            "java-client",
            getClass().getResource("/example/petstoreV3_deep_object_query.yaml").toExternalForm(),
            new SwaggerParams.Options()
        ));
        var message = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            message.append(t.getMessage()).append('\n');
        }

        assertTrue(message.toString().contains("listPeople"), message.toString());
        assertTrue(message.toString().contains("relationship"), message.toString());
        assertTrue(message.toString().contains("not supported"), message.toString());
    }

    @Test
    void enumNamesCollidingBySignSpellOutPlus() throws Exception {
        var files = generate(
            "petstoreV3_enum_sign_collision_names",
            "java-client",
            getClass().getResource("/example/petstoreV3_enum_sign_collision.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Tz.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("String ETC_GMT_PLUS_1 = \"Etc/GMT+1\";"), content);
        assertTrue(content.contains("String ETC_GMT_1 = \"Etc/GMT-1\";"), content);
        assertTrue(content.contains("String ETC_GMT_PLUS_12 = \"Etc/GMT+12\";"), content);
        assertTrue(content.contains("String ETC_GMT_12 = \"Etc/GMT-12\";"), content);
    }

    @Test
    void onlyFinalOrIncompatibleObjectMethodNamesArePrefixed() throws Exception {
        var files = generate(
            "petstoreV3_operation_notify_names",
            "java-client",
            getClass().getResource("/example/petstoreV3_operation_notify.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("IssuesApi.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains(" callNotify("), content);
        assertTrue(content.contains(" clone("), content);
        assertTrue(content.contains(" finalize("), content);
    }

    @Test
    void objectQueryParameterErrorNamesGeneratedOperationId() {
        var e = assertThrows(Exception.class, () -> generate(
            "petstoreV3_deep_object_query_no_operation_id",
            "java-client",
            getClass().getResource("/example/petstoreV3_deep_object_query_no_operation_id.yaml").toExternalForm(),
            new SwaggerParams.Options()
        ));
        var message = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            message.append(t.getMessage()).append('\n');
        }

        assertTrue(message.toString().contains("in operation `peopleGet`"), message.toString());
    }

    @Test
    void modelEnumsAreTyped() throws Exception {
        var files = generate(
            "petstoreV3_model_enums_defaults_types",
            "java-client",
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

        var accountStatus = read.apply("AccountStatus.java");
        assertTrue(accountStatus.contains("public enum AccountStatus "), accountStatus);

        var holder = read.apply("Holder.java");
        assertTrue(holder.contains("@Nullable List<List<Holder.SignersEnum>> signers"), holder);
    }

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
    void requestMappersAreNotGeneratedWhenEmpty() throws Exception {
        var files = generate(
            "petstoreV3_discriminator_no_request_mappers",
            "java-client",
            getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        assertTrue(files.stream().noneMatch(file -> file.getName().endsWith("ClientRequestMappers.java")));
    }

    @Test
    void multipartFormWritesArraysAsRepeatedParts() throws Exception {
        var files = generate(
            "petstoreV3_form_multipart_client_types",
            "java-client",
            getClass().getResource("/example/petstoreV3_form.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientRequestMappers.java"))
            .findFirst()
            .orElseThrow());

        // a string array is written as one part per element, using the raw element, no converter
        var stringArray = nestedClass(content, "FormMultipartFormDataWithStringArrayPatchFormParamRequestMapper");
        assertFalse(stringArray.contains("tagsConverter"));
        assertTrue(stringArray.contains("for (var item : value.tags())"));
        assertTrue(stringArray.contains("l.add(FormMultipart.data(\"tags\", item))"));

        // an int array is written one part per element through an element-typed writer
        var intArray = nestedClass(content, "FormMultipartFormDataWithIntArrayPatchFormParamRequestMapper");
        assertTrue(intArray.contains("HttpClientParameterWriter<Integer> countsConverter"));
        assertTrue(intArray.contains("for (var item : value.counts())"));
        assertTrue(intArray.contains("l.add(FormMultipart.data(\"counts\", countsConverter.convert(item)))"));

        // an enum array likewise writes one part per element
        var enumArray = nestedClass(content, "FormMultipartFormDataWithEnumArrayPatchFormParamRequestMapper");
        assertTrue(enumArray.contains("HttpClientParameterWriter<CurrencyType> typesConverter"));
        assertTrue(enumArray.contains("for (var item : value.types())"));
        assertTrue(enumArray.contains("l.add(FormMultipart.data(\"types\", typesConverter.convert(item)))"));

        // the whole-list single-part form must be gone
        assertFalse(content.contains("countsConverter.convert(value.counts())"));
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
    void openIdConnectSecuritySendsBearerAuthorizationHeader() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_security_openid.yaml").toExternalForm();
        process("petstoreV3_security_openid", "java-client", spec, new SwaggerParams.Options());

        var files = generate("petstoreV3_security_openid", "java-client", spec, new SwaggerParams.Options());
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("b.header(\"authorization\", \"Bearer \" + openIdAuth);"), content);
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

        assertTrue(content.contains("_securityCookieHeader = _securityCookieHeader == null || _securityCookieHeader.isBlank() ? \"X-COOKIE-KEY=\" + CookieAuth"));
        assertTrue(content.contains("b.header(\"Cookie\", _securityCookieHeader)"));
        assertFalse(content.contains("Cookies are not supported yet"));
    }

    @Test
    void multipleCookieSecuritySchemesAreCombinedWithExistingCookies() throws Exception {
        var files = generate(
            "petstoreV3_security_cookie_and_client_interceptor",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_cookie_and.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
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
    void successfulResponseMappersBuildIntoAGraph() throws Exception {
        var name = "petstoreV3_client_successful_response_graph";
        var files = generate(
            name,
            "java-client",
            getClass().getResource("/example/petstoreV3_client_successful_response.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL")
        );
        var sources = new ArrayList<Path>();
        for (var file : files) {
            if (file.getName().endsWith(".java")) {
                sources.add(file.toPath().toAbsolutePath());
            }
        }
        var apiPackage = "io.koraframework.openapi.generator." + name + ".java_client.api";
        var app = javaSourcesDir.resolve("app").resolve("TestApp.java");
        Files.createDirectories(app.getParent());
        Files.writeString(app, """
            package %s;

            @io.koraframework.common.annotation.KoraApp
            public interface TestApp {
                @io.koraframework.common.annotation.Root
                default String root(
                    PetsApiClientResponseMappers.CreatePetSuccessfulResponseMapper createPet,
                    PetsApiClientResponseMappers.FindPetSuccessfulResponseMapper findPet,
                    PetsApiClientResponseMappers.PartialPetSuccessfulResponseMapper partialPet,
                    PetsApiClientResponseMappers.AmbiguousPetSuccessfulResponseMapper ambiguousPet) {
                    return "";
                }
            }
            """.formatted(apiPackage));
        sources.add(app);

        assertDoesNotThrow(() -> new JavaCompilation()
            .withProcessor(new JsonAnnotationProcessor(), new HttpClientAnnotationProcessor(), new KoraAppProcessor())
            .withSources(sources)
            .withTargetClassesDir(javaClasses)
            .withGeneratedSourcesDir(javaSourcesDir.resolve("generated"))
            .compile());
    }

    @ParameterizedTest
    @ValueSource(strings = {"petstoreV3", "petstoreV3_client_successful_response"})
    void successfulResponseModeCompilesWithXlintAllWerror(String spec) throws Exception {
        var files = generate(
            spec + "_successful_xlint",
            "java-client",
            getClass().getResource("/example/" + spec + ".yaml").toExternalForm(),
            new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL")
        );
        var sources = files.stream().map(java.io.File::toPath).map(Path::toAbsolutePath)
            .filter(p -> p.getFileName().toString().endsWith(".java")).toList();
        var compilation = new JavaCompilation()
            .withProcessor(new JsonAnnotationProcessor(), new HttpClientAnnotationProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor())
            .withSources(sources)
            .withTargetClassesDir(javaClasses)
            .withGeneratedSourcesDir(javaSourcesDir)
            .withOption("-Xlint:all")
            .withOption("-Xlint:-processing")
            .withOption("-Werror");
        try {
            compilation.compile();
        } catch (TestUtils.CompilationErrorException ignore) {
        }
        var problems = compilation.diagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR || d.getKind() == Diagnostic.Kind.WARNING || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
            .map(d -> d.getSource() + ":" + d.getLineNumber() + " [" + d.getCode() + "] " + d.getMessage(Locale.ENGLISH))
            .toList();
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void formRequestMappersBuildIntoAGraph() throws Exception {
        var name = "petstoreV3_form_parts_graph";
        var files = generate(
            name,
            "java-client",
            getClass().getResource("/example/petstoreV3_form_parts.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var sources = new ArrayList<Path>();
        for (var file : files) {
            if (file.getName().endsWith(".java")) {
                sources.add(file.toPath().toAbsolutePath());
            }
        }
        var mappers = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiClientRequestMappers.java"))
            .findFirst()
            .orElseThrow());
        // an inline enum is a plain String field and is written as is
        assertFalse(mappers.contains("kindConverter"), mappers);
        // a $ref enum keeps its own untagged writer
        assertTrue(mappers.contains("HttpClientParameterWriter<Status> statusConverter"), mappers);
        assertFalse(mappers.contains("@Json HttpClientParameterWriter<Status>"), mappers);
        // a model part is written as JSON
        assertTrue(mappers.contains("@Json HttpClientParameterWriter<Meta> metaConverter"), mappers);
        assertTrue(mappers.contains("@Json HttpClientParameterWriter<Meta> metasConverter"), mappers);
        var flat = mappers.replaceAll("\\s+", " ");
        // a JSON media type is recognised whatever its case, parameters and position in a list are, a string with it is written as JSON too
        assertTrue(flat.contains("@Json HttpClientParameterWriter<Meta> jsonMetaConverter"), mappers);
        assertTrue(flat.contains("@Json HttpClientParameterWriter<Meta> listMetaConverter"), mappers);
        assertTrue(flat.contains("@Json HttpClientParameterWriter<String> jsonNoteConverter"), mappers);
        // any other media type of a model part has a tag of its own
        assertTrue(flat.contains("@Tag(ApiFormPartsModule.TextPlain.class) HttpClientParameterWriter<Meta> plainMetaConverter"), mappers);
        assertTrue(flat.contains("@Tag(ApiFormPartsModule.TextXml.class) HttpClientParameterWriter<Meta> xmlMetasConverter"), mappers);
        assertTrue(flat.contains("@Tag(ApiFormPartsModule.ApplicationProblemJson.class) HttpClientParameterWriter<Meta> problemMetaConverter"), mappers);
        // a scalar with a text type keeps the stock conversion
        assertFalse(flat.contains("plainCountConverter"), mappers);
        // a part is sent with its media type, a part without one stays plain text
        assertTrue(flat.contains("FormMultipart.file(\"meta\", null, \"application/json\", metaConverter.convert(value.meta()).getBytes(StandardCharsets.UTF_8))"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"jsonMeta\", null, \"Application/JSON; charset=utf-8\", jsonMetaConverter.convert(value.jsonMeta()).getBytes(StandardCharsets.UTF_8))"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"listMeta\", null, \"application/json\", listMetaConverter.convert(value.listMeta()).getBytes(StandardCharsets.UTF_8))"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"xmlMetas\", null, \"text/xml\", xmlMetasConverter.convert(item).getBytes(StandardCharsets.UTF_8))"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"plainCount\", null, \"text/plain\", Objects.toString(value.plainCount()).getBytes(StandardCharsets.UTF_8))"), mappers);
        assertTrue(flat.contains("FormMultipart.data(\"kind\", Objects.toString(value.kind()))"), mappers);
        // an element of an array of arrays is a JSON part
        assertTrue(flat.contains("@Json HttpClientParameterWriter<List<Meta>> nestedMetasConverter"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"nestedMetas\", null, \"application/json\", nestedMetasConverter.convert(item).getBytes(StandardCharsets.UTF_8))"), mappers);
        // a url-encoded array is repeated fields, `explode: false` joins the values by the delimiter of the style
        assertTrue(flat.contains("for (var item : value.tags()) { b.add(\"tags\", item); }"), mappers);
        assertTrue(flat.contains("var _csv_joined = new StringJoiner(\",\"); for (var item : value.csv()) { _csv_joined.add(csvConverter.convert(item)); } b.add(\"csv\", _csv_joined.toString());"), mappers);
        assertTrue(flat.contains("var _pipes_joined = new StringJoiner(\"|\");"), mappers);
        assertTrue(flat.contains("var _spaces_joined = new StringJoiner(\" \");"), mappers);
        // a JSON-like type has a default writer that delegates to the @Json one, a writer of a non-JSON type is provided by an application
        var formParts = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiFormPartsModule.java"))
            .findFirst()
            .orElseThrow()).replaceAll("\\s+", " ");
        assertTrue(formParts.contains("@Tag(ApplicationProblemJson.class) @DefaultComponent default HttpClientParameterWriter<Meta> metaApplicationProblemJsonFormPartWriter( @Json HttpClientParameterWriter<Meta> jsonWriter)"), formParts);
        assertTrue(formParts.contains("uploadPet.problemMeta (application/problem+json)"), formParts);
        assertTrue(formParts.contains("final class TextPlain"), formParts);
        assertTrue(formParts.contains("final class TextXml"), formParts);
        assertFalse(formParts.contains("TextPlainFormPartWriter"), formParts);
        assertFalse(formParts.contains("TextXmlFormPartWriter"), formParts);

        var apiPackage = "io.koraframework.openapi.generator." + name + ".java_client.api";
        var meta = "io.koraframework.openapi.generator." + name + ".java_client.model.Meta";
        var app = javaSourcesDir.resolve("app").resolve("TestApp.java");
        Files.createDirectories(app.getParent());
        Files.writeString(app, """
            package %s;

            @io.koraframework.common.annotation.KoraApp
            public interface TestApp extends io.koraframework.http.client.common.request.mapper.HttpClientParameterWriterModule, io.koraframework.json.common.JsonModule {
                @io.koraframework.common.annotation.Root
                default String root(
                    DefaultApiClientRequestMappers.SubmitPetFormParamRequestMapper submitPet,
                    DefaultApiClientRequestMappers.UploadPetFormParamRequestMapper uploadPet) {
                    return "";
                }

                @io.koraframework.common.annotation.Tag(ApiFormPartsModule.TextPlain.class)
                default io.koraframework.http.client.common.request.HttpClientParameterWriter<%2$s> plainMetaWriter() {
                    return value -> "plain";
                }

                @io.koraframework.common.annotation.Tag(ApiFormPartsModule.TextXml.class)
                default io.koraframework.http.client.common.request.HttpClientParameterWriter<%2$s> xmlMetaWriter() {
                    return value -> "<meta/>";
                }
            }
            """.formatted(apiPackage, meta));
        sources.add(app);

        assertDoesNotThrow(() -> new JavaCompilation()
            .withProcessor(new JsonAnnotationProcessor(), new HttpClientAnnotationProcessor(), new KoraAppProcessor())
            .withSources(sources)
            .withTargetClassesDir(javaClasses)
            .withGeneratedSourcesDir(javaSourcesDir.resolve("generated"))
            .compile());
    }

    @Test
    void successfulClientResponseModeReturnsSuccessAndThrowsTypedException() throws Exception {
        var files = generate(
            "petstoreV3_client_successful_response",
            "java-client",
            getClass().getResource("/example/petstoreV3_client_successful_response.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL")
        );

        var apiContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApi.java"))
            .findFirst()
            .orElseThrow());
        var mapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiClientResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(apiContent.contains("@Mapping(PetsApiClientResponseMappers.CreatePetSuccessfulResponseMapper.class)"));
        assertTrue(apiContent.contains("CreatePet200ApiResponse createPet("));
        assertTrue(apiContent.contains("FindPetPetApiResponse findPet("));
        assertTrue(apiContent.contains("AmbiguousPetApiResponse ambiguousPet("));
        assertTrue(apiContent.contains("@Mapping(PetsApiClientResponseMappers.AmbiguousPetSuccessfulResponseMapper.class)"));
        assertTrue(apiContent.contains("class PetsApiModelErrorHttpClientResponseException extends HttpClientResponseException"));
        assertTrue(apiContent.contains("private final ModelError content"));
        assertTrue(apiContent.contains("ModelError getContent()"));
        assertTrue(apiContent.contains("byte[] body"));
        assertTrue(apiContent.contains("super(code, headers, body)"));
        assertFalse(apiContent.contains("PetsApiCreatePetHttpClientResponseException"));
        assertFalse(apiContent.contains("PetsApiFindPetHttpClientResponseException"));
        assertFalse(apiContent.contains("PetsApiAmbiguousPetHttpClientResponseException"));
        assertTrue(mapperContent.contains("class CreatePetSuccessfulResponseMapper implements HttpClientResponseMapper<"));
        assertTrue(mapperContent.contains("CreatePet200ApiResponse"));
        assertTrue(mapperContent.contains("case 400 ->"));
        assertTrue(mapperContent.contains("var _bufferedResponse = bufferedResponse(response)"));
        assertTrue(mapperContent.contains("this.createPet400ResponseMapper.apply(_bufferedResponse.response())"));
        assertTrue(mapperContent.contains("throw responseException(response, _bufferedResponse.body(), e)"));
        assertTrue(mapperContent.contains("throw new PetsApi.PetsApiModelErrorHttpClientResponseException"));
        assertTrue(mapperContent.contains("((PetsApiResponses.CreatePetApiResponse.CreatePet400ApiResponse) _response).content()"));
        assertTrue(mapperContent.contains("new SimpleHttpClientResponse(response.code(), response.headers(), HttpBody.of(contentType, bytes))"));
        assertTrue(mapperContent.contains("class FindPetSuccessfulResponseMapper implements HttpClientResponseMapper<"));
        assertTrue(mapperContent.contains("FindPetPetApiResponse"));
        assertTrue(mapperContent.contains("class AmbiguousPetSuccessfulResponseMapper implements HttpClientResponseMapper<"));
        assertTrue(mapperContent.contains("((PetsApiResponses.AmbiguousPetApiResponse.AmbiguousPet400ApiResponse) _response).content()"));
    }

    @Test
    void successfulClientResponseModeReturnsSuccessOfDefaultOnlyOperation() throws Exception {
        var name = "petstoreV3_client_successful_response_default_only";
        var files = generate(
            name,
            "java-client",
            getClass().getResource("/example/petstoreV3_client_successful_response_default_only.yaml").toExternalForm(),
            new SwaggerParams.Options().setClientResponseMode("SUCCESSFUL")
        );
        var sources = files.stream()
            .map(file -> file.toPath().toAbsolutePath())
            .filter(path -> path.getFileName().toString().endsWith(".java"))
            .toList();
        var cl = new JavaCompilation()
            .withProcessor(new JsonAnnotationProcessor(), new HttpClientAnnotationProcessor())
            .withSources(sources)
            .withTargetClassesDir(javaClasses)
            .withGeneratedSourcesDir(javaSourcesDir)
            .compile();

        var packageName = "io.koraframework.openapi.generator." + name + ".java_client";
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
            .filter(path -> path.getFileName().toString().equals("PetDog__NestedEnumMapperModule.java"))
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

        assertTrue(moduleContent.contains("public interface PetDog__NestedEnumMapperModule"));
        assertTrue(moduleContent.contains("@DefaultComponent"));
        assertTrue(moduleContent.contains("default JsonWriter<PetDog.BreedEnum> breedEnumJsonWriter()"));
        assertTrue(moduleContent.contains("default JsonReader<PetDog.BreedEnum> breedEnumJsonReader()"));
        assertTrue(moduleContent.contains("default HttpClientParameterWriter<PetDog.BreedEnum> breedEnumStringParameterConverter()"));
        assertTrue(moduleContent.contains("default JsonWriter<PetDog.IntBreedEnum> intBreedEnumJsonWriter()"));
        assertTrue(moduleContent.contains("default JsonReader<PetDog.IntBreedEnum> intBreedEnumJsonReader()"));
        assertTrue(moduleContent.contains("default HttpClientParameterWriter<PetDog.IntBreedEnum> intBreedEnumStringParameterConverter()"));
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
            .filter(path -> path.getFileName().toString().equals("Pet__NestedEnumMapperModule.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(moduleContent.contains("default JsonWriter<Pet.NonReqDoubleEnum> nonReqDoubleEnumJsonWriter(JsonWriter<Double> delegate)"));
        assertTrue(moduleContent.contains("return new EnumJsonWriter<>(Pet.NonReqDoubleEnum.values(), Pet.NonReqDoubleEnum::getValue, delegate)"));
        assertTrue(moduleContent.contains("default JsonReader<Pet.NonReqDoubleEnum> nonReqDoubleEnumJsonReader(JsonReader<Double> delegate)"));
        assertTrue(moduleContent.contains("return new EnumJsonReader<>(Pet.NonReqDoubleEnum.values(), Pet.NonReqDoubleEnum::getValue, delegate)"));
    }

    @Test
    void nestedEnumMappersAreAggregatedByModel() throws Exception {
        var files = generate(
            "petstoreV3_validation_nested_enum",
            "java-client",
            getClass().getResource("/example/petstoreV3_validation.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var moduleContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetTO__NestedEnumMapperModule.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(moduleContent.contains("public interface PetTO__NestedEnumMapperModule"));
        assertTrue(moduleContent.contains("JsonWriter<PetTO.StatusEnum> statusEnumJsonWriter()"));
        assertTrue(moduleContent.contains("JsonReader<PetTO.StatusEnum> statusEnumJsonReader()"));
        assertTrue(moduleContent.contains("JsonWriter<PetTO.AvailabilityEnum> availabilityEnumJsonWriter()"));
        assertTrue(moduleContent.contains("JsonReader<PetTO.AvailabilityEnum> availabilityEnumJsonReader()"));
        assertEquals(1, files.stream()
            .filter(file -> file.getName().startsWith("PetTO") && file.getName().endsWith("NestedEnumMapperModule.java"))
            .count());
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

    @Test
    void base64JsonBodiesBuildIntoAGraph() throws Exception {
        var name = "petstoreV3_byte_json_body_client_graph";
        var files = generate(
            name,
            "java-client",
            getClass().getResource("/example/petstoreV3_byte_json_body.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var sources = new ArrayList<Path>();
        for (var file : files) {
            if (file.getName().endsWith(".java")) {
                sources.add(file.toPath().toAbsolutePath());
            }
        }
        var api = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("BytesApi.java"))
            .findFirst()
            .orElseThrow());
        assertTrue(api.contains("postInlineBytes(@Json byte[] body)"), api);
        assertTrue(api.contains("postRefBytes(@Json byte[] body)"), api);

        var app = javaSourcesDir.resolve("app").resolve("TestApp.java");
        Files.createDirectories(app.getParent());
        Files.writeString(app, """
            package io.koraframework.openapi.generator.%s.java_client.api;

            @io.koraframework.common.annotation.KoraApp
            public interface TestApp extends io.koraframework.json.common.JsonModule {
                @io.koraframework.common.annotation.Root
                default String root(
                    BytesApiClientResponseMappers.PostInlineBytes200ApiResponseMapper inline,
                    BytesApiClientResponseMappers.PostRefBytes200ApiResponseMapper ref) {
                    return "";
                }
            }
            """.formatted(name));
        sources.add(app);

        assertDoesNotThrow(() -> new JavaCompilation()
            .withProcessor(new JsonAnnotationProcessor(), new HttpClientAnnotationProcessor(), new KoraAppProcessor())
            .withSources(sources)
            .withTargetClassesDir(javaClasses)
            .withGeneratedSourcesDir(javaSourcesDir.resolve("generated"))
            .compile());
    }

    @Test
    void securedOperationsWithNonCamelCaseOrMissingOperationIdAreIntercepted() throws Exception {
        var files = generate(
            "petstoreV3_security_operation_id",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_operation_id.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApi.java"))
            .findFirst()
            .orElseThrow());

        // list_admin_users, get-admin-opsec, adminCamel and two operations without operationId; ping has `security: []`
        assertEquals(5, content.split("ApiSecurity.BearerAuth.class", -1).length - 1, content);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void securitySchemeNamesAreSanitizedToIdentifiers(boolean authAsArg) throws Exception {
        process(
            "petstoreV3_security_scheme_names",
            "java-client",
            getClass().getResource("/example/petstoreV3_security_scheme_names.yaml").toExternalForm(),
            new SwaggerParams.Options().setAuthAsArg(authAsArg)
        );

        if (authAsArg) {
            var apiContent = readGenerated("PetsApi.java");
            assertTrue(apiContent.contains("\"X-API-KEY\""), apiContent);
            assertTrue(apiContent.contains("partnerToken"), apiContent);
            assertTrue(apiContent.contains("jwtBearer"), apiContent);
        } else {
            var securityContent = readGenerated("ApiSecurity.java");
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
            "java-client",
            getClass().getResource("/example/petstoreV3_form_optional_args.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
    }

    @Test
    void defaultTagWithOnlyHttpClientTag() throws Exception {
        process(
            "petstoreV3_only_http_client_tag",
            "java-client",
            getClass().getResource("/example/petstoreV3_request_parameters.yaml").toExternalForm(),
            new SwaggerParams.Options().setTags("""
                {"*": {"httpClientTag": "java.lang.String"}}
                """)
        );

        var apiContent = readGenerated("PetsApi.java");
        assertTrue(apiContent.contains("httpClientTag"), apiContent);
        assertFalse(apiContent.contains("telemetryTag"), apiContent);
    }

    @Test
    void defaultTagWithOnlyTelemetryTag() throws Exception {
        process(
            "petstoreV3_only_telemetry_tag",
            "java-client",
            getClass().getResource("/example/petstoreV3_request_parameters.yaml").toExternalForm(),
            new SwaggerParams.Options().setTags("""
                {"*": {"telemetryTag": "java.lang.String"}}
                """)
        );

        var apiContent = readGenerated("PetsApi.java");
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
            "java-client",
            getClass().getResource("/example/petstoreV3_responses.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.java"))
            .findFirst()
            .orElseThrow());

        // X-API-VERSION and X-RATE-LIMIT
        assertTrue(content.contains("xApiVersion"), content);
        assertTrue(content.contains("xRateLimit"), content);
        assertFalse(content.contains("X_API_VERSION"), content);
        assertFalse(content.contains("xAPIVERSION"), content);
    }
}
