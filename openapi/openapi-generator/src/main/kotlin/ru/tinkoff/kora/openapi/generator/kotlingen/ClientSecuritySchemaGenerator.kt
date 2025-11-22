package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenSecurity
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class ClientSecuritySchemaGenerator : AbstractKotlinGenerator<Map<String, Any>>() {
    override fun generate(ctx: Map<String, Any>): FileSpec {
        val className = ClassName(apiPackage, "ApiSecurity")
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addProperty(
                        PropertySpec.builder("log", Logger::class.asClassName())
                            .initializer("%T.getLogger(%T::class.java)", LoggerFactory::class.asClassName(), className)
                            .build()
                    )
                    .build()
            )
        val authMethods = ctx["authMethods"] as List<CodegenSecurity>
        val tags = mutableSetOf<String>()
        tags.addAll(security.interceptorTagBySecurityRequirement.values)
        authMethods.forEach { tags.add(it.name) }
        tags.forEach { b.addType(buildTag(it)) }

        securityConfig(authMethods)?.let { b.addType(it) }
        securityConfigComponent(authMethods)?.let { b.addFunction(it) }

        for (authMethod in authMethods) {
            when (authMethod.type) {
                "http" -> when (authMethod.scheme) {
                    "basic" -> b.addFunction(basicAuthHttpClientTokenProvider(authMethod))
                    "bearer" -> {}
                }

                "apiKey" -> {
                    b.addFunction(buildApiKeyTokenProvider(ctx, authMethod))
                }

                "oauth2" -> {}

                else -> throw IllegalArgumentException("unknown scheme type: ${authMethod.type}")
            }
        }

        for ((requirement, tag) in security.interceptorTagBySecurityRequirement) {
            b.addType(buildAuthGroupInterceptor(tag, requirement.toList(), authMethods))
            b.addFunction(buildAuthGroupInterceptorComponent(tag, requirement.toList(), authMethods))
        }
        return FileSpec.get(apiPackage, b.build())
    }


    private fun securityConfigComponent(authMethods: List<CodegenSecurity>): FunSpec? {
        val configClassName = ClassName(apiPackage, "ApiSecurity", "Config")
        val b = FunSpec.builder("config")
            .addParameter("config", Classes.config.asKt())
            .addParameter("extractor", Classes.configValueExtractor.asKt().parameterizedBy(String::class.asTypeName()))
            .returns(configClassName)
        val params = mutableListOf<CodeBlock>()
        for (authMethod in authMethods) {
            val configPath = (this.params.clientConfigPrefix?.let { it + "." } ?: "") + authMethod.name
            if (authMethod.type == "http" && authMethod.scheme == "basic") {
                val authMethodConfig = ClassName(apiPackage, "ApiSecurity", "Config", authMethod.name + "Config")
                val username = authMethod.name + "_username"
                val password = authMethod.name + "_password"
                b.addStatement("val %N = extractor.extract(config.get(%S))", username, configPath + ".username")
                b.addStatement("val %N = extractor.extract(config.get(%S))", password, configPath + ".password")
                b.addStatement("val %N = %T(%N, %N)", authMethod.name, authMethodConfig, username, password)
                params.add(CodeBlock.of("%N", authMethod.name))
            }
            if (authMethod.type == "apiKey") {
                b.addStatement("val %N = extractor.extract(config.get(%S))", authMethod.name, configPath)
                params.add(CodeBlock.of("%N", authMethod.name))
            }
        }
        if (params.isEmpty()) {
            return null
        }
        b.addStatement("return %T(%L)", configClassName, params.joinToCode(", "))
        return b.build()
    }

    private fun securityConfig(authMethods: List<CodegenSecurity>): TypeSpec? {
        val builder = TypeSpec.classBuilder("Config")
            .addModifiers(KModifier.DATA)
        val b = FunSpec.constructorBuilder()
        for (authMethod in authMethods) {
            if (authMethod.type == "http" && authMethod.scheme == "basic") {
                val configName = ClassName(apiPackage, "ApiSecurity", "Config", authMethod.name + "Config")
                builder.addProperty(PropertySpec.builder(authMethod.name, configName).initializer("%N", authMethod.name).build())
                b.addParameter(authMethod.name, configName)
                builder.addType(basicAuthConfig(authMethod))
            }
            if (authMethod.type == "apiKey") {
                b.addParameter(authMethod.name, String::class.asClassName().copy(nullable = true))
                builder.addProperty(PropertySpec.builder(authMethod.name, String::class.asClassName().copy(nullable = true)).initializer("%N", authMethod.name).build())
            }
        }
        val constructor = b.build()
        if (constructor.parameters.isEmpty()) {
            return null
        }
        return builder.primaryConstructor(constructor).build()
    }

    private fun buildAuthGroupInterceptorComponent(
        interceptorTag: String,
        security: List<Map<String, Set<String>>>,
        authMethods: List<CodegenSecurity>
    ): FunSpec {
        val interceptorClass = ClassName(apiPackage, "ApiSecurity", interceptorTag + "HttpClientInterceptor");
        val b = FunSpec.builder(interceptorTag + "HttpClientInterceptor_component")
            .addAnnotation(securityTagAnnotation(interceptorTag))
            .addAnnotation(Classes.defaultComponent.asKt())
            .returns(interceptorClass)
            .addCode("return %T(", interceptorClass)
        val seen = mutableSetOf<String>()
        for (securityRequirement in security) {
            for (securitySchema in securityRequirement.keys) {
                if (!seen.add(securitySchema)) {
                    continue
                }
                val param = ParameterSpec.builder(securitySchema, Classes.httpClientTokenProvider.asKt())
                    .addAnnotation(securityTagAnnotation(securitySchema))
                    .build()
                b.addParameter(param)
                if (seen.size > 1) {
                    b.addCode(", ")
                }
                b.addCode("%N", param.name)
            }
        }
        b.addCode(")\n")
        return b.build()
    }

    private fun buildAuthGroupInterceptor(
        interceptorTag: String,
        security: List<MutableMap<String, MutableSet<String>>>,
        authMethods: List<CodegenSecurity>
    ): TypeSpec {
        val b = TypeSpec.classBuilder(interceptorTag + "HttpClientInterceptor")
            .addAnnotation(generated())
            .addSuperinterface(Classes.httpClientInterceptor.asKt())

        val constructor = FunSpec.constructorBuilder()
        val seen = mutableSetOf<String>()
        for (securityRequirement in security) {
            for (securitySchema in securityRequirement.keys) {
                if (!seen.add(securitySchema)) {
                    continue
                }
                val param = ParameterSpec.builder(securitySchema, Classes.httpClientTokenProvider.asKt())
                    .addAnnotation(securityTagAnnotation(securitySchema))
                    .build()
                constructor.addParameter(param)
                b.addProperty(PropertySpec.builder(param.name, param.type).initializer("%N", param.name).build())
            }
        }
        b.primaryConstructor(constructor.build())

        val intercept = FunSpec.builder("processRequest")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Classes.httpClientResponse.asKt())
            .addParameter("chain", Classes.httpClientInterceptChain.asKt())
            .addParameter("request", Classes.httpClientRequest.asKt())

        val securitySchemaSeen = mutableSetOf<String>()
        val fullConditionSeen = mutableSetOf<CodeBlock>()
        for (securityRequirement in security) {
            for (securitySchemaName in securityRequirement.keys) {
                if (securitySchemaSeen.add(securitySchemaName)) {
                    intercept.addStatement("val %N = this.%N.getToken(request)", securitySchemaName, securitySchemaName)
                }
            }
            val ifProvided = securityRequirement.keys.map { CodeBlock.of("%N != null", it) }.joinToCode(" && ", "if (", ")")
            if (!fullConditionSeen.add(ifProvided)) {
                // kotlin type system goes mad if we do double null check on value in this method
                continue
            }
            intercept.beginControlFlow("%L", ifProvided)
            intercept.addStatement("val b = request.toBuilder()")
            for (securitySchemaName in securityRequirement.keys) {
                val securitySchema = authMethods.first { it.name.equals(securitySchemaName) }
                when (securitySchema.type) {
                    "http", "oauth2", "openId" -> intercept.addStatement("b.header(%S, %N)", "Authorization", securitySchemaName)
                    "apiKey" -> when {
                        securitySchema.isKeyInQuery -> intercept.addStatement("b.queryParam(%S, %N)", securitySchema.keyParamName, securitySchemaName)
                        securitySchema.isKeyInHeader -> intercept.addStatement("b.header(%S, %N)", securitySchema.keyParamName, securitySchemaName)
                        securitySchema.isKeyInCookie -> intercept.addStatement("TODO(%S)", "Cookie client authentication is not implemented yet")
                    }

                    else -> throw IllegalArgumentException("Unsupported schema $securitySchemaName")
                }
            }
            intercept.addStatement("return chain.process(b.build())")
            intercept.endControlFlow()
        }
        intercept.addStatement("log.warn(%S)", "Security schema is defined for api but no data was provided")
        intercept.addStatement("return chain.process(request)")
        b.addFunction(intercept.build())
        return b.build()
    }

    private fun basicAuthHttpClientTokenProvider(authMethod: CodegenSecurity): FunSpec {
        val configClassName = ClassName(apiPackage, "ApiSecurity", "Config");

        return FunSpec.builder(authMethod.name + "BasicAuthHttpClientTokenProvider")
            .addAnnotation(securityTagAnnotation(authMethod.name))
            .addParameter("config", configClassName)
            .returns(Classes.basicAuthHttpClientTokenProvider.asKt())
            .addStatement("return %T(config.%N.username, config.%N.password)", Classes.basicAuthHttpClientTokenProvider.asKt(), authMethod.name, authMethod.name)
            .build()
    }

    private fun basicAuthConfig(authMethod: CodegenSecurity): TypeSpec {
        return TypeSpec.classBuilder(authMethod.name + "Config")
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

    private fun buildApiKeyTokenProvider(ctx: Map<String, Any>, authMethod: CodegenSecurity): FunSpec {
        val configClassName = ClassName(apiPackage, "ApiSecurity", "Config");

        return FunSpec.builder(authMethod.name + "TokenProvider")
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(securityTagAnnotation(authMethod.name))
            .addParameter("config", configClassName)
            .addStatement("return %T { config.%N }", Classes.httpClientTokenProvider.asKt(), authMethod.name)
            .returns(Classes.httpClientTokenProvider.asKt())
            .build()
    }

    private fun buildTag(tag: String) = TypeSpec.classBuilder(tag)
        .addAnnotation(generated())
        .build()
}
