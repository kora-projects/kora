package io.koraframework.openapi.generator;

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider;
import io.koraframework.http.server.symbol.procesor.HttpControllerProcessorProvider;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.ksp.JsonSymbolProcessorProvider;
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider;
import io.koraframework.ksp.common.KotlinCompilation;
import io.koraframework.validation.symbol.processor.ValidSymbolProcessorProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HttpServerKotlinOpenapiTest extends BaseKotlinOpenapiTest {

    @Test
    void mapResponseWithTypedValuesIsAJsonMap() throws Exception {
        var files = generate(
            "petstoreV3_map_response_kotlin_server",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_map_response.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var mappers = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.kt"))
            .findFirst()
            .orElseThrow());
        var responses = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiResponses.kt"))
            .findFirst()
            .orElseThrow());

        // a map with typed additionalProperties is a JSON map, not a raw body
        assertTrue(responses.contains("public val content: Map<String, Int>,"), responses);
        assertTrue(mappers.contains("@param:Json\n    public val response200Delegate: HttpServerResponseMapper<HttpResponseEntity<Map<String, Int>>>"), mappers);
    }

    @Test
    void throwExceptionDelegateGivesWayToAnApplicationDelegate() throws Exception {
        var name = "petstoreV3_default_delegate_graph";
        var files = generate(
            name,
            "kotlin-server",
            getClass().getResource("/example/petstoreV3.yaml").toExternalForm(),
            new SwaggerParams.Options().setDefaultDelegate(true)
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
        var apiPackage = "io.koraframework.openapi.generator." + name + ".kotlin_server.api";
        var app = sources.resolve("TestApp.kt");
        Files.writeString(app, """
            package %s

            @io.koraframework.common.annotation.Component
            class ApplicationPetsDelegate : PetsApiDelegate

            @io.koraframework.common.annotation.KoraApp
            interface TestApp {
                @io.koraframework.common.annotation.Root
                fun root(delegate: PetsApiDelegate) = ""
            }
            """.formatted(apiPackage));
        kc.withSrc(app);

        assertDoesNotThrow(() -> kc
            .withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpControllerProcessorProvider(), new AopSymbolProcessorProvider(), new KoraAppProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile());
    }

    @Test
    void enumsCompileWithoutRedundantConversionWarnings() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_enum.yaml").toExternalForm();
        var kc = process("petstoreV3_enum", "kotlin-server", spec, new SwaggerParams.Options());

        assertTrue(kc.getCompilerMessages().stream().noneMatch(m -> m.toLowerCase().contains("redundant call of conversion method")), () -> String.join("\n", kc.getCompilerMessages()));
    }

    @Test
    void validationAppliesSchemaConstraintsToFormParams() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_validation_form_params.yaml").toExternalForm();
        process("petstoreV3_validation_form_params", "kotlin-server", spec, new SwaggerParams.Options());
        var generated = java.nio.file.Path.of("build/out", "petstoreV3_validation_form_params", "kotlin-server");
        String controller;
        String delegate;
        String proxy;
        try (var files = Files.walk(generated)) {
            var paths = files.toList();
            controller = Files.readString(paths.stream().filter(p -> p.getFileName().toString().equals("ShelvesApiController.kt")).findFirst().orElseThrow());
            delegate = Files.readString(paths.stream().filter(p -> p.getFileName().toString().equals("ShelvesApiDelegate.kt")).findFirst().orElseThrow());
            proxy = Files.readString(paths.stream().filter(p -> p.getFileName().toString().equals("$ShelvesApiController__AopProxy.kt")).findFirst().orElseThrow());
        }
        var flat = controller.replaceAll("\\s+", " ");
        var submitForm = flat.substring(flat.indexOf("public data class SubmitShelfFormParam"));
        var uploadForm = flat.substring(flat.indexOf("public data class UploadShelfFormParam"));

        assertTrue(flat.contains("@Valid form: SubmitShelfFormParam"), controller);
        assertTrue(flat.contains("@Valid form: UploadShelfFormParam"), controller);
        assertTrue(flat.contains("@Valid public data class SubmitShelfFormParam"), controller);
        assertTrue(submitForm.contains("@field:Size( min = 3, max = 10, ) @field:Pattern(value = \"^[a-z]+${'$'}\") public val name: String"), submitForm);
        assertTrue(submitForm.contains("@field:Min(value = 18L) public val size: Int"), submitForm);
        assertTrue(submitForm.contains("public val tags: List<@Size(max = 5) String>?"), submitForm);
        assertTrue(uploadForm.contains("@field:Size(max = 5) public val title: String"), uploadForm);
        // file parts carry no constraints
        assertTrue(uploadForm.contains("(required) */ public val `file`: FormMultipart.FormPart"), uploadForm);
        // the delegate's own form class is never used as a parameter type, so it gets no validation
        assertFalse(delegate.contains("@Valid"), delegate);
        // the @Validate proxy checks the form argument with its generated validator
        assertTrue(proxy.contains("validator1.validate(form, _argsContext_form)"), proxy);
        assertTrue(proxy.contains("validator2.validate(form, _argsContext_form)"), proxy);
    }

    @Test
    void deprecatedOperationCompilesWithoutWarnings() throws Exception {
        // findPetsByTags is deprecated: the controller calling the deprecated delegate method must not warn
        var spec = getClass().getResource("/example/petstoreV2.yaml").toExternalForm();
        var kc = process("petstoreV2_no_warnings", "kotlin-server", spec, new SwaggerParams.Options());
        assertNoWarningsInGeneratedSources(kc);
    }

    @Test
    void discriminatorModelsCompileWithoutWarnings() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm();
        var kc = process("petstoreV3_discriminator_no_warnings", "kotlin-server", spec, new SwaggerParams.Options());
        assertNoWarningsInGeneratedSources(kc);
    }

    @Test
    void specTextWithFormatPlaceholdersReachesTheDocsLiterally() throws Exception {
        var files = generate(
            "petstoreV3_format_symbols_docs",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_format_symbols.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var delegate = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiDelegate.kt"))
            .findFirst()
            .orElseThrow());
        assertTrue(delegate.contains("Pet by id, 100% match"), delegate);
        assertTrue(delegate.contains("Plus in the query is %2B, placeholders %L %S %N %T %1L $L $S $N $T $$ %% stay literal"), delegate);
        assertTrue(delegate.contains("Id of the pet, `+` goes as %2B"), delegate);
        assertTrue(delegate.contains("Pet found, 100% %L $L (status code 200)"), delegate);

        var model = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());
        assertTrue(model.contains("Name, `+` as %2B, %S $S"), model);
    }

    @Test
    void validationKeepsEveryConstraintOfAProperty() throws Exception {
        var files = generate(
            "petstoreV3_validation_combined",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_validation_combined.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var model = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Order.kt"))
            .findFirst()
            .orElseThrow());
        var delegate = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("OrdersApiDelegate.kt"))
            .findFirst()
            .orElseThrow());

        var flat = model.replaceAll("\\s+", " ");
        assertTrue(flat.contains("@field:PositiveOrZero public val amount: BigDecimal"), model);
        assertTrue(flat.contains("from = 0.5, to = Double.MAX_VALUE, boundary = Range.Boundary.INCLUSIVE_INCLUSIVE, ) public val fee"), model);
        assertTrue(flat.contains("from = -Double.MAX_VALUE, to = 10.0, boundary = Range.Boundary.INCLUSIVE_INCLUSIVE, ) public val temperature"), model);
        // a fractional maximum used to be replaced by the minimum
        assertTrue(flat.contains("from = 0.5, to = 10.5, boundary = Range.Boundary.INCLUSIVE_INCLUSIVE, ) public val ratio"), model);
        assertFalse(model.contains("MIN_VALUE"), model);
        assertTrue(flat.contains("max = 64, ) @field:Pattern(value = \".*\\\\S.*\") public val code"), model);
        assertTrue(flat.contains("max = Int.MAX_VALUE, ) @field:Valid public val lines"), model);
        assertTrue(delegate.contains("@PositiveOrZero"), delegate);
        assertTrue(delegate.contains("@Pattern(value = \"^[A-Z]+$\")") || delegate.contains("@Pattern(value = \"^[A-Z]+${'$'}\")"), delegate);
    }

    @Test
    void validationPutsLengthAndPatternOnlyOnStringsAndValidatesNestedArraysOfModels() throws Exception {
        var files = generate(
            "petstoreV3_validation_formats",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_validation_formats.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var model = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Event.kt"))
            .findFirst()
            .orElseThrow());
        var delegate = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("EventsApiDelegate.kt"))
            .findFirst()
            .orElseThrow());

        var flat = model.replaceAll("\\s+", " ");
        // there are no length or pattern validators for these types, so the graph could not be built
        assertTrue(flat.contains("public val id: UUID"), model);
        assertFalse(flat.contains("max = 36"), model);
        assertFalse(flat.contains("max = 10,"), model);
        assertFalse(flat.contains("max = 100,"), model);
        assertFalse(flat.contains("max = 1,"), model);
        assertFalse(model.contains("d{4}"), model);
        assertFalse(delegate.contains("max = 36"), delegate);
        // a plain string keeps its constraints
        assertTrue(flat.contains("@field:Size(max = 3) @field:Pattern(value = \"^[A-Z]+${'$'}\") public val code: String"), model);
        assertTrue(delegate.contains("max = 8"), delegate);
        // an array of arrays of models is validated down to the models
        assertTrue(flat.contains("@field:Valid public val children: List<List<Event>>?"), model);
    }

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

    @Test
    void freeFormMapPropertyBecomesMapOfAny() throws Exception {
        var files = generate(
            "petstoreV3_additional_props_free_form",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_additional_props.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("propsFreeForm: Map<String, Any>?"), content);
    }

    @Test
    void dateTimeFollowsTypeMappings() throws Exception {
        var files = generate(
            "petstoreV3_types_instant",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_types.yaml").toExternalForm(),
            new SwaggerParams.Options().setTypeMappings(java.util.Map.of("DateTime", "java.time.Instant"))
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("dateTime: Instant"), content);
        assertTrue(content.contains("import java.time.Instant"), content);
    }

    @Test
    void urlEncodedFormMapsAbsentOptionalFieldsToNull() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedOptionalPatchFormParamRequestMapper");

        // an absent optional scalar never reaches its converter
        assertTrue(mapper.contains("val count = _count_str?.let { countConverter.read(it) }"), mapper);
        // an absent optional array is null instead of a call on a nullable part, which did not compile
        assertTrue(mapper.contains("val tags = _tags_part?.values()"), mapper);
        assertTrue(mapper.contains("val ids = _ids_part?.values()?.asSequence()?.map(this.idsConverter::read)?.toList()"), mapper);
        // a required field is still checked
        assertTrue(mapper.contains("if (name == null)"), mapper);
    }

    @Test
    void multipartModelPartIsReadWithJsonReader() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormMultipartJsonPartPatchFormParamRequestMapper").replaceAll("\\s+", " ");

        // a model part defaults to application/json, an explicit JSON encoding is honoured too
        assertTrue(mapper.contains("@param:Json public val metaConverter: HttpServerParameterReader<Info>"), mapper);
        assertTrue(mapper.contains("@param:Json public val encodedMetaConverter: HttpServerParameterReader<Info>"), mapper);
        // a part with an explicit non-JSON encoding and an enum part keep the plain reader
        assertTrue(mapper.contains(" public val plainMetaConverter: HttpServerParameterReader<Info>"), mapper);
        assertFalse(mapper.contains("@param:Json public val plainMetaConverter"), mapper);
        assertTrue(mapper.contains(" public val typeConverter: HttpServerParameterReader<CurrencyType>"), mapper);
        assertFalse(mapper.contains("@param:Json public val typeConverter"), mapper);
    }

    @Test
    void formDeclaringBothContentTypesIsReadByRequestContentType() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedAndMultipartPatchFormParamRequestMapper");

        assertTrue(mapper.contains("val _contentType = rq.headers().getFirst(\"content-type\")"), mapper);
        assertTrue(mapper.contains("if (_contentType != null && _contentType.lowercase().startsWith(\"multipart/form-data\"))"), mapper);
        assertTrue(mapper.contains("MultipartReaderUtils.read(rq)"), mapper);
        assertTrue(mapper.contains("FormUrlEncodedServerRequestMapper.read(_bodyString)"), mapper);
        assertTrue(mapper.indexOf("MultipartReaderUtils.read(rq)") < mapper.indexOf("FormUrlEncodedServerRequestMapper.read(_bodyString)"), mapper);
    }

    @Test
    void multipartModelArrayPartIsReadWithJsonReader() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormMultipartModelArrayPatchFormParamRequestMapper").replaceAll("\\s+", " ");

        // each element is a JSON model, so the element reader resolves with JsonModule
        assertTrue(mapper.contains("@param:Json public val metasConverter: HttpServerParameterReader<Info>"), mapper);
    }

    @Test
    void urlEncodedBinaryFieldOfDualFormIsDataPart() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedAndMultipartPatchFormParamRequestMapper");

        // the form class holds a FormPart for a binary field, so the url-encoded value is wrapped into one
        assertTrue(mapper.contains("val `file` = _file_str?.let { FormMultipart.data(\"file\", it) }"), mapper);
        assertTrue(mapper.contains("val files = _files_part?.values()?.asSequence()?.map { FormMultipart.data(\"files\", it) }?.toList()"), mapper);
        assertFalse(mapper.contains("fileConverter"), mapper);
        assertFalse(mapper.contains("filesConverter"), mapper);
    }

    @Test
    void urlEncodedBinaryFieldIsDataPart() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedBinaryPatchFormParamRequestMapper");

        assertTrue(mapper.contains("val doc = FormMultipart.data(\"doc\", _doc_str)"), mapper);
        assertFalse(mapper.contains("Converter"), mapper);
    }

    @Test
    void urlEncodedByteFieldsAreBase64Decoded() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedBytePatchFormParamRequestMapper");

        assertTrue(mapper.contains("val req = Base64.getDecoder().decode(_req_str)"), mapper);
        assertTrue(mapper.contains("val opt = _opt_str?.let { Base64.getDecoder().decode(it) }"), mapper);
        assertTrue(mapper.contains("val chunks = _chunks_part?.values()?.asSequence()?.map { Base64.getDecoder().decode(it) }?.toList()"), mapper);
        assertFalse(mapper.contains("Converter"), mapper);
    }

    // generated and compiled with the symbol processors, so the mappers are valid Kotlin
    private String generatedFormServerMappers() throws Exception {
        process(
            "petstoreV3_form_server",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_form_server.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        try (var files = Files.walk(Path.of("build/out", "petstoreV3_form_server", "kotlin-server"))) {
            return Files.readString(files
                .filter(path -> path.getFileName().toString().equals("DefaultApiServerRequestMappers.kt"))
                .findFirst()
                .orElseThrow());
        }
    }

    private static String nestedClass(String content, String name) {
        var start = content.indexOf("class " + name);
        assertTrue(start > 0, () -> name + " was not generated");
        var end = content.indexOf("class ", start + 1);
        return end < 0 ? content.substring(start) : content.substring(start, end);
    }

    @Test
    void multipartFormMapsEnumPrimitiveAndArrayParams() throws Exception {
        var files = generate(
            "petstoreV3_form_multipart_types",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_form.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerRequestMappers.kt"))
            .findFirst()
            .orElseThrow());

        // a single enum part is read through its converter
        var enumMapper = nestedClass(content, "FormMultipartFormDataWithEnumPatchFormParamRequestMapper");
        assertTrue(enumMapper.contains("typeConverter: HttpServerParameterReader<CurrencyType>"));
        assertTrue(enumMapper.contains("= typeConverter.read(String(_part.content(), StandardCharsets.UTF_8))"));

        // an array of strings collects parts directly and checks presence via isEmpty()
        var stringArray = nestedClass(content, "FormMultipartFormDataWithStringArrayPatchFormParamRequestMapper");
        assertFalse(stringArray.contains("tagsConverter"));
        assertTrue(stringArray.contains("val tags = mutableListOf<String>()"));
        assertTrue(stringArray.contains(".add(String(_part.content(), StandardCharsets.UTF_8))"));
        assertTrue(stringArray.contains(".isEmpty()"));

        // an array of enums collects converted elements
        var enumArray = nestedClass(content, "FormMultipartFormDataWithEnumArrayPatchFormParamRequestMapper");
        assertTrue(enumArray.contains("typesConverter: HttpServerParameterReader<CurrencyType>"));
        assertTrue(enumArray.contains("val types = mutableListOf<CurrencyType>()"));

        // a boolean array must map to Boolean (not Float, which the asKt mapping used to swap)
        var boolArray = nestedClass(content, "FormMultipartFormDataWithBoolArrayPatchFormParamRequestMapper");
        assertTrue(boolArray.contains("flagsConverter: HttpServerParameterReader<Boolean>"));
        assertTrue(boolArray.contains("val flags = mutableListOf<Boolean>()"));
        assertFalse(boolArray.contains("HttpServerParameterReader<Float>"));
    }

    @Test
    void objectQueryParameterFailsWithClearError() {
        var e = assertThrows(Exception.class, () -> generate(
            "petstoreV3_deep_object_query",
            "kotlin-server",
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

        assertTrue(controllerContent.indexOf("tag = ApiSecurity.Sec1_Anonymous::class") < controllerContent.indexOf("optionalAccess("));
        assertTrue(controllerContent.indexOf("tag = ApiSecurity.Sec1::class") < controllerContent.indexOf("requiredAccess("));
        assertTrue(securityContent.contains("class Sec1_Anonymous"));
        assertTrue(securityContent.contains("class Sec1"));
        assertFalse(securityContent.contains("SecurityRequirementTag1"));
        assertTrue(securityContent.contains("return chain.process(request)"));
    }

    @Test
    void openIdConnectSecurityReadsAuthorizationHeaderAndChecksScopes() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_security_openid.yaml").toExternalForm();
        process("petstoreV3_security_openid", "kotlin-server", spec, new SwaggerParams.Options());

        var files = generate("petstoreV3_security_openid", "kotlin-server", spec, new SwaggerParams.Options());
        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("val openIdAuthHeader = request.headers().getFirst("), securityContent);
        assertTrue(securityContent.contains("this.OpenIdAuth_.extract(request, openIdAuthHeader)"), securityContent);
        assertTrue(securityContent.contains(".scopes().contains(\"pets:write\")"), securityContent);
        assertTrue(securityContent.contains("HttpServerResponseException.of(403, \"Forbidden\")"), securityContent);
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
    void securityPrincipalExtractorTagsUseSchemeNames() throws Exception {
        var files = generate(
            "petstoreV3_security_all_named_tags",
            "kotlin-server",
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
        assertFalse(securityContent.contains("SecurityRequirementTag"));
        assertTrue(securityContent.contains("class BearerAuth_ApiKeyAuth_BasicAuth_CookieAuth_OAuth"));
        assertFalse(securityContent.contains("ReadPets"));
        assertFalse(securityContent.contains("WritePets"));
        assertFalse(securityContent.contains("OperationSecuritySchemaTag"));
    }

    @Test
    void headerSecurityCredentialsUseHeaderSuffix() throws Exception {
        var files = generate(
            "petstoreV3_security_api_key_header_variables",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_security_api_key.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("val ApiKeyAuthHeader = request.headers().getFirst(\"X-API-KEY\")"));
        assertTrue(securityContent.contains("val ApiKeyAuth = this.ApiKeyAuth_.extract(request, ApiKeyAuthHeader)"));
    }

    @Test
    void securityCredentialVariablesUseSourceSuffixes() throws Exception {
        var files = generate(
            "petstoreV3_security_multi_source_variables",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_security_multi.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("val headerAuth1Header = request.headers().getFirst(\"X-API-KEY-1\")"));
        assertTrue(securityContent.contains("val queryAuthQuery = request.queryParams().get(\"X-QUERY-KEY\")?.firstOrNull()"));
        assertTrue(securityContent.contains("HeaderAuth1WithQueryAuthAuthData(headerAuth1Header, queryAuthQuery)"));
        assertTrue(securityContent.contains("val oAuthHeader = request.headers().getFirst(\"Authorization\")"));
    }

    @Test
    void cookieSecurityCredentialsUseCookieSuffix() throws Exception {
        var files = generate(
            "petstoreV3_security_cookie_source_variables",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_security_cookie.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("val CookieAuthCookie = request.cookies().firstOrNull"));
        assertTrue(securityContent.contains("val CookieAuth = this.CookieAuth_.extract(request, CookieAuthCookie)"));
    }

    @Test
    void enumMappersAreDefaultComponents() throws Exception {
        var files = generate(
            "petstoreV3_filter_enum_default_components",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_filter.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var modelContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetDog.kt"))
            .findFirst()
            .orElseThrow());
        var moduleContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetDog__NestedEnumMapperModule.kt"))
            .findFirst()
            .orElseThrow());

        assertFalse(modelContent.contains("class JsonWriter"));
        assertTrue(moduleContent.contains("public interface PetDog__NestedEnumMapperModule"));
        assertTrue(moduleContent.contains("@DefaultComponent\n  public fun breedEnumJsonWriter("));
        assertTrue(moduleContent.contains("@DefaultComponent\n  public fun breedEnumJsonReader("));
        assertTrue(moduleContent.contains("@DefaultComponent\n  public fun breedEnumStringParameterReader("));
    }

    @Test
    void modelValidationAnnotationsTargetFields() throws Exception {
        var files = generate(
            "petstoreV3_validation_field_annotations",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_validation.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("@field:Max(value = 99L)"), content);
        assertTrue(content.contains("@field:Min(value = 1L)"), content);
        assertTrue(content.contains("max = Int.MAX_VALUE"), content);
        assertTrue(content.contains("@field:Size("));
        assertTrue(content.contains("@field:Pattern("));
        assertTrue(content.contains("@field:Valid"));
    }

    @Test
    void validationPutsItemConstraintsOnTypeArguments() throws Exception {
        process(
            "petstoreV3_validation_items",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_validation_items.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var model = readGenerated("petstoreV3_validation_items", "Shelf.kt");
        var delegate = readGenerated("petstoreV3_validation_items", "ShelvesApiDelegate.kt");

        assertTrue(model.contains("val tags: List<@Size(max = 5) String>"), model);
        assertTrue(model.contains("val scores: Map<String, @Min(value = 1L) Int>?"), model);
        assertTrue(model.contains("val matrix: List<List<@Size(min = 2, max = 8) String>>?"), model);
        // models are validated by @Valid of the container itself
        assertTrue(model.contains("val books: List<Book>?"), model);
        assertTrue(delegate.contains("labels: List<@Size(max = 4) String>?"), delegate);
    }

    @Test
    void validationValidatesMapsOfModels() throws Exception {
        process(
            "petstoreV3_validation_map",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_validation_map.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var validator = readGenerated("petstoreV3_validation_map", "$Shelf_Validator.kt");

        // ValidationModule provides Validator<Map<K, V>> that validates the values
        assertTrue(validator.contains("Validator<Map<String, Book>>"), validator);
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
        // no form params in the spec, so there is nothing to put into request mappers
        assertTrue(files.stream().noneMatch(file -> file.getName().equals("DefaultApiServerRequestMappers.kt")));
    }

    private static String readGenerated(String name, String fileName) throws Exception {
        try (var files = Files.walk(java.nio.file.Path.of("build/out", name, "kotlin-server"))) {
            return Files.readString(files
                .filter(path -> path.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow());
        }
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
        assertTrue(responseMapperContent.contains("@param:Json"));
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

    @Test
    void discriminatorWithoutMappingUsesOneOfMembersAsSubtypes() throws Exception {
        process(
            "petstoreV3_discriminator_no_mapping",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_discriminator_no_mapping.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var cat = readGenerated("petstoreV3_discriminator_no_mapping", "Cat.kt");
        assertTrue(cat.contains("@JsonDiscriminatorValue(value = [\"Cat\"])"), cat);
        assertTrue(cat.contains(") : Pet"), cat);
        var petReader = readGenerated("petstoreV3_discriminator_no_mapping", "$Pet_JsonReader.kt");
        assertTrue(petReader.contains("\"Cat\"") && petReader.contains("\"Dog\""), petReader);

        // a member missing from an explicit mapping is still mapped by its schema name
        var animalReader = readGenerated("petstoreV3_discriminator_no_mapping", "$Animal_JsonReader.kt");
        assertTrue(animalReader.contains("\"bird\"") && animalReader.contains("\"Fish\""), animalReader);
    }

    @Test
    void oneOfWithoutDiscriminatorIsSealedInterfaceWithWriterOnly() throws Exception {
        process(
            "petstoreV3_oneof_no_discriminator",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_oneof_no_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        // a writer picks the actual subtype, a reader is left to an application: there is nothing to choose a subtype by
        var pet = readGenerated("petstoreV3_oneof_no_discriminator", "Pet.kt").replaceAll("\\s+", " ");
        assertTrue(pet.contains("@JsonWriter") && pet.contains("public sealed interface Pet"), pet);
        assertFalse(pet.contains("@Json "), pet);
        assertFalse(pet.contains("@JsonDiscriminatorField"), pet);
        assertTrue(readGenerated("petstoreV3_oneof_no_discriminator", "Cat.kt").contains(") : Pet"));
        assertTrue(readGenerated("petstoreV3_oneof_no_discriminator", "Dog.kt").contains(") : Pet"));
    }

    @Test
    void oneOfWithoutDiscriminatorWrapsNonObjectMembers() throws Exception {
        process(
            "petstoreV3_oneof_no_discriminator_scalar",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_oneof_no_discriminator_scalar.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var pet = readGenerated("petstoreV3_oneof_no_discriminator_scalar", "Pet.kt").replaceAll("\\s+", " ");
        // an object schema implements the interface, any other member is a subtype with a single value
        assertTrue(pet.contains("public sealed interface Pet"), pet);
        assertTrue(pet.contains("public data class ListStringValue( public val `value`: List<String>, ) : Pet"), pet);
        assertTrue(pet.contains("public data class StringValue( public val `value`: String, ) : Pet"), pet);
        assertTrue(pet.contains("public data class LongValue( public val `value`: Long, ) : Pet"), pet);
        assertTrue(pet.contains("public data class PetStatusValue( public val `value`: PetStatus, ) : Pet"), pet);
        // the generated writer writes a value as is, it is a default component so an application can replace it
        assertTrue(pet.contains("@DefaultComponent @Component public class PetJsonWriter("), pet);
        assertTrue(pet.contains("is Cat -> this.catWriter.write(_gen, _object)"), pet);
        assertTrue(pet.contains("is StringValue -> this.stringValueWriter.write(_gen, _object.value)"), pet);
        assertFalse(pet.contains("@JsonWriter"), pet);
        // the kdoc lists the readers an own reader can be built from
        assertTrue(pet.contains("- `JsonReader<Cat>`"), pet);
        assertTrue(pet.contains("- `JsonReader<String> for the value of Pet.StringValue`"), pet);
        assertTrue(pet.contains("- `JsonReader<List<String>> for the value of Pet.ListStringValue`"), pet);
        assertTrue(pet.contains("- `JsonReader<PetStatus> for the value of Pet.PetStatusValue`"), pet);
        assertTrue(readGenerated("petstoreV3_oneof_no_discriminator_scalar", "Cat.kt").contains(") : Pet"));
    }

    @Test
    void oneOfWithDiscriminatorAndInlineMembersFailsGeneration() {
        var e = assertThrows(RuntimeException.class, () -> generate(
            "inline_oneof_discriminator",
            "kotlin-server",
            getClass().getResource("/example/inline_oneof_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        ));
        var message = rootCause(e).getMessage();
        assertTrue(message.contains("`createCheckRun_request`") && message.contains("$ref"), message);
    }

    @Test
    void omittedOptionalNullableFieldDefaultsToUndefined() throws Exception {
        var files = generate(
            "petstoreV3_nullable_defaults",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_nullable.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());

        // an omitted optional nullable field is absent from the JSON, not an explicit null
        assertTrue(content.contains("fieldNullable: JsonNullable<String> = JsonNullable.undefined()"), content);
        assertFalse(content.contains("JsonNullable.nullValue()"), content);
    }

    @Test
    void enumNamesAndLiteralsCompile() throws Exception {
        process(
            "petstoreV3_enum_names",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_enum_names.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var model = readGenerated("petstoreV3_enum_names", "Constants.kt");
        assertTrue(model.contains("10000000000L"), model);
        assertTrue(model.contains("BigDecimal(\"1.5\")"), model);
        assertTrue(model.contains("\"\\$all\""), model);
    }

    private static Throwable rootCause(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e;
    }

    @Test
    void arrayOfInlineEnumKeepsItsCollectionType() throws Exception {
        var files = generate(
            "petstoreV3_enum_array",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_enum.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("val nonReqArrayString: List<NonReqArrayStringEnum>?"), content);
        assertTrue(content.contains("val reqArrayString: List<ReqArrayStringEnum>"), content);
        assertTrue(content.contains("val nonReqArrayInt: List<NonReqArrayIntEnum>?"), content);
    }

    @Test
    void jsonSuffixMediaTypesUseJsonMappers() throws Exception {
        var files = generate(
            "petstoreV3_json_media_types",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_json_media_types.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        // application/problem+json response
        var responseMappers = readFile(files, "PetsApiServerResponseMappers.kt");
        assertTrue(responseMappers.contains("""
                @param:Json
                public val response404Delegate: HttpServerResponseMapper<HttpResponseEntity<Problem>>,
            """), responseMappers);
        // application/merge-patch+json request body
        var controller = readFile(files, "PetsApiController.kt");
        assertTrue(controller.contains("patchPet(@Path(value = \"petId\") petId: String, @Json pet: Pet)"), controller);
    }

    @Test
    void formParameterDefaultsAreTypedLiterals() throws Exception {
        var files = generate(
            "petstoreV3_defaults",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_defaults.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var controller = readFile(files, "PetsApiController.kt");
        assertTrue(controller.contains("public val ratio: Float? = 1.5f,"), controller);
        assertTrue(controller.contains("public val weight: Double? = 2.0,"), controller);
    }

    @Test
    void base64JsonBodiesBuildIntoAGraph() throws Exception {
        var name = "petstoreV3_byte_json_body_server_graph";
        var files = generate(
            name,
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_byte_json_body.yaml").toExternalForm(),
            new SwaggerParams.Options().setDefaultDelegate(true)
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
        var delegate = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("BytesApiDelegate.kt"))
            .findFirst()
            .orElseThrow());
        assertTrue(delegate.contains("fun postInlineBytes(@Json body: ByteArray)"), delegate);
        assertTrue(delegate.contains("fun postRefBytes(@Json body: ByteArray)"), delegate);

        var app = sources.resolve("TestApp.kt");
        Files.writeString(app, """
            package io.koraframework.openapi.generator.%s.kotlin_server.api

            @io.koraframework.common.annotation.KoraApp
            interface TestApp : io.koraframework.http.server.common.HttpServerModule, io.koraframework.json.common.JsonModule, io.koraframework.validation.module.ValidationModule {
                @io.koraframework.common.annotation.Root
                fun root(handlers: io.koraframework.application.graph.All<io.koraframework.http.server.common.request.HttpServerRequestHandler>) = ""

                @io.koraframework.common.annotation.Tag(String::class)
                fun interceptor() = io.koraframework.http.server.common.interceptor.HttpServerInterceptor { request, chain -> chain.process(request) }
            }
            """.formatted(name));
        kc.withSrc(app);

        assertDoesNotThrow(() -> kc
            .withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpControllerProcessorProvider(), new ValidSymbolProcessorProvider(), new AopSymbolProcessorProvider(), new KoraAppProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile());
    }

    @Test
    void securedOperationsWithNonCamelCaseOrMissingOperationIdAreIntercepted() throws Exception {
        var files = generate(
            "petstoreV3_security_operation_id",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_security_operation_id.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.kt"))
            .findFirst()
            .orElseThrow());

        // list_admin_users, get-admin-opsec, adminCamel and two operations without operationId; ping has `security: []`
        assertEquals(5, content.split("ApiSecurity.BearerAuth::class", -1).length - 1, content);
    }

    @Test
    void securitySchemeNamesAreSanitizedToIdentifiers() throws Exception {
        process(
            "petstoreV3_server_security_scheme_names",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_server_security_scheme_names.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var security = Files.readString(Files.walk(openapiSourcesDir)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.kt"))
            .findFirst()
            .orElseThrow());

        assertTrue(security.contains("val apiKeyHeader = request.headers().getFirst(\"X-API-KEY\")"), security);
        assertTrue(security.contains("val partnerTokenQuery"), security);
        assertTrue(security.contains("val jwtBearerHeader"), security);
        assertTrue(security.contains("val apiKey: String?"), security);
        assertTrue(security.contains("val partnerToken: String?"), security);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "petstoreV3_discriminator_names_mapped|Pet|{\"petType\":\"cat\",\"meow\":\"loud\"}|CatInfo",
        "petstoreV3_discriminator_names_mapped|Pet|{\"petType\":\"dog\",\"bark\":\"loud\"}|DogInfo",
        "petstoreV3_discriminator_reserved|Event|{\"kind\":\"record\",\"value\":\"v\"}|Record",
    })
    void discriminatorSubtypesWithSchemaNamesDifferentFromClassNamesAreReadable(String spec, String parent, String json, String subtype) throws Exception {
        var name = spec + "_runtime_" + subtype;
        var files = generate(name, "kotlin-server", getClass().getResource("/example/" + spec + ".yaml").toExternalForm(), new SwaggerParams.Options());
        var kc = new KotlinCompilation();
        var sources = kc.getBaseDir().resolve("sources");
        for (var src : files) {
            var target = sources.resolve(openapiSourcesDir.relativize(src.toPath()));
            Files.createDirectories(target.getParent());
            Files.copy(src.toPath(), target);
            if (target.toString().endsWith(".kt")) {
                kc.withSrc(target);
            }
        }
        var cl = kc.withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpControllerProcessorProvider(), new ValidSymbolProcessorProvider(), new AopSymbolProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile();
        var pkg = "io.koraframework.openapi.generator." + name + ".kotlin_server.model.";
        var constructor = cl.loadClass(pkg + "$" + parent + "_JsonReader").getConstructors()[0];
        var subtypeReaders = new Object[constructor.getParameterCount()];
        for (int i = 0; i < subtypeReaders.length; i++) {
            var readerType = (ParameterizedType) constructor.getGenericParameterTypes()[i];
            var subtypeClass = (Class<?>) readerType.getActualTypeArguments()[0];
            subtypeReaders[i] = cl.loadClass(pkg + "$" + subtypeClass.getSimpleName() + "_JsonReader").getConstructor().newInstance();
        }
        var reader = (JsonReader<?>) constructor.newInstance(subtypeReaders);

        var value = reader.read(json);

        assertEquals(pkg + subtype, value.getClass().getName());
    }

    @Test
    void uppercaseResponseHeaderNamesAreCamelCase() throws Exception {
        var files = generate(
            "petstoreV3_responses_uppercase_headers",
            "kotlin-server",
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

    @Test
    void urlEncodedObjectIsJsonFieldWhenOptionIsEnabled() throws Exception {
        var files = generate(
            "petstoreV3_form_object_as_json",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_form_object_unsupported.yaml").toExternalForm(),
            new SwaggerParams.Options().setUrlEncodedFormObjectsAsJson(true)
        );

        var mappers = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerRequestMappers.kt"))
            .findFirst()
            .orElseThrow()).replaceAll("\\s+", " ");
        // the option reads an object as a JSON value of a single field, whatever its properties are
        assertTrue(mappers.contains("@param:Json public val profileConverter: HttpServerParameterReader<Profile>"), mappers);
        assertFalse(mappers.contains("profileLoginConverter"), mappers);
    }

    @Test
    void urlEncodedObjectWithNestedObjectFailsWithClearError() {
        var e = assertThrows(Exception.class, () -> generate(
            "petstoreV3_form_object_unsupported",
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_form_object_unsupported.yaml").toExternalForm(),
            new SwaggerParams.Options()
        ));
        var message = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            message.append(t.getMessage()).append('\n');
        }

        assertTrue(message.toString().contains("Unsupported OpenAPI form field `profile` in operation `submitProfile`"), message.toString());
        assertTrue(message.toString().contains("urlEncodedFormObjectsAsJson: true"), message.toString());
    }

    @Test
    void formPartReadersAreTaggedByMediaType() throws Exception {
        var name = "petstoreV3_form_server_parts_graph";
        var files = generate(
            name,
            "kotlin-server",
            getClass().getResource("/example/petstoreV3_form_server.yaml").toExternalForm(),
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerRequestMappers.kt"))
            .findFirst()
            .orElseThrow());
        var flat = mappers.replaceAll("\\s+", " ");
        // a string with a JSON media type is read as JSON, any media type of a model part besides JSON has a tag of its own
        assertTrue(flat.contains("@param:Json public val jsonNoteConverter: HttpServerParameterReader<String>"), mappers);
        assertTrue(flat.contains("@param:Tag(value = ApiFormPartsModule.TextPlain::class) public val plainMetaConverter: HttpServerParameterReader<Info>"), mappers);
        assertTrue(flat.contains("@param:Tag(value = ApiFormPartsModule.ApplicationProblemJson::class) public val problemMetaConverter: HttpServerParameterReader<Info>"), mappers);
        // an element of an array of arrays is a JSON part
        assertTrue(flat.contains("@param:Json public val nestedMetasConverter: HttpServerParameterReader<List<Info>>"), mappers);
        // a url-encoded array is repeated fields, `explode: false` splits one field by the delimiter of the style
        assertTrue(flat.contains("val tags = _tags_part?.values()"), mappers);
        assertTrue(flat.contains("val csv = FormUrlEncodedServerRequestMapper.readDelimited(_bodyString, \"csv\", \",\")?.asSequence()?.map(this.csvConverter::read)?.toList()"), mappers);
        assertTrue(flat.contains("val pipes = FormUrlEncodedServerRequestMapper.readDelimited(_bodyString, \"pipes\", \"|\")"), mappers);
        assertTrue(flat.contains("val spaces = FormUrlEncodedServerRequestMapper.readDelimited(_bodyString, \"spaces\", \" \")"), mappers);
        // a url-encoded object is read from a field per property, an optional one is absent when none of its fields is sent
        assertTrue(flat.contains("public val ownerModeConverter: HttpServerParameterReader<Owner.ModeEnum>"), mappers);
        assertFalse(flat.contains("public val ownerConverter: HttpServerParameterReader<Owner>"), mappers);
        assertTrue(flat.contains("val owner = if (_formData[\"ownerName\"] != null || _formData[\"age\"] != null"), mappers);
        assertTrue(flat.contains("nick = if (_owner_nick == null) JsonNullable.undefined() else JsonNullable.of(_owner_nick)"), mappers);
        assertTrue(flat.contains("throw HttpServerResponseException.of(400, \"Form key 'zip' is required\")"), mappers);
        assertTrue(flat.contains("val address = Address(city = _address_city, zip = _address_zip)"), mappers);
        assertTrue(flat.contains("@param:Json public val jsonOwnerConverter: HttpServerParameterReader<Owner>"), mappers);
        // a JSON-like type has a default reader that delegates to the @Json one, a reader of a non-JSON type is provided by an application
        var formParts = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiFormPartsModule.kt"))
            .findFirst()
            .orElseThrow()).replaceAll("\\s+", " ");
        assertTrue(formParts.contains("@Tag(value = ApiFormPartsModule.ApplicationProblemJson::class) @DefaultComponent public fun infoApplicationProblemJsonFormPartReader(@Json jsonReader: HttpServerParameterReader<Info>): HttpServerParameterReader<Info>"), formParts);
        assertTrue(formParts.contains(".problemMeta (application/problem+json)"), formParts);
        assertTrue(formParts.contains("public class TextPlain"), formParts);
        assertFalse(formParts.contains("TextPlainFormPartReader"), formParts);

        var app = sources.resolve("TestApp.kt");
        Files.writeString(app, """
            package io.koraframework.openapi.generator.%1$s.kotlin_server.api

            @io.koraframework.common.annotation.KoraApp
            interface TestApp : io.koraframework.http.server.common.request.mapper.HttpServerParameterReaderModule, io.koraframework.json.common.JsonModule {
                @io.koraframework.common.annotation.Root
                fun root(mapper: DefaultApiServerRequestMappers.FormMultipartJsonPartPatchFormParamRequestMapper) = ""

                @io.koraframework.common.annotation.Tag(ApiFormPartsModule.TextPlain::class)
                fun plainInfoReader() = io.koraframework.http.server.common.request.HttpServerParameterReader<io.koraframework.openapi.generator.%1$s.kotlin_server.model.Info> { throw IllegalStateException() }
            }
            """.formatted(name));
        kc.withSrc(app);

        assertDoesNotThrow(() -> kc
            .withProcessors(List.of(new JsonSymbolProcessorProvider(), new HttpControllerProcessorProvider(), new ValidSymbolProcessorProvider(), new AopSymbolProcessorProvider(), new KoraAppProcessorProvider()))
            .withGeneratedSourcesDir(kotlinSourcesDir)
            .compile());
    }
}
