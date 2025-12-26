package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.util.*;

public class ClientSecuritySchemaGenerator extends AbstractJavaGenerator<Map<String, Object>> {
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
            tags.add(authMethod.name);
        }
        for (var tag : tags) {
            b.addType(buildTag(tag));
        }

        b.addType(securityConfig(authMethods));
        b.addMethod(securityConfigComponent(authMethods));

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
                    throw new IllegalStateException("Unexpected value: " + authMethod.type);
                }
            }
        }

        for (var entry : security.interceptorTagBySecurityRequirement.entrySet()) {
            var requirement = entry.getKey();
            var tag = entry.getValue();
            b.addType(buildAuthGroupInterceptor(tag, new ArrayList<>(requirement), authMethods));
            b.addMethod(buildAuthGroupInterceptorComponent(tag, new ArrayList<>(requirement), authMethods));
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private MethodSpec securityConfigComponent(List<CodegenSecurity> authMethods) {
        var configClassName = ClassName.get(apiPackage, "ApiSecurity", "Config");
        var b = MethodSpec.methodBuilder("config")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(Classes.config, "config")
            .addParameter(ParameterizedTypeName.get(Classes.configValueExtractor, ClassName.get(String.class)), "extractor")
            .returns(configClassName);
        var params = new ArrayList<CodeBlock>();
        for (var authMethod : authMethods) {
            var configPath = this.params.clientConfigPrefix == null
                ? authMethod.name
                : this.params.clientConfigPrefix + "." + authMethod.name;
            if (authMethod.type.equals("http") && authMethod.scheme.equals("basic")) {
                var authMethodConfig = ClassName.get(apiPackage, "ApiSecurity", "Config", authMethod.name + "Config");
                var username = authMethod.name + "_username";
                var password = authMethod.name + "_password";
                b.addStatement("var $N = extractor.extract(config.get($S))", username, configPath + ".username");
                b.addStatement("var $N = extractor.extract(config.get($S))", password, configPath + ".password");
                b.addStatement("var $N = new $T($N, $N)", authMethod.name, authMethodConfig, username, password);
                params.add(CodeBlock.of("$N", authMethod.name));
            }
            if (authMethod.type.equals("apiKey")) {
                b.addStatement("var $N = extractor.extract(config.get($S))", authMethod.name, configPath);
                params.add(CodeBlock.of("$N", authMethod.name));
            }
        }
        b.addStatement("return new $T($L)", configClassName, CodeBlock.join(params, ", "));
        return b.build();
    }

    private TypeSpec securityConfig(List<CodegenSecurity> authMethods) {
        var builder = TypeSpec.recordBuilder("Config")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        var b = MethodSpec.constructorBuilder();
        for (var authMethod : authMethods) {
            if (authMethod.type.equals("http") && authMethod.scheme.equals("basic")) {
                b.addParameter(ParameterSpec.builder(ClassName.get(apiPackage, "ApiSecurity", "Config", authMethod.name + "Config"), authMethod.name).build());
                builder.addType(basicAuthConfig(authMethod));
            }
            if (authMethod.type.equals("apiKey")) {
                b.addParameter(ParameterSpec.builder(ClassName.get(String.class).annotated(AnnotationSpec.builder(Classes.nullable).build()), authMethod.name).build());
            }
        }
        return builder.recordConstructor(b.build()).build();
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
                    .addAnnotation(securityTagAnnotation(securitySchema))
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
                    .addAnnotation(securityTagAnnotation(securitySchema))
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
        for (var securityRequirement : security) {
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
            var needReturn = true;
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
                            needReturn = false;
                            intercept.addStatement("throw new IllegalArgumentException(\"Cookies are not supported yet\")");
                        } else {
                            throw new IllegalArgumentException("Invalid api key location for auth: " + securitySchema.scheme);
                        }
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + securitySchema.type);
                }
            }
            if (needReturn) {
                intercept.addStatement("return chain.process(b.build())");
            }
            intercept.endControlFlow();
        }
        intercept.addStatement("log.warn($S)", "Security schema is defined for api but no data was provided");
        intercept.addStatement("return chain.process(request)");
        b.addMethod(intercept.build());
        return b.build();
    }


    private MethodSpec basicAuthHttpClientTokenProvider(CodegenSecurity authMethod) {
        var configClassName = ClassName.get(apiPackage, "ApiSecurity", "Config");
        return MethodSpec.methodBuilder(authMethod.name + "BasicAuthHttpClientTokenProvider")
            .addAnnotation(securityTagAnnotation(authMethod.name))
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(configClassName, "config")
            .returns(Classes.basicAuthHttpClientTokenProvider)
            .addStatement("return new $T(config.$N().username(), config.$N().password())", Classes.basicAuthHttpClientTokenProvider, authMethod.name, authMethod.name)
            .build();
    }

    private TypeSpec basicAuthConfig(CodegenSecurity authMethod) {
        return TypeSpec.recordBuilder(authMethod.name + "Config")
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

    private MethodSpec buildApiKeyTokenProvider(Map<String, Object> ctx, CodegenSecurity authMethod) {
        var configClassName = ClassName.get(apiPackage, "ApiSecurity", "Config");

        return MethodSpec.methodBuilder(authMethod.name + "TokenProvider")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(Classes.defaultComponent)
            .addAnnotation(securityTagAnnotation(authMethod.name))
            .addParameter(configClassName, "config")
            .addStatement("return _ -> config.$N()", authMethod.name)
            .returns(Classes.httpClientTokenProvider)
            .build();
    }

    private TypeSpec buildTag(String tag) {
        return TypeSpec.classBuilder(tag)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .build();
    }
}
