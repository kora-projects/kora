package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.koraframework.openapi.generator.SecurityData
import org.openapitools.codegen.CodegenSecurity
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class ClientSecuritySchemaGenerator : AbstractKotlinGenerator<Map<String, Any>>() {
    private val generatorLog = LoggerFactory.getLogger(ClientSecuritySchemaGenerator::class.java)

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
        authMethods.forEach { tags.add(this.security.tagForSecurityScheme(it.name)) }
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

                else -> throw IllegalArgumentException(unsupportedSecurityTypeError(authMethod))
            }
        }

        for ((requirement, tag) in security.interceptorTagBySecurityRequirement) {
            warnAboutCombinedHeaderSecurity(tag, requirement, authMethods)
            b.addType(buildAuthGroupInterceptor(tag, requirement.toList(), authMethods))
            b.addFunction(buildAuthGroupInterceptorComponent(tag, requirement.toList(), authMethods))
        }
        return FileSpec.get(apiPackage, b.build())
    }


    private fun securityConfigComponent(authMethods: List<CodegenSecurity>): FunSpec? {
        val configClassName = ClassName(apiPackage, "ApiSecurity", "SecurityConfig")
        val b = FunSpec.builder("securityConfig")
            .addAnnotation(Classes.defaultComponent.asKt())
            .addParameter("config", Classes.config.asKt())
            .addParameter("mapper", Classes.configValueExtractor.asKt().parameterizedBy(String::class.asTypeName()))
            .returns(configClassName)
        val params = mutableListOf<CodeBlock>()
        val securityConfigPathPrefix = securityConfigPathPrefix()
        for (authMethod in authMethods) {
            val configPath = securityConfigPathPrefix + "." + authMethod.name
            if (authMethod.type == "http" && authMethod.scheme == "basic") {
                val authMethodConfig = securityAuthMethodConfigClassName(authMethod)
                val username = authMethod.name + "_username"
                val password = authMethod.name + "_password"
                b.addStatement("val %N = mapper.map(config.get(%S))", username, configPath + ".username")
                b.addStatement("val %N = mapper.map(config.get(%S))", password, configPath + ".password")
                b.addStatement("val %N = if (%N == null && %N == null) null else %T(%N, %N)", authMethod.name, username, password, authMethodConfig, username, password)
                params.add(CodeBlock.of("%N", authMethod.name))
            }
            if (authMethod.type == "apiKey") {
                b.addStatement("val %N = mapper.map(config.get(%S))", authMethod.name, configPath)
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
        val builder = TypeSpec.classBuilder("SecurityConfig")
            .addAnnotation(generated())
            .addModifiers(KModifier.DATA)
        val b = FunSpec.constructorBuilder()
        for (authMethod in authMethods) {
            if (authMethod.type == "http" && authMethod.scheme == "basic") {
                val configName = securityAuthMethodConfigClassName(authMethod)
                builder.addProperty(PropertySpec.builder(authMethod.name, configName.copy(nullable = true)).initializer("%N", authMethod.name).build())
                b.addParameter(authMethod.name, configName.copy(nullable = true))
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
                    .addAnnotation(securityTagAnnotation(this.security.tagForSecurityScheme(securitySchema)))
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
                    .addAnnotation(securityTagAnnotation(this.security.tagForSecurityScheme(securitySchema)))
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
        val allowAnonymous = SecurityData.hasAnonymousRequirement(security)
        for (securityRequirement in security) {
            if (securityRequirement.isEmpty()) {
                continue
            }
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
            val cookieHeaderName = uniqueLocalName(security, "_securityCookieHeader")
            val hasCookieSecurity = securityRequirement.keys
                .map { name -> authMethods.first { it.name == name } }
                .any { it.isApiKey && it.isKeyInCookie }
            if (hasCookieSecurity) {
                intercept.addStatement("var %N = request.headers().getFirst(%S)", cookieHeaderName, "Cookie")
            }
            for (securitySchemaName in securityRequirement.keys) {
                val securitySchema = authMethods.first { it.name.equals(securitySchemaName) }
                when (securitySchema.type) {
                    "http", "oauth2", "openId" -> intercept.addStatement("b.header(%S, %N)", "Authorization", securitySchemaName)
                    "apiKey" -> when {
                        securitySchema.isKeyInQuery -> intercept.addStatement("b.queryParam(%S, %N)", securitySchema.keyParamName, securitySchemaName)
                        securitySchema.isKeyInHeader -> intercept.addStatement("b.header(%S, %N)", securitySchema.keyParamName, securitySchemaName)
                        securitySchema.isKeyInCookie -> intercept.addStatement("%N = if (%N.isNullOrBlank()) %S + %N else %N + %S + %S + %N", cookieHeaderName, cookieHeaderName, securitySchema.keyParamName + "=", securitySchemaName, cookieHeaderName, "; ", securitySchema.keyParamName + "=", securitySchemaName)
                        else -> throw IllegalArgumentException(invalidApiKeyLocationError(securitySchema))
                    }

                    else -> throw IllegalArgumentException(unsupportedSecurityTypeError(securitySchema))
                }
            }
            if (hasCookieSecurity) {
                intercept.addStatement("b.header(%S, %N)", "Cookie", cookieHeaderName)
            }
            intercept.addStatement("return chain.process(b.build())")
            intercept.endControlFlow()
        }
        if (!allowAnonymous) {
            intercept.addStatement("log.warn(%S)", "Security schema is defined for api but no data was provided")
        }
        intercept.addStatement("return chain.process(request)")
        b.addFunction(intercept.build())
        return b.build()
    }

    private fun warnAboutCombinedHeaderSecurity(interceptorTag: String, security: Set<Map<String, Set<String>>>, authMethods: List<CodegenSecurity>) {
        for (requirement in security) {
            val methods = requirement.keys.map { name -> authMethods.first { it.name == name } }
            val authorizationSchemes = methods.filter { it.type == "http" || it.type == "oauth2" || it.type == "openId" }.map { it.name }
            if (authorizationSchemes.size > 1) {
                generatorLog.warn("Security requirement '{}' uses multiple Authorization schemes {}; generated client applies them in declaration order and the last value wins", interceptorTag, authorizationSchemes)
            }
            val cookieSchemes = methods.filter { it.isApiKey && it.isKeyInCookie }.map { it.name }
            if (cookieSchemes.size > 1) {
                generatorLog.warn("Security requirement '{}' uses multiple cookie schemes {}; generated client combines them into one Cookie header", interceptorTag, cookieSchemes)
            }
        }
    }

    private fun uniqueLocalName(security: List<Map<String, Set<String>>>, baseName: String): String {
        val names = security.flatMap { it.keys }.toSet()
        var result = baseName
        while (names.contains(result)) {
            result = "_" + result
        }
        return result
    }

    private fun unsupportedSecurityTypeError(securitySchema: CodegenSecurity): String {
        return """
            Unsupported OpenAPI security scheme `${securitySchema.name}`.

            Scheme type: `${securitySchema.type}`
            Scheme name: `${securitySchema.name}`

            Supported client security types: `http` basic/bearer, `apiKey`, and `oauth2`.
            Fix: use a supported OpenAPI security scheme type or provide custom client authentication outside generated security.
        """.trimIndent()
    }

    private fun invalidApiKeyLocationError(securitySchema: CodegenSecurity): String {
        return """
            Invalid OpenAPI apiKey security scheme `${securitySchema.name}`.

            Kora supports apiKey client auth in query parameters, headers, and cookies.
            Unsupported location: `${securitySchema.scheme}`

            Fix: set `in: query`, `in: header`, or `in: cookie` for this security scheme.
        """.trimIndent()
    }

    private fun basicAuthHttpClientTokenProvider(authMethod: CodegenSecurity): FunSpec {
        val configClassName = ClassName(apiPackage, "ApiSecurity", "SecurityConfig");

        return FunSpec.builder(authMethod.name + "BasicAuthHttpClientTokenProvider")
            .addAnnotation(securityTagAnnotation(this.security.tagForSecurityScheme(authMethod.name)))
            .addParameter("config", configClassName)
            .returns(Classes.basicAuthHttpClientTokenProvider.asKt())
            .addStatement("return config.%N?.let { %T(it.username, it.password) } ?: %T(null, null)", authMethod.name, Classes.basicAuthHttpClientTokenProvider.asKt(), Classes.basicAuthHttpClientTokenProvider.asKt())
            .build()
    }

    private fun basicAuthConfig(authMethod: CodegenSecurity): TypeSpec {
        val stringType = String::class.asClassName().copy(nullable = true)
        return TypeSpec.classBuilder(securityAuthMethodConfigName(authMethod))
            .addModifiers(KModifier.DATA)
            .addAnnotation(generated())
            .addProperty(PropertySpec.builder("username", stringType).initializer("username").build())
            .addProperty(PropertySpec.builder("password", stringType).initializer("password").build())
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("username", stringType)
                    .addParameter("password", stringType)
                    .build()
            )
            .build()
    }

    private fun buildApiKeyTokenProvider(ctx: Map<String, Any>, authMethod: CodegenSecurity): FunSpec {
        val configClassName = ClassName(apiPackage, "ApiSecurity", "SecurityConfig");

        return FunSpec.builder(authMethod.name + "TokenProvider")
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(securityTagAnnotation(this.security.tagForSecurityScheme(authMethod.name)))
            .addParameter("config", configClassName)
            .addStatement("return %T { config.%N }", Classes.httpClientTokenProvider.asKt(), authMethod.name)
            .returns(Classes.httpClientTokenProvider.asKt())
            .build()
    }

    private fun securityAuthMethodConfigClassName(authMethod: CodegenSecurity): ClassName {
        return ClassName(apiPackage, "ApiSecurity", "SecurityConfig", securityAuthMethodConfigName(authMethod))
    }

    private fun securityAuthMethodConfigName(authMethod: CodegenSecurity): String {
        return "Security" + this.security.tagForSecurityScheme(authMethod.name) + "Config"
    }

    private fun securityConfigPathPrefix(): String {
        params.securityConfigPrefix?.takeIf { it.isNotBlank() }?.let { return it }
        params.clientConfigPrefix?.takeIf { it.isNotBlank() }?.let { return it + ".security" }
        params.clientConfig?.takeIf { it.isNotBlank() }?.let { return it + ".security" }
        return "security"
    }

    private fun buildTag(tag: String) = TypeSpec.classBuilder(tag)
        .addAnnotation(generated())
        .build()
}
