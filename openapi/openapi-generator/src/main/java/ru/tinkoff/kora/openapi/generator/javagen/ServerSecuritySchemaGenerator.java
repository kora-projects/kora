package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.*;
import org.openapitools.codegen.CodegenSecurity;

import javax.lang.model.element.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
        for (var authMethod : authMethods) {
            tags.add(camelize(toVarName(authMethod.name)));
        }
        var authGroups = collectServerAuthMethodGroups();
        for (var authGroup : authGroups) {
            tags.add(authGroup.name());
        }
        tags.addAll(collectServerAuthTags());
        for (var tag : tags) {
            b.addType(buildTag(tag));
        }
        for (var authGroup : authGroups) {
            b.addType(buildAuthGroupInterceptor(authGroup));
            b.addMethod(buildAuthGroupInterceptorComponent(authGroup));
        }

        return JavaFile.builder(apiPackage, b.build()).build();
    }

    private MethodSpec buildAuthGroupInterceptorComponent(ServerAuthMethodGroup authGroup) {
        var interceptorClass = ClassName.get(apiPackage, "ApiSecurity", authGroup.name() + "HttpServerInterceptor");
        var b = MethodSpec.methodBuilder(authGroup.name() + "HttpServerInterceptor")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(tagAnnotation(authGroup.name()))
            .addAnnotation(Classes.defaultComponent)
            .returns(interceptorClass)
            .addCode("return new $T(", interceptorClass);
        for (int i = 0; i < authGroup.methods().size(); i++) {
            var method = authGroup.methods().get(i);
            var extractorType = ParameterizedTypeName.get(Classes.httpServerPrincipalExtractor, method.isOAuth ? Classes.principalWithScopes : Classes.principal);
            b.addParameter(ParameterSpec.builder(extractorType, method.name)
                .addAnnotation(tagAnnotation(camelize(toVarName(method.name))))
                .build());
            if (i > 0) {
                b.addCode(", ");
            }
            b.addCode("$N", method.name);
        }
        b.addCode(");\n");

        return b.build();
    }

    private TypeSpec buildAuthGroupInterceptor(ServerAuthMethodGroup authGroup) {
        var b = TypeSpec.classBuilder(authGroup.name() + "HttpServerInterceptor")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addAnnotation(generated())
            .addSuperinterface(Classes.httpServerInterceptor);
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        for (var method : authGroup.methods()) {
            var extractorType = ParameterizedTypeName.get(Classes.httpServerPrincipalExtractor, method.isOAuth ? Classes.principalWithScopes : Classes.principal);
            b.addField(extractorType, method.name);
            constructor.addParameter(ParameterSpec.builder(extractorType, method.name)
                .build());
            constructor.addStatement("this.$N = $N", method.name, method.name);
        }
        b.addMethod(constructor.build());
        var intercept = MethodSpec.methodBuilder("intercept")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Classes.httpServerResponse)
            .addParameter(Classes.context, "ctx")
            .addParameter(Classes.httpServerRequest, "request")
            .addParameter(Classes.httpServerInterceptChain, "chain")
            .addException(Exception.class);
        for (var method : authGroup.methods()) {
            if (method.isApiKey) {
                if (method.isKeyInHeader) {
                    intercept.addStatement("var $N = request.headers().getFirst($S)", method.name + "_token", method.keyParamName);
                } else if (method.isKeyInQuery) {
                    intercept.addStatement("var $N = request.queryParams().get($S)", method.name + "_cookie", method.keyParamName);
                    intercept.addStatement("var $N = $N == null || $N.isEmpty() ? null : $N.iterator().next()", method.name + "_token", method.name + "_query", method.name + "_query", method.name + "_query");
                } else if (method.isKeyInCookie) {
                    intercept.addStatement("var $N = request.cookies().stream().filter(c -> $S.equals(c.name())).map(c -> c.value()).findFirst().orElse(null)", method.name + "_token", method.keyParamName);
                } else {
                    throw new IllegalArgumentException();
                }
            } else if (method.isBasicBasic || method.isBasicBearer || method.isOAuth) {
                intercept.addStatement("var $N = request.headers().getFirst($S)", method.name + "_token", "Authorization");
            } else {
                throw new IllegalArgumentException();
            }
            intercept.addStatement("var $N = this.$N.extract(request, $N)", method.name, method.name, method.name + "_token");
            intercept.beginControlFlow("if ($N != null)", method.name);
            if (Boolean.TRUE.equals(method.isOAuth) && method.scopes != null && !method.scopes.isEmpty()) {
                for (var scope : method.scopes) {
                    intercept.beginControlFlow("if (!$N.scopes().contains($S))", method.name, scope.get("scope"));
                    intercept.addStatement("throw $T.of(403, $S)", Classes.httpServerResponseException, "Forbidden");
                    intercept.endControlFlow();
                }
            }
            intercept.addStatement("$T.set(ctx, $N)", Classes.principal, method.name);
            intercept.addStatement("return chain.process(ctx, request)");
            intercept.endControlFlow();
        }
        intercept.addStatement("throw $T.of(403, $S)", Classes.httpServerResponseException, "Forbidden");
        b.addMethod(intercept.build());
        return b.build();
    }

    private AnnotationSpec tagAnnotation(String securityTagName) {
        return AnnotationSpec.builder(Classes.tag).addMember("value", "$T.class", ClassName.get(apiPackage, "ApiSecurity", securityTagName)).build();
    }

    private TypeSpec buildTag(String name) {
        return TypeSpec.classBuilder(name)
            .addAnnotation(generated())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .build();
    }
}
