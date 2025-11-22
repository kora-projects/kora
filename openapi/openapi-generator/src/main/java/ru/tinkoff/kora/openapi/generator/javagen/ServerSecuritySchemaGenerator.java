package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenSecurity;

import javax.lang.model.element.Modifier;
import java.util.*;

public class ServerSecuritySchemaGenerator extends AbstractJavaGenerator<Map<String, Object>> {

    @Override
    public JavaFile generate(Map<String, Object> ctx) {
        var b = TypeSpec.interfaceBuilder("ApiSecurity")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated())
            .addAnnotation(Classes.module);
        var tags = new HashSet<String>();
        @SuppressWarnings("unchecked")
        var authMethods = (List<CodegenSecurity>) ctx.get("authMethods");

        tags.addAll(security.interceptorTagBySecurityRequirement.values());
        tags.addAll(security.principalExtractorTagBySecurityRequirementNames.values());

        for (var tag : tags) {
            b.addType(buildTag(tag));
        }
        for (var entry : security.principalExtractorTagBySecurityRequirementNames.entrySet()) {
            var securityRequirementNames = entry.getKey();
            var tag = entry.getValue();
            if (securityRequirementNames.size() > 1) {
                b.addType(buildAuthData(tag, securityRequirementNames, authMethods));
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

    private TypeSpec buildAuthData(String tag, Set<String> securityRequirementNames, List<CodegenSecurity> authMethods) {
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        for (var securityRequirementName : securityRequirementNames) {
            var authMethod = authMethods.stream().filter(m -> m.name.equals(securityRequirementName)).findFirst().get();
            var javadoc = authMethodParameterJavadoc(authMethod);
            constructor.addParameter(ParameterSpec.builder(String.class, securityRequirementName)
                .addJavadoc(javadoc)
                .build()
            );
        }
        return TypeSpec.recordBuilder(tag + "AuthData")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .recordConstructor(constructor.build())
            .build();
    }

    private static String authMethodParameterJavadoc(CodegenSecurity authMethod) {
        if (authMethod.isOAuth || authMethod.isOpenId || authMethod.isBasic || authMethod.isBasicBasic || authMethod.isBasicBearer) {
            return "'Authorization' header of request\n";
        }
        if (authMethod.isApiKey) {
            if (authMethod.isKeyInHeader) {
                return "'" + authMethod.keyParamName + "' header of request\n";
            } else if (authMethod.isKeyInQuery) {
                return "'" + authMethod.keyParamName + "' query parameter value\n";
            } else if (authMethod.isKeyInCookie) {
                return "'" + authMethod.keyParamName + "' cookie value\n";
            } else {
                throw new IllegalArgumentException();
            }

        }
        return "\n";
    }

    private MethodSpec buildAuthGroupInterceptorComponent(String interceptorTag, List<Map<String, Set<String>>> security, List<CodegenSecurity> authMethods) {
        var interceptorClass = ClassName.get(apiPackage, "ApiSecurity", interceptorTag + "HttpServerInterceptor");
        var b = MethodSpec.methodBuilder(interceptorTag + "HttpServerInterceptor")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(securityTagAnnotation(interceptorTag))
            .addAnnotation(Classes.defaultComponent)
            .returns(interceptorClass)
            .addCode("return new $T(", interceptorClass);
        var seen = new HashSet<String>();
        for (var securityRequirement : security) {
            var principalExtractorTag = this.security.principalExtractorTagBySecurityRequirementNames.get(securityRequirement.keySet());
            if (!seen.add(principalExtractorTag)) {
                continue;
            }
            var param = securityRequirementPrincipalExtractorParameter(authMethods, securityRequirement, principalExtractorTag);
            b.addParameter(param);
            if (seen.size() > 1) {
                b.addCode(", ");
            }
            b.addCode("$N", param.name());
        }
        b.addCode(");\n");

        return b.build();
    }

    private ParameterSpec securityRequirementPrincipalExtractorParameter(List<CodegenSecurity> authMethods, Map<String, Set<String>> securityRequirement, String principalExtractorTag) {
        var needScopes = authMethods.stream()
            .filter(auth -> securityRequirement.containsKey(auth.name))
            .anyMatch(auth -> auth.isOAuth || auth.isOpenId);
        var extractorType = ParameterizedTypeName.get(
            Classes.httpServerPrincipalExtractor,
            securityRequirement.size() == 1 ? ClassName.get(String.class) : ClassName.get(apiPackage, "ApiSecurity", principalExtractorTag + "AuthData"),
            needScopes ? Classes.principalWithScopes : Classes.principal
        );
        return ParameterSpec.builder(extractorType, principalExtractorTag)
            .addAnnotation(securityTagAnnotation(principalExtractorTag))
            .build();
    }

    private TypeSpec buildAuthGroupInterceptor(String interceptorTag, List<Map<String, Set<String>>> security, List<CodegenSecurity> authMethods) {
        var b = TypeSpec.classBuilder(interceptorTag + "HttpServerInterceptor")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addAnnotation(generated())
            .addSuperinterface(Classes.httpServerInterceptor);
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var seen = new HashSet<String>();
        for (var securityRequirement : security) {
            var principalExtractorTag = this.security.principalExtractorTagBySecurityRequirementNames.get(securityRequirement.keySet());
            if (!seen.add(principalExtractorTag)) {
                continue;
            }
            var param = securityRequirementPrincipalExtractorParameter(authMethods, securityRequirement, principalExtractorTag);
            constructor.addParameter(param);
            constructor.addStatement("this.$N = $N", param.name(), param.name());
            b.addField(param.type(), param.name());
        }
        b.addMethod(constructor.build());
        var intercept = MethodSpec.methodBuilder("intercept")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Classes.httpServerResponse)
            .addParameter(Classes.httpServerRequest, "request")
            .addParameter(Classes.httpServerInterceptChain, "chain")
            .addException(Exception.class);

        var securitySchemaSeen = new HashSet<String>();
        var securityRequirementSeen = new HashSet<String>();
        for (var securityRequirement : security) {
            for (var entry : securityRequirement.entrySet()) {
                var securitySchemaName = entry.getKey();
                var scopes = entry.getValue();
                if (securitySchemaSeen.add(securitySchemaName)) {
                    var securitySchema = authMethods.stream().filter(s -> s.name.equals(securitySchemaName)).findFirst().get();
                    if (securitySchema.isApiKey) {
                        if (securitySchema.isKeyInHeader) {
                            intercept.addStatement("var $N = request.headers().getFirst($S)", securitySchemaName, securitySchema.keyParamName);
                        } else if (securitySchema.isKeyInQuery) {
                            intercept.addStatement("var $N = request.queryParams().get($S)", securitySchemaName + "_list", securitySchema.keyParamName);
                            intercept.addStatement("var $N = $N == null || $N.isEmpty() ? null : $N.iterator().next()", securitySchemaName, securitySchemaName + "_list", securitySchemaName + "_list", securitySchemaName + "_list");
                        } else if (securitySchema.isKeyInCookie) {
                            intercept.addStatement("var $N = request.cookies().stream().filter(c -> $S.equals(c.name())).map(c -> c.value()).findFirst().orElse(null)", securitySchemaName, securitySchema.keyParamName);
                        } else {
                            throw new IllegalArgumentException();
                        }
                    } else if (securitySchema.isBasicBasic || securitySchema.isBasicBearer || securitySchema.isOAuth) {
                        intercept.addStatement("var $N = request.headers().getFirst($S)", securitySchemaName, "Authorization");
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
            }
            var extractorTag = this.security.principalExtractorTagBySecurityRequirementNames.get(securityRequirement.keySet());
            if (securityRequirementSeen.add(extractorTag)) {
                if (securityRequirement.size() == 1) {
                    intercept.addStatement("var $N = this.$N.extract(request, $N)", extractorTag, extractorTag, securityRequirement.keySet().stream().findFirst().get());
                } else {
                    var authData = ClassName.get(this.apiPackage, "ApiSecurity", extractorTag + "AuthData");

                    var params = securityRequirement.keySet()
                        .stream()
                        .map(n -> CodeBlock.of("$N", n))
                        .collect(CodeBlock.joining(", ", "(", ")"));
                    intercept.addStatement("var $N = this.$N.extract(\n  request,\n  new $T$L)", extractorTag, extractorTag, authData, params);
                }
            }
            intercept.beginControlFlow("if ($N != null)", extractorTag);
            var scopesCount = 0;
            for (var entry : securityRequirement.entrySet()) {
                var securitySchemaName = entry.getKey();
                var scopes = entry.getValue();
                var securitySchema = authMethods.stream().filter(s -> s.name.equals(securitySchemaName)).findFirst().get();
                if (Boolean.TRUE.equals(securitySchema.isOAuth) && !scopes.isEmpty()) {
                    for (var scope : scopes) {
                        intercept.beginControlFlow("if ($N.scopes().contains($S))", extractorTag, scope);
                        scopesCount++;
                    }
                }
            }
            intercept.addStatement("return $T.with($N, () -> chain.process(request))", Classes.principal, extractorTag);
            for (var i = 0; i < scopesCount; i++) {
                intercept.endControlFlow();
            }

            intercept.endControlFlow();

            intercept.addCode("\n");
        }
        intercept.addStatement("throw $T.of(403, $S)", Classes.httpServerResponseException, "Forbidden");
        b.addMethod(intercept.build());
        return b.build();
    }

    private TypeSpec buildTag(String name) {
        return TypeSpec.classBuilder(name)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .build();
    }
}
