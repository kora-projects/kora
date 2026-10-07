package io.koraframework.openapi.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class HttpServerJavaOpenapiTest extends BaseJavaOpenapiTest {

    @Test
    void specTextWithFormatPlaceholdersReachesTheDocsLiterally() throws Exception {
        var files = generate(
            "petstoreV3_format_symbols_docs",
            "java-server",
            getClass().getResource("/example/petstoreV3_format_symbols.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var delegate = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiDelegate.java"))
            .findFirst()
            .orElseThrow());
        assertTrue(delegate.contains("Pet by id, 100% match"), delegate);
        assertTrue(delegate.contains("Plus in the query is %2B, placeholders %L %S %N %T %1L $L $S $N $T $$ %% stay literal"), delegate);
        assertTrue(delegate.contains("Id of the pet, `+` goes as %2B"), delegate);
        assertTrue(delegate.contains("Pet found, 100% %L $L (status code 200)"), delegate);

        var model = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.java"))
            .findFirst()
            .orElseThrow());
        assertTrue(model.contains("Name, `+` as %2B, %S $S"), model);
    }

    @Test
    void multipartFileFormParamDoesNotAskForAConverterItNeverUses() throws Exception {
        var files = generate(
            "petstoreV3_form_multipart",
            "java-server",
            getClass().getResource("/example/petstoreV3_form.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerRequestMappers.java"))
            .findFirst()
            .orElseThrow());

        var singleFile = nestedClass(content, "FormMultipartFormDataWithObjectPatchFormParamRequestMapper");
        assertTrue(singleFile.contains("file = _part"));
        assertFalse(singleFile.contains("HttpServerParameterReader"));

        var fileArray = nestedClass(content, "FormMultipartFormDataWithArrayPatchFormParamRequestMapper");
        assertTrue(fileArray.contains("filename.add(_part)"));
        assertFalse(fileArray.contains("HttpServerParameterReader"));

        // a url-encoded form still converts every non-string parameter
        var urlEncoded = nestedClass(content, "FormUrlencodedObjectPatchFormParamRequestMapper");
        assertTrue(urlEncoded.contains("HttpServerParameterReader<Boolean> providedConverter"));
    }

    @Test
    void multipartFormMapsEnumModelPrimitiveAndArrayParams() throws Exception {
        var files = generate(
            "petstoreV3_form_multipart_types",
            "java-server",
            getClass().getResource("/example/petstoreV3_form.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerRequestMappers.java"))
            .findFirst()
            .orElseThrow());

        // a single enum part is read through its converter, not passed through as a raw String
        var enumMapper = nestedClass(content, "FormMultipartFormDataWithEnumPatchFormParamRequestMapper");
        assertTrue(enumMapper.contains("HttpServerParameterReader<CurrencyType> typeConverter"));
        assertTrue(enumMapper.contains("type = typeConverter.read(new String(_part.content(), StandardCharsets.UTF_8))"));

        // a single model part is read through its converter as well
        var modelMapper = nestedClass(content, "FormMultipartFormDataWithModelPatchFormParamRequestMapper");
        assertTrue(modelMapper.contains("HttpServerParameterReader<Info> infoConverter"));
        assertTrue(modelMapper.contains("info = infoConverter.read(new String(_part.content(), StandardCharsets.UTF_8))"));

        // single primitive parts declare a boxed nullable local so it can be null before the part is read
        var primitiveMapper = nestedClass(content, "FormMultipartFormDataWithPrimitivesPatchFormParamRequestMapper");
        assertTrue(primitiveMapper.contains("var active = (Boolean) null"));
        assertTrue(primitiveMapper.contains("var count = (Integer) null"));
        assertTrue(primitiveMapper.contains("active = activeConverter.read(new String(_part.content(), StandardCharsets.UTF_8))"));

        // an array of strings collects every part directly, no converter and required checked via isEmpty()
        var stringArray = nestedClass(content, "FormMultipartFormDataWithStringArrayPatchFormParamRequestMapper");
        assertFalse(stringArray.contains("tagsConverter"));
        assertTrue(stringArray.contains("var tags = new ArrayList<String>()"));
        assertTrue(stringArray.contains("tags.add(new String(_part.content(), StandardCharsets.UTF_8))"));
        assertTrue(stringArray.contains("if (tags.isEmpty())"));

        // an array of primitives collects converted elements
        var intArray = nestedClass(content, "FormMultipartFormDataWithIntArrayPatchFormParamRequestMapper");
        assertTrue(intArray.contains("HttpServerParameterReader<Integer> countsConverter"));
        assertTrue(intArray.contains("var counts = new ArrayList<Integer>()"));
        assertTrue(intArray.contains("counts.add(countsConverter.read(new String(_part.content(), StandardCharsets.UTF_8)))"));
        assertTrue(intArray.contains("if (counts.isEmpty())"));

        // an array of enums collects converted elements
        var enumArray = nestedClass(content, "FormMultipartFormDataWithEnumArrayPatchFormParamRequestMapper");
        assertTrue(enumArray.contains("HttpServerParameterReader<CurrencyType> typesConverter"));
        assertTrue(enumArray.contains("var types = new ArrayList<CurrencyType>()"));
        assertTrue(enumArray.contains("types.add(typesConverter.read(new String(_part.content(), StandardCharsets.UTF_8)))"));

        // an absent optional array yields null, not an empty list
        var optionalArray = nestedClass(content, "FormMultipartFormDataWithOptionalArrayPatchFormParamRequestMapper");
        assertTrue(optionalArray.contains("labels.isEmpty() ? null : labels"));
    }

    @Test
    void urlEncodedFormMapsAbsentOptionalFieldsToNull() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedOptionalPatchFormParamRequestMapper");

        // an absent optional scalar never reaches its converter
        assertTrue(mapper.contains("var count = _count_str == null ? null : countConverter.read(_count_str)"), mapper);
        // an absent optional array is null instead of dereferencing the missing part
        assertTrue(mapper.contains("var tags = _tags_part == null ? null : _tags_part.values()"), mapper);
        assertTrue(mapper.contains("var ids = _ids_part == null ? null : _ids_part.values().stream().map(this.idsConverter::read).toList()"), mapper);
        // a required field is still checked
        assertTrue(mapper.contains("if (name == null)"), mapper);
    }

    @Test
    void multipartModelPartIsReadWithJsonReader() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormMultipartJsonPartPatchFormParamRequestMapper");

        // a model part defaults to application/json, an explicit JSON encoding is honoured too
        assertTrue(mapper.contains("@Json HttpServerParameterReader<Info> metaConverter"), mapper);
        assertTrue(mapper.contains("@Json HttpServerParameterReader<Info> encodedMetaConverter"), mapper);
        // a part with an explicit non-JSON encoding and an enum part keep the plain reader
        assertTrue(mapper.contains("HttpServerParameterReader<Info> plainMetaConverter"), mapper);
        assertFalse(mapper.contains("@Json HttpServerParameterReader<Info> plainMetaConverter"), mapper);
        assertTrue(mapper.contains("HttpServerParameterReader<CurrencyType> typeConverter"), mapper);
        assertFalse(mapper.contains("@Json HttpServerParameterReader<CurrencyType>"), mapper);
    }

    @Test
    void formDeclaringBothContentTypesIsReadByRequestContentType() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedAndMultipartPatchFormParamRequestMapper");

        assertTrue(mapper.contains("var _contentType = rq.headers().getFirst(\"content-type\")"), mapper);
        assertTrue(mapper.contains("if (_contentType != null && _contentType.toLowerCase(Locale.ROOT).startsWith(\"multipart/form-data\"))"), mapper);
        assertTrue(mapper.contains("MultipartReaderUtils.read(rq)"), mapper);
        assertTrue(mapper.contains("FormUrlEncodedServerRequestMapper.read(_bodyString)"), mapper);
        assertTrue(mapper.indexOf("MultipartReaderUtils.read(rq)") < mapper.indexOf("FormUrlEncodedServerRequestMapper.read(_bodyString)"), mapper);
    }

    @Test
    void multipartModelArrayPartIsReadWithJsonReader() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormMultipartModelArrayPatchFormParamRequestMapper");

        // each element is a JSON model, so the element reader resolves with JsonModule
        assertTrue(mapper.contains("@Json HttpServerParameterReader<Info> metasConverter"), mapper);
    }

    @Test
    void urlEncodedBinaryFieldOfDualFormIsDataPart() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedAndMultipartPatchFormParamRequestMapper");

        // the form record holds a FormPart for a binary field, so the url-encoded value is wrapped into one
        assertTrue(mapper.contains("var file = _file_str == null ? null : FormMultipart.data(\"file\", _file_str)"), mapper);
        assertTrue(mapper.contains("var files = _files_part == null ? null : _files_part.values().stream().map(_v -> FormMultipart.data(\"files\", _v)).toList()"), mapper);
        assertFalse(mapper.contains("fileConverter"), mapper);
        assertFalse(mapper.contains("filesConverter"), mapper);
    }

    @Test
    void urlEncodedBinaryFieldIsDataPart() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedBinaryPatchFormParamRequestMapper");

        assertTrue(mapper.contains("var doc = FormMultipart.data(\"doc\", _doc_str)"), mapper);
        assertFalse(mapper.contains("Converter"), mapper);
    }

    @Test
    void urlEncodedByteFieldsAreBase64Decoded() throws Exception {
        var content = generatedFormServerMappers();
        var mapper = nestedClass(content, "FormUrlencodedBytePatchFormParamRequestMapper");

        assertTrue(mapper.contains("var req = Base64.getDecoder().decode(_req_str)"), mapper);
        assertTrue(mapper.contains("var opt = _opt_str == null ? null : Base64.getDecoder().decode(_opt_str)"), mapper);
        assertTrue(mapper.contains("var chunks = _chunks_part == null ? null : _chunks_part.values().stream().map(_v -> Base64.getDecoder().decode(_v)).toList()"), mapper);
        assertFalse(mapper.contains("Converter"), mapper);
    }

    // generated and compiled with the annotation processors, so the mappers are valid Java
    private String generatedFormServerMappers() throws Exception {
        process(
            "petstoreV3_form_server",
            "java-server",
            getClass().getResource("/example/petstoreV3_form_server.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        return readGenerated("petstoreV3_form_server", "DefaultApiServerRequestMappers.java");
    }

    private static String nestedClass(String content, String name) {
        var start = content.indexOf("class " + name);
        assertTrue(start > 0, () -> name + " was not generated");
        var end = content.indexOf("class ", start + 1);
        return end < 0 ? content.substring(start) : content.substring(start, end);
    }


    @Test
    void numericRangeUsesTheSchemaMaximumAsUpperBound() throws Exception {
        var files = generate(
            "petstoreV3_validation_range",
            "java-server",
            getClass().getResource("/example/petstoreV3_validation.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var pets = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetsApiDelegate.java"))
            .findFirst()
            .orElseThrow());
        // the schema declares minimum: 1 and maximum: 100
        assertTrue(pets.contains("@Range(from = 1.0, to = 100.0"), pets);

        var pet = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PetApiDelegate.java"))
            .findFirst()
            .orElseThrow());
        // a single inclusive integral bound can use the more concise annotation
        assertTrue(pet.contains("@Min(1L)"), pet);
    }

    @Test
    void validationKeepsEveryConstraintOfAProperty() throws Exception {
        process(
            "petstoreV3_validation_combined",
            "java-server",
            getClass().getResource("/example/petstoreV3_validation_combined.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var model = readGenerated("petstoreV3_validation_combined", "Order.java");
        var delegate = readGenerated("petstoreV3_validation_combined", "OrdersApiDelegate.java");

        // a BigDecimal with a single bound used to fail the generation
        assertTrue(model.contains("@PositiveOrZero BigDecimal amount"), model);
        assertTrue(model.contains("@Range(from = 0.5, to = Double.MAX_VALUE, boundary = Range.Boundary.INCLUSIVE_INCLUSIVE)"), model);
        assertTrue(delegate.contains("@PositiveOrZero"), delegate);
        // a missing lower bound of a double is the most negative double, not the smallest positive one
        assertTrue(model.contains("@Range(from = -Double.MAX_VALUE, to = 10.0"), model);
        assertTrue(model.contains("@Range(from = 0.5, to = 10.5"), model);
        assertFalse(model.contains("Double.MIN_VALUE"), model);
        // a pattern is kept next to a length constraint, and array items are validated
        assertTrue(model.contains("@Size(min = 1, max = 64) @Pattern(\".*\\\\S.*\") String code"), model);
        assertTrue(model.contains("@Size(min = 1, max = Integer.MAX_VALUE) @Valid List<Line> lines"), model);
        assertTrue(delegate.contains("@Size(max = 16) @Pattern(\"^[A-Z]+$\")"), delegate);
    }

    @Test
    void securitySchemeNamesAreSanitizedToIdentifiers() throws Exception {
        process(
            "petstoreV3_server_security_scheme_names",
            "java-server",
            getClass().getResource("/example/petstoreV3_server_security_scheme_names.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );
        var security = readGenerated("petstoreV3_server_security_scheme_names", "ApiSecurity.java");

        assertTrue(security.contains("var apiKeyHeader = request.headers().getFirst(\"X-API-KEY\")"), security);
        assertTrue(security.contains("var partnerTokenQuery"), security);
        assertTrue(security.contains("var jwtBearerHeader"), security);
        assertTrue(security.contains("String apiKey"), security);
        assertTrue(security.contains("String partnerToken"), security);
    }

    private static String readGenerated(String name, String fileName) throws Exception {
        try (var files = Files.walk(Path.of("build/out", name, "java-server"))) {
            return Files.readString(files
                .filter(path -> path.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow());
        }
    }

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

    @Test
    void anonymousSecurityDoesNotRequireServerInterceptor() throws Exception {
        var files = generate(
            "petstoreV3_security_anonymous",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_anonymous.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("PublicApiController.java"))
            .findFirst()
            .orElseThrow());
        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.indexOf("tag = ApiSecurity.Sec1_Anonymous.class") < controllerContent.indexOf("optionalAccess("));
        assertTrue(controllerContent.indexOf("tag = ApiSecurity.Sec1.class") < controllerContent.indexOf("requiredAccess("));
        assertTrue(securityContent.contains("final class Sec1_Anonymous"));
        assertTrue(securityContent.contains("final class Sec1"));
        assertFalse(securityContent.contains("SecurityRequirementTag1"));
        assertTrue(securityContent.contains("return chain.process(request);"));
    }

    @Test
    void openIdConnectSecurityReadsAuthorizationHeaderAndChecksScopes() throws Exception {
        var spec = getClass().getResource("/example/petstoreV3_security_openid.yaml").toExternalForm();
        process("petstoreV3_security_openid", "java-server", spec, new SwaggerParams.Options());

        var files = generate("petstoreV3_security_openid", "java-server", spec, new SwaggerParams.Options());
        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("var openIdAuthHeader = request.headers().getFirst("), securityContent);
        assertTrue(securityContent.contains("this.OpenIdAuth_.extract(request, openIdAuthHeader)"), securityContent);
        assertTrue(securityContent.contains(".scopes().contains(\"pets:write\")"), securityContent);
        assertTrue(securityContent.contains("HttpServerResponseException.of(403, \"Forbidden\")"), securityContent);
    }

    @Test
    void serverAuthFallbackUsesUnauthorized() throws Exception {
        var files = generate(
            "petstoreV3_security_api_key",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_api_key.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("throw HttpServerResponseException.of(401, \"Unauthorized\")"));
        assertFalse(securityContent.contains("Forbidden"));
    }

    @Test
    void securityPrincipalExtractorTagsUseSchemeNames() throws Exception {
        var files = generate(
            "petstoreV3_security_all_named_tags",
            "java-server",
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
        assertFalse(securityContent.contains("SecurityRequirementTag"));
        assertTrue(securityContent.contains("final class BearerAuth_ApiKeyAuth_BasicAuth_CookieAuth_OAuth"));
        assertFalse(securityContent.contains("ReadPets"));
        assertFalse(securityContent.contains("WritePets"));
        assertFalse(securityContent.contains("OperationSecuritySchemaTag"));
    }

    @Test
    void securityCredentialAndPrincipalVariablesDoNotCollide() throws Exception {
        var files = generate(
            "petstoreV3_security_api_key_distinct_variables",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_api_key.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("HttpServerPrincipalExtractor<String, Principal> ApiKeyAuth_"));
        assertTrue(securityContent.contains("var ApiKeyAuthHeader = request.headers().getFirst(\"X-API-KEY\")"));
        assertTrue(securityContent.contains("var ApiKeyAuth = this.ApiKeyAuth_.extract(request, ApiKeyAuthHeader)"));
        assertFalse(securityContent.contains("var ApiKeyAuth = this.ApiKeyAuth.extract(request, ApiKeyAuth)"));
    }

    @Test
    void securityCredentialVariablesUseSourceSuffixes() throws Exception {
        var files = generate(
            "petstoreV3_security_multi_source_variables",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_multi.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("var headerAuth1Header = request.headers().getFirst(\"X-API-KEY-1\")"));
        assertTrue(securityContent.contains("var queryAuthQueryList = request.queryParams().get(\"X-QUERY-KEY\")"));
        assertTrue(securityContent.contains("var queryAuthQuery = queryAuthQueryList == null"));
        assertTrue(securityContent.contains("new HeaderAuth1WithQueryAuthAuthData(headerAuth1Header, queryAuthQuery)"));
        assertTrue(securityContent.contains("var oAuthHeader = request.headers().getFirst(\"authorization\")"));
    }

    @Test
    void cookieSecurityCredentialsUseCookieSuffix() throws Exception {
        var files = generate(
            "petstoreV3_security_cookie_source_variables",
            "java-server",
            getClass().getResource("/example/petstoreV3_security_cookie.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var securityContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("ApiSecurity.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(securityContent.contains("var CookieAuthCookie = request.cookies().stream()"));
        assertTrue(securityContent.contains("var CookieAuth = this.CookieAuth_.extract(request, CookieAuthCookie)"));
    }

    @Test
    void serverResponseMapperWithoutDelegatesDoesNotGenerateEmptyConstructor() throws Exception {
        var files = generate(
            "petstoreV3_discriminator",
            "java-server",
            getClass().getResource("/example/petstoreV3_discriminator.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var responseMapperContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(responseMapperContent.contains("class PetsPatchApiResponseMapper"));
        assertFalse(responseMapperContent.contains("public PetsPatchApiResponseMapper()"));
        assertTrue(responseMapperContent.contains("var headers = HttpHeaders.empty()"));
        assertFalse(responseMapperContent.contains("var headers = HttpHeaders.of()"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsHttpBodyTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_body",
            "java-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("BODY")
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.java"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_body"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiDelegate.java"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("StoreInventoryApiResponse storeInventory(HttpHeaders _headers,"));
        assertTrue(controllerContent.contains("HttpBodyInput body)"));
        assertTrue(delegateContent.contains("StoreInventoryApiResponse storeInventory(HttpHeaders _headers,"));
        assertTrue(delegateContent.contains("HttpBodyInput body)"));
        assertTrue(controllerContent.contains("RawObjectApiResponse rawObject(HttpHeaders _headers,"));
        assertTrue(delegateContent.contains("RawObjectApiResponse rawObject(HttpHeaders _headers,"));
        assertEquals(3, countJavadocReturnTags(controllerContent));
        assertEquals(3, countJavadocReturnTags(delegateContent));
        assertTrue(containsMultilineStoreInventoryReturn(controllerContent));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse("));
        assertTrue(responsesContent.contains("HttpBodyOutput content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory400ApiResponse(ErrorMessage content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse("));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse("));
        assertTrue(responsesContent.contains("HttpBodyOutput content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse("));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse("));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<HttpBodyOutput>> response200Delegate"));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<ErrorMessage>> response400Delegate"));
        assertTrue(responseMapperContent.contains("@DefaultComponent"));
        assertTrue(responseMapperContent.contains("class StoreInventoryApiResponseMapper"));
        assertFalse(responseMapperContent.contains("public static final class StoreInventoryApiResponseMapper"));
    }

    @Test
    void bareObjectRequestAndResponseAreGeneratedAsObjectTypes() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_object",
            "java-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options().setRawBodyMode("OBJECT")
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.java"))
            .findFirst()
            .orElseThrow());
        var delegateContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_object"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiDelegate.java"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("StoreInventoryApiResponse storeInventory(@Json Object body)"));
        assertTrue(delegateContent.contains("StoreInventoryApiResponse storeInventory(@Json Object body)"));
        assertTrue(controllerContent.contains("RawObjectApiResponse rawObject(@Json Object body)"));
        assertTrue(delegateContent.contains("RawObjectApiResponse rawObject(@Json Object body)"));
        assertFalse(controllerContent.contains("HttpHeaders _headers"));
        assertFalse(delegateContent.contains("HttpHeaders _headers"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse(Object content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse(Object content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse(Object content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse(Object content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse(Object content) implements RawObjectApiResponse"));
        assertTrue(responseMapperContent.contains("@Json HttpServerResponseMapper<HttpResponseEntity<Object>> response200Delegate"));
    }

    @Test
    void bareObjectRequestAndResponseUseByteArrayByDefault() throws Exception {
        var files = generate(
            "petstoreV3_bare_object_bytes_default",
            "java-server",
            getClass().getResource("/example/petstoreV3_bare_object.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var controllerContent = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.toString().contains("petstoreV3_bare_object_bytes_default"))
            .filter(path -> path.getFileName().toString().equals("DefaultApiController.java"))
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
            .filter(path -> path.getFileName().toString().equals("DefaultApiServerResponseMappers.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(controllerContent.contains("StoreInventoryApiResponse storeInventory(HttpHeaders _headers,"));
        assertTrue(controllerContent.contains("byte[] body)"));
        assertTrue(controllerContent.contains("RawObjectApiResponse rawObject(HttpHeaders _headers, byte[] body)"));
        assertTrue(responsesContent.contains("record StoreInventory200ApiResponse(byte[] content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record StoreInventory500ApiResponse(byte[] content) implements StoreInventoryApiResponse"));
        assertTrue(responsesContent.contains("record RawObject200ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject400ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responsesContent.contains("record RawObject500ApiResponse(byte[] content) implements RawObjectApiResponse"));
        assertTrue(responseMapperContent.contains("HttpServerResponseMapper<HttpResponseEntity<byte[]>> response200Delegate"));
    }

    @Test
    void arrayOfInlineEnumKeepsItsCollectionType() throws Exception {
        var files = generate(
            "petstoreV3_enum_array",
            "java-server",
            getClass().getResource("/example/petstoreV3_enum.yaml").toExternalForm(),
            new SwaggerParams.Options()
        );

        var content = Files.readString(files.stream()
            .map(java.io.File::toPath)
            .filter(path -> path.getFileName().toString().equals("Pet.java"))
            .findFirst()
            .orElseThrow());

        assertTrue(content.contains("List<Pet.NonReqArrayStringEnum> nonReqArrayString"), content);
        assertTrue(content.contains("List<Pet.ReqArrayStringEnum> reqArrayString"), content);
        assertTrue(content.contains("List<Pet.NonReqArrayIntEnum> nonReqArrayInt"), content);
    }
}
