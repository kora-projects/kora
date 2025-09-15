package ru.tinkoff.kora.openapi.generator;

import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class BaseOpenapiTest {
    @TempDir
    protected Path openapiSourcesDir;

    public record SwaggerParams(String spec, String name, Options options) {
        public static final class Options {
            public boolean authAsArg;
            public boolean jsonNullable;
            public boolean includeServerRequest;
            public boolean implicitHeaders;
            @Nullable
            public String implicitHeadersRegex;
            public boolean defaultDelegate;

            public Options setAuthAsArg(boolean authAsArg) {
                this.authAsArg = authAsArg;
                return this;
            }

            public Options setJsonNullable(boolean jsonNullable) {
                this.jsonNullable = jsonNullable;
                return this;
            }

            public Options setIncludeServerRequest(boolean includeServerRequest) {
                this.includeServerRequest = includeServerRequest;
                return this;
            }

            public Options setImplicitHeaders(boolean implicitHeaders) {
                this.implicitHeaders = implicitHeaders;
                return this;
            }

            public Options setImplicitHeadersRegex(@Nullable String implicitHeadersRegex) {
                this.implicitHeadersRegex = implicitHeadersRegex;
                return this;
            }

            public Options setDefaultDelegate(boolean defaultDelegate) {
                this.defaultDelegate = defaultDelegate;
                return this;
            }
        }
    }


    public static SwaggerParams[] generateParams() {
        var result = new ArrayList<SwaggerParams>();

        var files = new String[]{
//            "/example/petstoreV3_additional_props.yaml",
//            "/example/petstoreV3_enum.yaml",
//            "/example/petstoreV3_form.yaml",
            "/example/petstoreV3_request_parameters.yaml",
//            "/example/petstoreV3_types.yaml",
//            "/example/petstoreV3_validation.yaml",
//            "/example/petstoreV3_single_response.yaml",
//            "/example/petstoreV3_security_all.yaml",
//            "/example/petstoreV3_security_api_key.yaml",
//            "/example/petstoreV3_security_basic.yaml",
//            "/example/petstoreV3_security_bearer.yaml",
//            "/example/petstoreV3_security_oauth.yaml",
//            "/example/petstoreV3_security_cookie.yaml",
//            "/example/petstoreV3_discriminator.yaml",
//            "/example/petstoreV3_nullable.yaml",
//            "/example/petstoreV3_filter.yaml",
//            "/example/petstoreV3.yaml",
//            "/example/petstoreV2.yaml",
        };

        for (var fileName : files) {
            var name = fileName.substring(fileName.lastIndexOf('/') + 1)
                .replace(".yaml", "")
                .replace(".json", "");

            result.add(new SwaggerParams(fileName, name, new SwaggerParams.Options()));

            if (fileName.contains("security")) {
                result.add(new SwaggerParams(fileName, name + "_auth_arg", new SwaggerParams.Options().setIncludeServerRequest(true)));
            }

            if (name.equals("petstoreV2") || name.equals("petstoreV3")) {
                result.add(new SwaggerParams(fileName, name + "_server_request", new SwaggerParams.Options().setIncludeServerRequest(true)));
                result.add(new SwaggerParams(fileName, name + "_implicit_headers", new SwaggerParams.Options().setImplicitHeaders(true)));
                result.add(new SwaggerParams(fileName, name + "_implicit_headers_regex", new SwaggerParams.Options().setImplicitHeadersRegex("first.*")));
            }

            if (name.equals("petstoreV3")) {
                result.add(new SwaggerParams(fileName, name + "_default_delegate", new SwaggerParams.Options().setDefaultDelegate(true)));
            }

            if (fileName.contains("discriminator")
                || fileName.contains("validation")
                || fileName.contains("nullable")
                || fileName.contains("additional_props")) {
                result.add(new SwaggerParams(fileName, name + "_enable_json_nullable", new SwaggerParams.Options().setJsonNullable(true)));
            }
        }

        return result.toArray(SwaggerParams[]::new);
    }


    protected final List<File> generate(String name, String mode, String spec, SwaggerParams.Options options) throws Exception {
        var dir = openapiSourcesDir.toAbsolutePath().toString();
        var packageName = "ru.tinkoff.kora.openapi.generator." + name.replace('-', '_') + "." + mode.replace('-', '_');

        var configurator = new CodegenConfigurator()
            .setGeneratorName("kora")
            .setInputSpec(spec) // or from the server
            .setOutputDir(dir)
            .setApiPackage(packageName + ".api")
            .setModelPackage(packageName + ".model")
            .setGlobalProperties(Map.of(
                "skipFormModel", "false"
            ))
            .addAdditionalProperty("mode", mode)
            .addAdditionalProperty("additionalModelTypeAnnotations", "@ru.tinkoff.kora.json.common.annotation.JsonInclude(ru.tinkoff.kora.json.common.annotation.JsonInclude.IncludeType.ALWAYS)")
            .addAdditionalProperty("interceptors", """
                {
                  "*": [
                    {
                      "tag": "java.lang.String"
                    }
                  ]
                }
                """)
            .addAdditionalProperty("tags", """
                {
                    "*": {
                      "httpClientTag": "java.lang.String",
                      "telemetryTag": "java.lang.String"
                    }
                  }
                """)
            .addAdditionalProperty("enableServerValidation", name.contains("validation"))
            .addAdditionalProperty("authAsMethodArgument", options.authAsArg)
            .addAdditionalProperty("enableJsonNullable", options.jsonNullable)
            .addAdditionalProperty("implicitHeaders", options.implicitHeaders)
            .addAdditionalProperty("requestInDelegateParams", options.includeServerRequest)
            .addAdditionalProperty("requestInDelegateParams", options.includeServerRequest)
            .addAdditionalProperty("clientConfigPrefix", "test");

        if (options.defaultDelegate) {
            configurator.addAdditionalProperty("delegateMethodBodyMode", "throwException");
        }

        if (options.implicitHeadersRegex != null) {
            configurator.addAdditionalProperty("implicitHeadersRegex", options.implicitHeadersRegex);
        }

        if (spec.contains("_filter")) {
            configurator.addOpenapiNormalizer("FILTER", "operationId:updatePets|getDeliveries|getSystems");
            configurator.addAdditionalProperty("filterWithModels", "true");
        }

        var clientOptInput = configurator.toClientOptInput();
        var generator = new DefaultGenerator();
        return generator.opts(clientOptInput).generate();
    }
}
