package io.koraframework.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.util.*;

import static io.koraframework.openapi.generator.SecurityData.hasAnonymousRequirement;

public class ClientSecuritySchemaGenerator extends AbstractJavaGenerator<Map<String, Object>> {
    private static final Logger GENERATOR_LOG = LoggerFactory.getLogger(ClientSecuritySchemaGenerator.class);

    @Override
    public JavaFile generate(Map<String, Object> ctx) {
        var className = ClassName.get(apiPackage, "ApiSecurity");
        var b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Classes.module)
            .addField(FieldSpec.builder(ClassName.get(Logger.class), "log")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.getLogger($T.class)", LoggerFactory.class, className)
                .build());
        @SuppressWarnings("unchecked")
        var authMethods = (List<CodegenSecurity>) ctx.get("authMethods");
        var tags = new HashSet<String>();
        tags.addAll(security.interceptorTagBySecurityRequirement.values());
        for (var authMethod : authMethods) {
            tags.add(this.security.tagForSecurityScheme(authMethod.name));
        }
        for (var tag : tags) {
            b.addType(buildTag(tag));
        }

        var securityConfig = securityConfig(authMethods);
        if (securityConfig != null) {
            b.addType(securityConfig);
            b.addMethod(securityConfigComponent(authMethods));
        }

        for (var authMethod : authMethods) {
            switch (authMethod.type) {
                case "http" -> {
                    switch (authMethod.scheme) {
                        case "basic" -> b.addMethod(basicAuthHttpClientTokenProvider(authMethod));
                        case "bearer" -> {}
                    }
                }
                case "apiKey" -> {
                    b.addMethod(buildApiKeyTokenProvider(ctx, authMethod));
                }
                case "oauth2" -> {}
                default -> {
                    throw new IllegalStateException(unsupportedSecurityTypeError(authMethod));
                }
            }
        }

        for (var entry : security.interceptorTagBySecurityRequirement.entrySet()) {
            var requirement = entry.getKey();
            var tag = entry.getValue();
            warnAboutCombinedHeaderSecurity(tag, requirement, authMethods);
            b.addType(buildAuthGroupInterceptor(tag, new ArrayList<>(requirement), authMethods));
            b.addMethod(buildAuthGroupInterceptorComponent(tag, new ArrayList<>(requirement), authMethods));
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private MethodSpec securityConfigComponent(List<CodegenSecurity> authMethods) {
        var configClassName = ClassName.get(apiPackage, "ApiSecurity", "SecurityConfig");
        var b = MethodSpec.methodBuilder("securityConfig")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(Classes.defaultComponent)
            .addParameter(Classes.config, "config")
            .addParameter(ParameterizedTypeName.get(Classes.configValueExtractor, ClassName.get(String.class)), "mapper")
            .returns(configClassName);
        var params = new ArrayList<CodeBlock>();
        var securityConfigPathPrefix = securityConfigPathPrefix();
        for (var authMethod : authMethods) {
            var configPath = securityConfigPathPrefix + "." + authMethod.name;
            if (authMethod.type.equals("http") && authMethod.scheme.equals("basic")) {
                var authMethodConfig = securityAuthMethodConfigClassName(authMethod);
                var username = authMethod.name + "_username";
                var password = authMethod.name + "_password";
                b.addStatement("var $N = mapper.map(config.get($S))", username, configPath + ".username");
                b.addStatement("var $N = mapper.map(config.get($S))", password, configPath + ".password");
                b.addStatement("var $N = $N == null && $N == null ? null : new $T($N, $N)", authMethod.name, username, password, authMethodConfig, username, password);
                params.add(CodeBlock.of("$N", authMethod.name));
            }
            if (authMethod.type.equals("apiKey")) {
                b.addStatement("var $N = mapper.map(config.get($S))", authMethod.name, configPath);
                params.add(CodeBlock.of("$N", authMethod.name));
            }
        }
        b.addStatement("return new $T($L)", configClassName, CodeBlock.join(params, ", "));
        return b.build();
    }

    private TypeSpec securityConfig(List<CodegenSecurity> authMethods) {
        var builder = TypeSpec.recordBuilder("SecurityConfig")
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        var b = MethodSpec.constructorBuilder();
        var parameterCount = 0;
        for (var authMethod : authMethods) {
            if (authMethod.type.equals("http") && authMethod.scheme.equals("basic")) {
                b.addParameter(ParameterSpec.builder(securityAuthMethodConfigClassName(authMethod).annotated(AnnotationSpec.builder(Classes.nullable).build()), authMethod.name).build());
                builder.addType(basicAuthConfig(authMethod));
                parameterCount++;
            }
            if (authMethod.type.equals("apiKey")) {
                b.addParameter(ParameterSpec.builder(ClassName.get(String.class).annotated(AnnotationSpec.builder(Classes.nullable).build()), authMethod.name).build());
                parameterCount++;
            }
        }
        return parameterCount == 0 ? null : builder.recordConstructor(b.build()).build();
    }

    private MethodSpec buildAuthGroupInterceptorComponent(String interceptorTag, List<Map<String, Set<String>>> security, List<CodegenSecurity> authMethods) {
        var interceptorClass = ClassName.get(apiPackage, "ApiSecurity", interceptorTag + "HttpClientInterceptor");
        var b = MethodSpec.methodBuilder(interceptorTag + "HttpClientInterceptor")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(securityTagAnnotation(interceptorTag))
            .addAnnotation(Classes.defaultComponent)
            .returns(interceptorClass)
            .addCode("return new $T(", interceptorClass);
        var seen = new HashSet<String>();
        for (var securityRequirement : security) {
            for (var securitySchema : securityRequirement.keySet()) {
                if (!seen.add(securitySchema)) {
                    continue;
                }
                var param = ParameterSpec.builder(Classes.httpClientTokenProvider, securitySchema)
                    .addAnnotation(securityTagAnnotation(this.security.tagForSecurityScheme(securitySchema)))
                    .build();
                b.addParameter(param);
                if (seen.size() > 1) {
                    b.addCode(", ");
                }
                b.addCode("$N", param.name());
            }
        }
        b.addCode(");\n");

        return b.build();
    }

    private TypeSpec buildAuthGroupInterceptor(String interceptorTag, List<Map<String, Set<String>>> security, List<CodegenSecurity> authMethods) {
        var b = TypeSpec.classBuilder(interceptorTag + "HttpClientInterceptor")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addAnnotation(generated())
            .addSuperinterface(Classes.httpClientInterceptor);

        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var seen = new HashSet<String>();
        for (var securityRequirement : security) {
            for (var securitySchema : securityRequirement.keySet()) {
                if (!seen.add(securitySchema)) {
                    continue;
                }
                var param = ParameterSpec.builder(Classes.httpClientTokenProvider, securitySchema)
                    .addAnnotation(securityTagAnnotation(this.security.tagForSecurityScheme(securitySchema)))
                    .build();
                constructor.addParameter(param);
                constructor.addStatement("this.$N = $N", param.name(), param.name());
                b.addField(param.type(), param.name(), Modifier.PRIVATE, Modifier.FINAL);
            }
        }
        b.addMethod(constructor.build());

        var intercept = MethodSpec.methodBuilder("processRequest")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Classes.httpClientResponse)
            .addParameter(Classes.httpClientInterceptChain, "chain")
            .addParameter(Classes.httpClientRequest, "request")
            .addException(Exception.class);

        var securitySchemaSeen = new HashSet<String>();
        var allowAnonymous = hasAnonymousRequirement(security);
        for (var securityRequirement : security) {
            if (securityRequirement.isEmpty()) {
                continue;
            }
            for (var entry : securityRequirement.entrySet()) {
                var securitySchemaName = entry.getKey();
                var scopes = entry.getValue();
                if (securitySchemaSeen.add(securitySchemaName)) {
                    intercept.addStatement("var $N = this.$N.getToken(request)", securitySchemaName, securitySchemaName);
                }
            }
            var ifProvided = securityRequirement.keySet().stream().map(name -> CodeBlock.of("$N != null", name)).collect(CodeBlock.joining(" && ", "if (", ")"));
            intercept.beginControlFlow(ifProvided);
            intercept.addStatement("var b = request.toBuilder()");
            var cookieHeaderName = uniqueLocalName(security, "_securityCookieHeader");
            var hasCookieSecurity = securityRequirement.keySet().stream()
                .map(name -> authMethods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow())
                .anyMatch(method -> method.isApiKey && method.isKeyInCookie);
            if (hasCookieSecurity) {
                intercept.addStatement("var $N = request.headers().getFirst($S)", cookieHeaderName, "Cookie");
            }
            for (var securitySchemaName : securityRequirement.keySet()) {
                var securitySchema = authMethods.stream().filter(s -> s.name.equals(securitySchemaName)).findFirst().get();
                switch (securitySchema.type) {
                    case "http", "oauth2", "openId" -> intercept.addStatement("b.header($S, $N)", "Authorization", securitySchemaName);
                    case "apiKey" -> {
                        if (securitySchema.isKeyInQuery) {
                            intercept.addStatement("b.queryParam($S, $N)", securitySchema.keyParamName, securitySchemaName);
                        } else if (securitySchema.isKeyInHeader) {
                            intercept.addStatement("b.header($S, $N)", securitySchema.keyParamName, securitySchemaName);
                        } else if (securitySchema.isKeyInCookie) {
                            intercept.addStatement("$N = $N == null || $N.isBlank() ? $S + $N : $N + $S + $S + $N", cookieHeaderName, cookieHeaderName, cookieHeaderName, securitySchema.keyParamName + "=", securitySchemaName, cookieHeaderName, "; ", securitySchema.keyParamName + "=", securitySchemaName);
                        } else {
                            throw new IllegalArgumentException(invalidApiKeyLocationError(securitySchema));
                        }
                    }
                    default -> throw new IllegalStateException(unsupportedSecurityTypeError(securitySchema));
                }
            }
            if (hasCookieSecurity) {
                intercept.addStatement("b.header($S, $N)", "Cookie", cookieHeaderName);
            }
            intercept.addStatement("return chain.process(b.build())");
            intercept.endControlFlow();
        }
        if (!allowAnonymous) {
            intercept.addStatement("log.warn($S)", "Security schema is defined for api but no data was provided");
        }
        intercept.addStatement("return chain.process(request)");
        b.addMethod(intercept.build());
        return b.build();
    }

    private static void warnAboutCombinedHeaderSecurity(String tag, Set<Map<String, Set<String>>> security, List<CodegenSecurity> authMethods) {
        for (var requirement : security) {
            var methods = requirement.keySet().stream()
                .map(name -> authMethods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow())
                .toList();
            var authorizationSchemes = methods.stream().filter(method -> method.type.equals("http") || method.type.equals("oauth2") || method.type.equals("openId")).map(method -> method.name).toList();
            if (authorizationSchemes.size() > 1) {
                GENERATOR_LOG.warn("Security requirement '{}' uses multiple Authorization schemes {}; generated client applies them in declaration order and the last value wins", tag, authorizationSchemes);
            }
            var cookieSchemes = methods.stream().filter(method -> method.isApiKey && method.isKeyInCookie).map(method -> method.name).toList();
            if (cookieSchemes.size() > 1) {
                GENERATOR_LOG.warn("Security requirement '{}' uses multiple cookie schemes {}; generated client combines them into one Cookie header", tag, cookieSchemes);
            }
        }
    }

    private static String uniqueLocalName(List<Map<String, Set<String>>> security, String baseName) {
        var names = security.stream().flatMap(requirement -> requirement.keySet().stream()).collect(java.util.stream.Collectors.toSet());
        var result = baseName;
        while (names.contains(result)) {
            result = "_" + result;
        }
        return result;
    }

    private static String unsupportedSecurityTypeError(CodegenSecurity securitySchema) {
        return """
            Unsupported OpenAPI security scheme `%s`.

            Scheme type: `%s`
            Scheme name: `%s`

            Supported client security types: `http` basic/bearer, `apiKey`, and `oauth2`.
            Fix: use a supported OpenAPI security scheme type or provide custom client authentication outside generated security.
            """.formatted(securitySchema.name, securitySchema.type, securitySchema.name);
    }

    private static String invalidApiKeyLocationError(CodegenSecurity securitySchema) {
        return """
            Invalid OpenAPI apiKey security scheme `%s`.

            Kora supports apiKey client auth in query parameters, headers, and cookies.
            Unsupported location: `%s`

            Fix: set `in: query`, `in: header`, or `in: cookie` for this security scheme.
            """.formatted(securitySchema.name, securitySchema.scheme);
    }


    private MethodSpec basicAuthHttpClientTokenProvider(CodegenSecurity authMethod) {
        var configClassName = ClassName.get(apiPackage, "ApiSecurity", "SecurityConfig");
        return MethodSpec.methodBuilder(authMethod.name + "BasicAuthHttpClientTokenProvider")
            .addAnnotation(securityTagAnnotation(this.security.tagForSecurityScheme(authMethod.name)))
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(configClassName, "config")
            .returns(Classes.basicAuthHttpClientTokenProvider)
            .addStatement("return config.$N() == null ? new $T(null, null) : new $T(config.$N().username(), config.$N().password())", authMethod.name, Classes.basicAuthHttpClientTokenProvider, Classes.basicAuthHttpClientTokenProvider, authMethod.name, authMethod.name)
            .build();
    }

    private TypeSpec basicAuthConfig(CodegenSecurity authMethod) {
        return TypeSpec.recordBuilder(securityAuthMethodConfigName(authMethod))
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .recordConstructor(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(String.class).annotated(AnnotationSpec.builder(Classes.nullable).build()), "username")
                .addParameter(ClassName.get(String.class).annotated(AnnotationSpec.builder(Classes.nullable).build()), "password")
                .build())
            .build();
    }

    private MethodSpec buildApiKeyTokenProvider(Map<String, Object> ctx, CodegenSecurity authMethod) {
        var configClassName = ClassName.get(apiPackage, "ApiSecurity", "SecurityConfig");

        return MethodSpec.methodBuilder(authMethod.name + "TokenProvider")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(Classes.defaultComponent)
            .addAnnotation(securityTagAnnotation(this.security.tagForSecurityScheme(authMethod.name)))
            .addParameter(configClassName, "config")
            .addStatement("return _ -> config.$N()", authMethod.name)
            .returns(Classes.httpClientTokenProvider)
            .build();
    }

    private ClassName securityAuthMethodConfigClassName(CodegenSecurity authMethod) {
        return ClassName.get(apiPackage, "ApiSecurity", "SecurityConfig", securityAuthMethodConfigName(authMethod));
    }

    private String securityAuthMethodConfigName(CodegenSecurity authMethod) {
        return "Security" + this.security.tagForSecurityScheme(authMethod.name) + "Config";
    }

    private String securityConfigPathPrefix() {
        if (params.securityConfigPrefix != null && !params.securityConfigPrefix.isBlank()) {
            return params.securityConfigPrefix;
        }
        if (params.clientConfigPrefix != null && !params.clientConfigPrefix.isBlank()) {
            return params.clientConfigPrefix + ".security";
        }
        if (params.clientConfig != null && !params.clientConfig.isBlank()) {
            return params.clientConfig + ".security";
        }
        return "security";
    }

    private TypeSpec buildTag(String tag) {
        return TypeSpec.classBuilder(tag)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .build();
    }
}
