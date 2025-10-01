package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenSecurity


class ClientSecuritySchemaGenerator : AbstractKotlinGenerator<Map<String, Any>>() {
    override fun generate(ctx: Map<String, Any>): FileSpec {
        val className = ClassName(apiPackage, "ApiSecurity")
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())
        if (params.authAsMethodArgument) {
            return FileSpec.get(apiPackage, b.build());
        }

        val authMethods = ctx["authMethods"] as List<CodegenSecurity>
        for (authMethod in authMethods) {
            b.addType(buildTag(authMethod))
            when (authMethod.type) {
                "http" -> when (authMethod.scheme) {
                    "basic" -> {
                        b.addType(basicAuthConfig(authMethod))
                        b.addFunction(basicAuthHttpClientTokenProvider(authMethod))
                        b.addFunction(basicAuthHttpClientAuthInterceptor(authMethod))
                    }

                    "bearer" -> {
                        b.addFunction(bearerAuthHttpClientInterceptor(authMethod))
                    }
                }

                "apiKey" -> {
                    b.addFunction(buildApiKeyConfig(ctx, authMethod))
                    b.addFunction(buildApiKeyInterceptor(ctx, authMethod))
                }

                "oauth2" -> {
                    b.addFunction(bearerAuthHttpClientInterceptor(authMethod))
                }

                else -> throw IllegalArgumentException("unknown scheme type: ${authMethod.type}")
            }
        }
        return FileSpec.get(apiPackage, b.build());
    }

    private fun bearerAuthHttpClientInterceptor(authMethod: CodegenSecurity) = FunSpec.builder(authMethod.name + "HttpClientAuthInterceptor")
        .addAnnotation(tagAnnotation(authMethod))
        .addParameter(ParameterSpec.builder("tokenProvider", Classes.httpClientTokenProvider.asKt()).addAnnotation(tagAnnotation(authMethod)).build())
        .returns(Classes.bearerAuthHttpClientInterceptor.asKt())
        .addStatement("return %T(tokenProvider)", Classes.bearerAuthHttpClientInterceptor.asKt())
        .build()

    private fun basicAuthHttpClientAuthInterceptor(authMethod: CodegenSecurity) = FunSpec.builder(authMethod.name + "HttpClientAuthInterceptor")
        .addAnnotation(tagAnnotation(authMethod))
        .addAnnotation(Classes.defaultComponent.asKt())
        .addParameter(ParameterSpec.builder("tokenProvider", Classes.httpClientTokenProvider.asKt()).addAnnotation(tagAnnotation(authMethod)).build())
        .returns(Classes.httpClientInterceptor.asKt())
        .addStatement("return %T(tokenProvider)", Classes.basicAuthHttpClientInterceptor.asKt())
        .build()

    private fun basicAuthHttpClientTokenProvider(authMethod: CodegenSecurity) = FunSpec.builder(authMethod.name + "BasicAuthHttpClientTokenProvider")
        .addAnnotation(tagAnnotation(authMethod))
        .addParameter("config", ClassName(apiPackage, "ApiSecurity", capitalize(authMethod.name) + "Config"))
        .returns(Classes.basicAuthHttpClientTokenProvider.asKt())
        .addStatement("return %T(config.username, config.password)", Classes.basicAuthHttpClientTokenProvider.asKt())
        .build()

    private fun basicAuthConfig(authMethod: CodegenSecurity): TypeSpec {
        return TypeSpec.classBuilder(capitalize(authMethod.name) + "Config")
            .addAnnotation(generated())
            .addProperty(PropertySpec.builder("username", String::class.asClassName()).initializer("username").build())
            .addProperty(PropertySpec.builder("password", String::class.asClassName()).initializer("password").build())
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("username", String::class.asClassName())
                    .addParameter("password", String::class.asClassName())
                    .build()
            )
            .addAnnotation(
                AnnotationSpec.builder(Classes.configSource.asKt()).addMember(
                    "value = %S", if (params.securityConfigPrefix != null)
                        params.securityConfigPrefix + "." + authMethod.name
                    else
                        authMethod.name
                )
                    .build()
            )
            .build()
    }

    private fun buildApiKeyInterceptor(ctx: Map<String, Any>, authMethod: CodegenSecurity): FunSpec {
        val apiKeyLocationClass = Classes.apiKeyHttpClientInterceptor.nestedClass("ApiKeyLocation").asKt()
        val apiKeyLocation = when {
            authMethod.isKeyInQuery -> "QUERY"
            authMethod.isKeyInHeader -> "HEADER"
            authMethod.isKeyInCookie -> "COOKIE"
            else -> throw java.lang.IllegalArgumentException("Invalid api key location for auth: " + authMethod.scheme)
        }

        return FunSpec.builder(authMethod.name + "HttpClientAuthInterceptor")
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(tagAnnotation(authMethod))
            .addParameter(ParameterSpec.builder("apiKey", String::class).addAnnotation(tagAnnotation(authMethod)).build())
            .addStatement("val paramLocation = %T.%N", apiKeyLocationClass, apiKeyLocation)
            .addStatement("return %T(paramLocation, %S, apiKey)", Classes.apiKeyHttpClientInterceptor.asKt(), authMethod.keyParamName)
            .returns(Classes.apiKeyHttpClientInterceptor.asKt())
            .build()
    }

    private fun buildApiKeyConfig(ctx: Map<String, Any>, authMethod: CodegenSecurity): FunSpec {
        return FunSpec.builder(authMethod.name + "Config")
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(tagAnnotation(authMethod))
            .addParameter("config", Classes.config.asKt())
            .addParameter("extractor", Classes.configValueExtractor.asKt().parameterizedBy(String::class.asClassName()))
            .addStatement(
                "val configPath = %S", if (params.securityConfigPrefix == null)
                    authMethod.name
                else
                    params.securityConfigPrefix + "." + authMethod.name
            )
            .addStatement("val configValue = config.get(configPath)")
            .addStatement("val parsed = extractor.extract(configValue)")
            .beginControlFlow("if (parsed == null)")
            .addStatement("throw %T.missingValueAfterParse(configValue)", Classes.configValueExtractionException.asKt())
            .endControlFlow()
            .addStatement("return parsed")
            .returns(String::class)
            .build()
    }


    private fun tagAnnotation(authMethod: CodegenSecurity) = AnnotationSpec.builder(Classes.tag.asKt())
        .addMember("value = [%T::class]", ClassName(apiPackage, "ApiSecurity", camelize(toVarName(authMethod.name))))
        .build()

    private fun buildTag(authMethod: CodegenSecurity) = TypeSpec.classBuilder(camelize(toVarName(authMethod.name)))
        .addAnnotation(generated())
        .build()
}
