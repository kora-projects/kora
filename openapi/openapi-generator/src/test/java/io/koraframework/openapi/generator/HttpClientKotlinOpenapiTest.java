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

    @Test
    void requiredNullableFieldIsAlwaysWritten() throws Exception {
        var files = generate(
            "petstoreV3_required_nullable",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_required_nullable.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Holder.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("@JsonInclude(value = JsonInclude.IncludeType.ALWAYS)\n  public val note: String?"), content);
    }

    @Test
    void oneOfSubtypeKeepsInlineEnumDiscriminatorWhenParentDoesNotDeclareIt() throws Exception {
        var files = generate(
            "petstoreV3_discriminator_inline_enum_one_of",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("InlineEnumOneOfCat.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("val petType: PetTypeEnum"), content);
        assertTrue(content.contains("enum class PetTypeEnum"), content);
        assertTrue(files.stream().anyMatch(f -> f.getName().startsWith("InlineEnumOneOfCat__NestedEnumMapperModule")), files::toString);
    }

    @Test
    void snakeCaseAllOfSubtypeUsesParentInlineEnumDiscriminator() throws Exception {
        var files = generate(
            "petstoreV3_discriminator_inline_enum_snake_case",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("FloatingIpActionAssign.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("override val type: FloatingIPsAction.TypeEnum"), content);
        assertTrue(content.contains(") : FloatingIPsAction"), content);
        assertFalse(content.contains("enum class"), content);
        assertTrue(files.stream().anyMatch(f -> f.getName().startsWith("FloatingIPsAction__NestedEnumMapperModule")), files::toString);
        assertTrue(files.stream().noneMatch(f -> f.getName().startsWith("FloatingIpActionAssign__NestedEnumMapperModule")), files::toString);
    }

    @Test
    void sealedSubtypesShareInlineEnumsOfParent() throws Exception {
        var name = "petstoreV3_discriminator_inline_enum_shared";
        var files = generate(
            name,
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_discriminator_inline_enum.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        assertTrue(files.stream().anyMatch(f -> f.getName().equals("Shape__NestedEnumMapperModule.kt")), files::toString);
        assertTrue(files.stream().anyMatch(f -> f.getName().equals("Animal__NestedEnumMapperModule.kt")), files::toString);
        assertTrue(files.stream().noneMatch(f -> f.getName().startsWith("ShapeCircle__") || f.getName().startsWith("ShapeSquare__")
            || f.getName().startsWith("EventCreated__") || f.getName().startsWith("AnimalCat__")), files::toString);
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

        var model = "io.koraframework.openapi.generator." + name + ".kotlin_client.model.";
        var kind = cl.loadClass(model + "Shape$KindEnum");
        var unit = cl.loadClass(model + "Shape$UnitEnum");
        // the shared enum has the values of the parent, the values a subtype adds and the mapping names the parent misses
        assertEquals("[CIRCLE, SQUARE, ROUND, TRIANGLE]", java.util.Arrays.toString(kind.getEnumConstants()).toUpperCase(java.util.Locale.ROOT));
        assertEquals("[MM, CM, INCH]", java.util.Arrays.toString(unit.getEnumConstants()).toUpperCase(java.util.Locale.ROOT));

        var circle = cl.loadClass(model + "ShapeCircle");
        assertEquals(0, circle.getDeclaredClasses().length);
        assertEquals(kind, circle.getMethod("getKind").getReturnType());
        assertEquals(unit, circle.getMethod("getUnit").getReturnType());
        // the parent declares a plain string, so the inline enum of the subtype has no class and its default stays a string
        var eventCreated = cl.loadClass(model + "EventCreated");
        assertEquals(String.class, eventCreated.getMethod("getType").getReturnType());
        assertEquals(0, eventCreated.getDeclaredClasses().length);
        var species = cl.loadClass(model + "Animal$SpeciesEnum");
        assertEquals(species, cl.loadClass(model + "AnimalCat").getMethod("getSpecies").getReturnType());
        assertEquals(species, cl.loadClass(model + "AnimalDog").getMethod("getSpecies").getReturnType());

        // a subtype accepts only its own discriminator values
        var circleConstructor = circle.getConstructor(kind, unit, Integer.class);
        var mm = unit.getEnumConstants()[0];
        assertDoesNotThrow(() -> circleConstructor.newInstance(kind.getEnumConstants()[0], mm, 1));
        var e = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> circleConstructor.newInstance(kind.getEnumConstants()[1], mm, 1));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Discriminator field 'kind' of ShapeCircle must be 'circle' or 'round', but was 'square'", e.getCause().getMessage());
        // the mapping takes precedence over the enum a subtype declares for the discriminator
        assertDoesNotThrow(() -> circleConstructor.newInstance(kind.getEnumConstants()[2], mm, 1));
        var triangleConstructor = cl.loadClass(model + "ShapeTriangle").getConstructor(kind, unit, Integer.class);
        assertDoesNotThrow(() -> triangleConstructor.newInstance(kind.getEnumConstants()[3], mm, 1));

        // an enum shared with the parent has the values of every subtype, a subtype accepts only the ones it declares
        var inch = unit.getEnumConstants()[2];
        e = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> circleConstructor.newInstance(kind.getEnumConstants()[0], inch, 1));
        assertEquals("Field 'unit' of ShapeCircle must be 'mm' or 'cm', but was 'inch'", e.getCause().getMessage());
        var squareConstructor = cl.loadClass(model + "ShapeSquare").getConstructor(kind, unit, Integer.class);
        assertDoesNotThrow(() -> squareConstructor.newInstance(kind.getEnumConstants()[1], inch, 1));

        // a string discriminator and a discriminator of an enum schema are checked too
        var eventDeletedConstructor = cl.loadClass(model + "EventDeleted").getConstructor(String.class, String.class);
        assertDoesNotThrow(() -> eventDeletedConstructor.newInstance("deleted", "1"));
        e = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> eventDeletedConstructor.newInstance("created", "1"));
        assertEquals("Discriminator field 'type' of EventDeleted must be 'deleted', but was 'created'", e.getCause().getMessage());
        var vehicleType = cl.loadClass(model + "VehicleType");
        var carConstructor = cl.loadClass(model + "VehicleCar").getConstructor(vehicleType, String.class, Integer.class);
        assertDoesNotThrow(() -> carConstructor.newInstance(vehicleType.getEnumConstants()[0], "n", 4));
        e = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> carConstructor.newInstance(vehicleType.getEnumConstants()[1], "n", 4));
        assertDoesNotThrow(() -> carConstructor.newInstance(vehicleType.getEnumConstants()[2], "n", 4));
        assertEquals("Discriminator field 'type' of VehicleCar must be 'automobile' or 'car', but was 'bike'", e.getCause().getMessage());

        // a property the parent does not allow to be null is not nullable in a subtype that declares it nullable
        var vehicleBike = Files.readString(files.stream().map(java.io.File::toPath).filter(p -> p.getFileName().toString().equals("VehicleBike.kt")).findFirst().orElseThrow());
        assertTrue(vehicleBike.contains("override val name: String,"), vehicleBike);

        // the discriminator is always there, though the schema does not require it
        var eventDeleted = Files.readString(files.stream().map(java.io.File::toPath).filter(p -> p.getFileName().toString().equals("EventDeleted.kt")).findFirst().orElseThrow());
        assertTrue(eventDeleted.contains("override val type: String,"), eventDeleted);
        // the default of an inline enum the parent declares as a plain string is that string
        var eventCreatedSource = Files.readString(files.stream().map(java.io.File::toPath).filter(p -> p.getFileName().toString().equals("EventCreated.kt")).findFirst().orElseThrow());
        assertTrue(eventCreatedSource.contains("override val type: String = \"created\","), eventCreatedSource);
    }

    @Test
    void subtypePropertyOfAnotherTypeThanParentFailsWithClearError() {
        var e = assertThrows(Exception.class, () -> generate(
            "petstoreV3_discriminator_property_mismatch",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_discriminator_property_mismatch.yaml").toExternalForm(),
            new SwaggerParams.Options()
        ));
        var message = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            message.append(t.getMessage()).append('\n');
        }

        assertTrue(message.toString().contains("Invalid OpenAPI schema `NodeLeaf`: property `weight` differs from the same property of its discriminator parent `Node`"), message.toString());
        assertTrue(message.toString().contains("Parent `Node` declares: java.lang.Integer"), message.toString());
        assertTrue(message.toString().contains("Subtype `NodeLeaf` declares: java.lang.String"), message.toString());
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
        var flat = mappers.replaceAll("\\s+", " ");
        // a JSON media type is recognised whatever its case, parameters and position in a list are, a string with it is written as JSON too
        assertTrue(flat.contains("@Json public val jsonMetaConverter: HttpClientParameterWriter<Meta>"), mappers);
        assertTrue(flat.contains("@Json public val listMetaConverter: HttpClientParameterWriter<Meta>"), mappers);
        assertTrue(flat.contains("@Json public val jsonNoteConverter: HttpClientParameterWriter<String>"), mappers);
        // any other media type of a model part has a tag of its own
        assertTrue(flat.contains("@Tag(value = ApiFormPartsModule.TextPlain::class) public val plainMetaConverter: HttpClientParameterWriter<Meta>"), mappers);
        assertTrue(flat.contains("@Tag(value = ApiFormPartsModule.TextXml::class) public val xmlMetasConverter: HttpClientParameterWriter<Meta>"), mappers);
        assertTrue(flat.contains("@Tag(value = ApiFormPartsModule.ApplicationProblemJson::class) public val problemMetaConverter: HttpClientParameterWriter<Meta>"), mappers);
        // a scalar with a text type keeps the stock conversion
        assertFalse(flat.contains("plainCountConverter"), mappers);
        // a part is sent with its media type, a part without one stays plain text
        assertTrue(flat.contains("FormMultipart.file(\"meta\", null, \"application/json\", metaConverter.convert(it).toByteArray())"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"jsonMeta\", null, \"Application/JSON; charset=utf-8\", jsonMetaConverter.convert(it).toByteArray())"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"listMeta\", null, \"application/json\", listMetaConverter.convert(it).toByteArray())"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"xmlMetas\", null, \"text/xml\", xmlMetasConverter.convert(item).toByteArray())"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"plainCount\", null, \"text/plain\", it.toString().toByteArray())"), mappers);
        assertTrue(flat.contains("FormMultipart.data(\"kind\", it)"), mappers);
        // a `format: byte` part is base64 text, sent with its media type when one is declared
        assertTrue(flat.contains("FormMultipart.data(\"plainBytes\", Base64.getEncoder().encodeToString(it))"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"typedBytes\", null, \"application/base64\", Base64.getEncoder().encodeToString(it).toByteArray())"), mappers);
        // an element of an array of arrays is a JSON part
        assertTrue(flat.contains("@Json public val nestedMetasConverter: HttpClientParameterWriter<List<Meta>>"), mappers);
        assertTrue(flat.contains("FormMultipart.file(\"nestedMetas\", null, \"application/json\", nestedMetasConverter.convert(item).toByteArray())"), mappers);
        // a url-encoded array is repeated fields, `explode: false` joins the values by the delimiter of the style
        assertTrue(flat.contains("for (item in it) { b.add(\"tags\", item) }"), mappers);
        assertTrue(flat.contains("b.add(\"csv\", \",\", it.map { item -> csvConverter.convert(item) })"), mappers);
        assertTrue(flat.contains("b.add(\"pipes\", \"|\", it.map { item -> item })"), mappers);
        assertTrue(flat.contains("b.add(\"spaces\", \" \", it.map { item -> item })"), mappers);
        // a url-encoded object is a field per property, each written by its type, unless it declares a JSON media type
        assertTrue(flat.contains("public val ownerAgeConverter: HttpClientParameterWriter<Int>"), mappers);
        assertTrue(flat.contains("public val ownerModeConverter: HttpClientParameterWriter<Owner.ModeEnum>"), mappers);
        assertFalse(flat.contains("public val ownerConverter: HttpClientParameterWriter<Owner>"), mappers);
        assertTrue(flat.contains("it.ownerName.let { _v -> b.add(\"ownerName\", _v) }"), mappers);
        assertTrue(flat.contains("it.age?.let { _v -> b.add(\"age\", ownerAgeConverter.convert(_v)) }"), mappers);
        assertTrue(flat.contains("it.nick.takeIf { _p -> _p.isDefined }?.value()?.let { _v -> b.add(\"nick\", _v) }"), mappers);
        assertTrue(flat.contains("for (item in _v) { b.add(\"scores\", ownerScoresConverter.convert(item)) }"), mappers);
        assertTrue(flat.contains("it.zip.let { _v -> b.add(\"zip\", addressZipConverter.convert(_v)) }"), mappers);
        assertTrue(flat.contains("@Json public val jsonOwnerConverter: HttpClientParameterWriter<Owner>"), mappers);
        // a JSON-like type has a default writer that delegates to the @Json one, a writer of a non-JSON type is provided by an application
        var formParts = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiFormPartsModule.kt"))
            .findFirst()
            .orElseThrow()).replaceAll("\\s+", " ");
        assertTrue(formParts.contains("@Tag(value = ApiFormPartsModule.ApplicationProblemJson::class) @DefaultComponent public fun metaApplicationProblemJsonFormPartWriter(@Json jsonWriter: HttpClientParameterWriter<Meta>): HttpClientParameterWriter<Meta>"), formParts);
        assertTrue(formParts.contains("uploadPet.problemMeta (application/problem+json)"), formParts);
        assertTrue(formParts.contains("public class TextPlain"), formParts);
        assertTrue(formParts.contains("public class TextXml"), formParts);
        assertFalse(formParts.contains("TextPlainFormPartWriter"), formParts);
        assertFalse(formParts.contains("TextXmlFormPartWriter"), formParts);

        var apiPackage = "io.koraframework.openapi.generator." + name + ".kotlin_client.api";
        var meta = "io.koraframework.openapi.generator." + name + ".kotlin_client.model.Meta";
        var app = sources.resolve("TestApp.kt");
        Files.writeString(app, """
            package %s

            @io.koraframework.common.annotation.KoraApp
            interface TestApp : io.koraframework.http.client.common.request.mapper.HttpClientParameterWriterModule, io.koraframework.json.common.JsonModule {
                @io.koraframework.common.annotation.Root
                fun root(
                    submitPet: DefaultApiClientRequestMappers.SubmitPetFormParamRequestMapper,
                    uploadPet: DefaultApiClientRequestMappers.UploadPetFormParamRequestMapper,
                ) = ""

                @io.koraframework.common.annotation.Tag(ApiFormPartsModule.TextPlain::class)
                fun plainMetaWriter() = io.koraframework.http.client.common.request.HttpClientParameterWriter<%2$s> { "plain" }

                @io.koraframework.common.annotation.Tag(ApiFormPartsModule.TextXml::class)
                fun xmlMetaWriter() = io.koraframework.http.client.common.request.HttpClientParameterWriter<%2$s> { "<meta/>" }
            }
            """.formatted(apiPackage, meta));
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
    void jsonSuffixMediaTypesUseJsonMappers() throws Exception {
        var files = generate(
            "petstoreV3_json_media_types",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_json_media_types.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        // application/problem+json response
        var responseMappers = readFile(files, "PetsApiClientResponseMappers.kt");
        assertTrue(responseMappers.contains("""
                @param:Json
                public val `delegate`: HttpClientResponseMapper<Problem>,
            """), responseMappers);
        // application/merge-patch+json request body
        var api = readFile(files, "PetsApi.kt");
        assertTrue(api.contains("patchPet(@Path(value = \"petId\") petId: String, @Json pet: Pet)"), api);
    }

    @Test
    void propertyNamesCollidingAfterCamelCaseAreUnique() throws Exception {
        var files = generate(
            "petstoreV3_property_names",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_property_names.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var model = readFile(files, "Pet.kt");
        assertTrue(model.contains("public val createdAt: String? = null,"), model);
        assertTrue(model.contains("""
              @param:JsonField(value = "createdAt")
              public val createdAt2: String? = null,
            """), model);
    }

    @Test
    void tagsAndOperationIdsAreSanitized() throws Exception {
        var files = generate(
            "petstoreV3_operation_names",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_operation_names.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        // tags `pets` and `Pets` are one api
        var pets = readFile(files, "PetsApi.kt");
        assertTrue(pets.contains("fun listPets()"), pets);
        assertTrue(pets.contains("fun getPet(@Path(value = \"petId\") petId: String)"), pets);
        assertTrue(readFile(files, "PetStoreApi.kt").contains("interface PetStoreApi"));
        assertTrue(readFile(files, "Class3rdPartyApi.kt").contains("interface Class3rdPartyApi"));
        // a run of capitals is one word of the api name
        assertTrue(readFile(files, "StoreApi.kt").contains("interface StoreApi"));
        assertTrue(readFile(files, "ApiKeysApi.kt").contains("interface ApiKeysApi"));
        // cyrillic operationId is transliterated
        var owners = readFile(files, "OwnersApi.kt");
        assertTrue(owners.contains("fun poluchitVladeltsa()"), owners);
    }

    @Test
    void parameterDefaultsAreTypedLiterals() throws Exception {
        var files = generate(
            "petstoreV3_defaults",
            "kotlin-client",
            getClass().getResource("/example/petstoreV3_defaults.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var api = readFile(files, "PetsApi.kt");
        assertTrue(api.contains("ratio: Float? = 0.5f,"), api);
        assertTrue(api.contains("weight: Double? = 1.0,"), api);
        assertTrue(api.contains("height: Double? = 1.5,"), api);
        assertTrue(api.contains("ownerId: UUID? = java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000001\"),"), api);
        assertTrue(api.contains("status: Status? = Status.ACTIVE,"), api);
        assertTrue(api.contains("priority: Priority? = Priority.NUMBER_2,"), api);
        assertTrue(api.contains("score: Score? = Score.NUMBER_1_5,"), api);
        // form parameters
        assertTrue(api.contains("public val ratio: Float? = 1.5f,"), api);
        assertTrue(api.contains("public val weight: Double? = 2.0,"), api);
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
