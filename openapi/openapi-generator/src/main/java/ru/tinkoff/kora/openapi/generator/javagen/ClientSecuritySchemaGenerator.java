package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenSecurity;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Map;

public class ClientSecuritySchemaGenerator extends AbstractJavaGenerator<Map<String, Object>> {
    @Override
    public JavaFile generate(Map<String, Object> ctx) {
        var className = ClassName.get(apiPackage, "ApiSecurity");
        var b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Classes.module);
        if (params.authAsMethodArgument) {
            return JavaFile.builder(apiPackage, b.build()).build();
        }
        @SuppressWarnings("unchecked")
        var authMethods = (List<CodegenSecurity>) ctx.get("authMethods");
        for (var authMethod : authMethods) {
            b.addType(buildTag(authMethod));
            switch (authMethod.type) {
                case "http" -> {
                    switch (authMethod.scheme) {
                        case "basic" -> {
                            b.addType(basicAuthConfig(authMethod));
                            b.addMethod(basicAuthHttpClientTokenProvider(authMethod));
                            b.addMethod(basicAuthHttpClientAuthInterceptor(authMethod));
                        }
                        case "bearer" -> b.addMethod(bearerAuthHttpClientInterceptor(authMethod));
                    }
                }
                case "apiKey" -> {
                    b.addMethod(buildApiKeyConfig(ctx, authMethod));
                    b.addMethod(buildApiKeyInterceptor(ctx, authMethod));
                }
                case "oauth2" -> b.addMethod(bearerAuthHttpClientInterceptor(authMethod));
                default -> {
                    throw new IllegalStateException("Unexpected value: " + authMethod.type);
                }
            }
        }


        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private MethodSpec bearerAuthHttpClientInterceptor(CodegenSecurity authMethod) {
        return MethodSpec.methodBuilder(authMethod.name + "HttpClientAuthInterceptor")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(tagAnnotation(authMethod))
            .addParameter(ParameterSpec.builder(Classes.httpClientTokenProvider, "tokenProvider").addAnnotation(tagAnnotation(authMethod)).build())
            .returns(Classes.bearerAuthHttpClientInterceptor)
            .addStatement("return new $T(tokenProvider)", Classes.bearerAuthHttpClientInterceptor)
            .build();
    }

    private MethodSpec basicAuthHttpClientAuthInterceptor(CodegenSecurity authMethod) {
        return MethodSpec.methodBuilder(authMethod.name + "HttpClientAuthInterceptor")
            .addAnnotation(tagAnnotation(authMethod))
            .addAnnotation(Classes.defaultComponent)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(ParameterSpec.builder(Classes.httpClientTokenProvider, "tokenProvider").addAnnotation(tagAnnotation(authMethod)).build())
            .returns(Classes.httpClientInterceptor)
            .addStatement("return new $T(tokenProvider)", Classes.basicAuthHttpClientInterceptor)
            .build();
    }

    private MethodSpec basicAuthHttpClientTokenProvider(CodegenSecurity authMethod) {
        return MethodSpec.methodBuilder(authMethod.name + "BasicAuthHttpClientTokenProvider")
            .addAnnotation(tagAnnotation(authMethod))
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(ClassName.get(apiPackage, "ApiSecurity", StringUtils.capitalize(authMethod.name) + "Config"), "config")
            .returns(Classes.basicAuthHttpClientTokenProvider)
            .addStatement("return new $T(config.username(), config.password())", Classes.basicAuthHttpClientTokenProvider)
            .build();
    }

    private TypeSpec basicAuthConfig(CodegenSecurity authMethod) {
        return TypeSpec.recordBuilder(StringUtils.capitalize(authMethod.name) + "Config")
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .recordConstructor(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "username")
                .addParameter(String.class, "password")
                .build())
            .addAnnotation(AnnotationSpec.builder(Classes.configSource).addMember("value", "$S", params.securityConfigPrefix != null
                    ? params.securityConfigPrefix + "." + authMethod.name
                    : authMethod.name)
                .build())
            .build();
    }

    private MethodSpec buildApiKeyInterceptor(Map<String, Object> ctx, CodegenSecurity authMethod) {
        var apiKeyLocationClass = Classes.apiKeyHttpClientInterceptor.nestedClass("ApiKeyLocation");
        var apiKeyLocation = (String) null;
        if (authMethod.isKeyInQuery) {
            apiKeyLocation = "QUERY";
        } else if (authMethod.isKeyInHeader) {
            apiKeyLocation = "HEADER";
        } else if (authMethod.isKeyInCookie) {
            apiKeyLocation = "COOKIE";
        } else {
            throw new IllegalArgumentException("Invalid api key location for auth: " + authMethod.scheme);
        }

        return MethodSpec.methodBuilder(authMethod.name + "HttpClientAuthInterceptor")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(Classes.defaultComponent)
            .addAnnotation(tagAnnotation(authMethod))
            .addParameter(ParameterSpec.builder(String.class, "apiKey").addAnnotation(tagAnnotation(authMethod)).build())
            .addStatement("var paramLocation = $T.$N", apiKeyLocationClass, apiKeyLocation)
            .addStatement("return new $T(paramLocation, $S, apiKey)", Classes.apiKeyHttpClientInterceptor, authMethod.keyParamName)
            .returns(Classes.apiKeyHttpClientInterceptor)
            .build();
    }

    private MethodSpec buildApiKeyConfig(Map<String, Object> ctx, CodegenSecurity authMethod) {
        return MethodSpec.methodBuilder(authMethod.name + "Config")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(Classes.defaultComponent)
            .addAnnotation(tagAnnotation(authMethod))
            .addParameter(Classes.config, "config")
            .addParameter(ParameterizedTypeName.get(Classes.configValueExtractor, ClassName.get(String.class)), "extractor")
            .addStatement("var configPath = $S", params.securityConfigPrefix == null
                ? authMethod.name
                : params.securityConfigPrefix + "." + authMethod.name)
            .addStatement("var configValue = config.get(configPath)")
            .addStatement("var parsed = extractor.extract(configValue)")
            .beginControlFlow("if (parsed == null)")
            .addStatement("throw $T.missingValueAfterParse(configValue)", Classes.configValueExtractionException)
            .endControlFlow()
            .addStatement("return parsed")
            .returns(String.class)
            .build();
    }

    private AnnotationSpec tagAnnotation(CodegenSecurity authMethod) {
        return AnnotationSpec.builder(Classes.tag).addMember("value", "$T.class", ClassName.get(apiPackage, "ApiSecurity", camelize(toVarName(authMethod.name)))).build();
    }

    private TypeSpec buildTag(CodegenSecurity authMethod) {
        return TypeSpec.classBuilder(camelize(toVarName(authMethod.name)))
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .build();
    }
}
