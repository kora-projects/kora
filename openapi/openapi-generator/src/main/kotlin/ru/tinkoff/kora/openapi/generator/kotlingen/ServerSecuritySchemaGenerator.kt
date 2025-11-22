package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenSecurity


class ServerSecuritySchemaGenerator : AbstractKotlinGenerator<Map<String, Any>>() {
    override fun generate(ctx: Map<String, Any>): FileSpec {
        val b = TypeSpec.interfaceBuilder("ApiSecurity")
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())
        val tags = mutableSetOf<String>()
        val authMethods = ctx["authMethods"] as List<CodegenSecurity>

        tags.addAll(security.interceptorTagBySecurityRequirement.values)
        tags.addAll(security.principalExtractorTagBySecurityRequirementNames.values)

        for (tag in tags) {
            b.addType(buildTag(tag))
        }
        for (entry in security.principalExtractorTagBySecurityRequirementNames.entries) {
            val securityRequirementNames = entry.key
            val tag = entry.value
            if (securityRequirementNames.size > 1) {
                b.addType(buildAuthData(tag, securityRequirementNames, authMethods))
            }
        }
        for (entry in security.interceptorTagBySecurityRequirement.entries) {
            val requirement = entry.key
            val tag = entry.value
            b.addType(buildAuthGroupInterceptor(tag, requirement.toList(), authMethods))
            b.addFunction(buildAuthGroupInterceptorComponent(tag, requirement.toList(), authMethods))
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun buildAuthData(tag: String, securityRequirementNames: Set<String>, authMethods: List<CodegenSecurity>): TypeSpec {
        val constructor = FunSpec.constructorBuilder()
        val type = TypeSpec.classBuilder(tag + "AuthData")
            .addModifiers(KModifier.DATA)
        for (securityRequirementName in securityRequirementNames) {
            val authMethod = authMethods.first { m -> m.name == securityRequirementName }
            val doc = authMethodParameterJavadoc(authMethod)
            type.addProperty(
                PropertySpec.builder(securityRequirementName, String::class.asClassName().copy(nullable = true))
                    .addKdoc(doc)
                    .initializer("%N", securityRequirementName)
                    .build()
            )
            constructor.addParameter(
                ParameterSpec.builder(securityRequirementName, String::class.asClassName().copy(nullable = true))
                    .addKdoc(doc)
                    .build()
            )
        }
        return type.primaryConstructor(constructor.build()).build()
    }


    private fun authMethodParameterJavadoc(authMethod: CodegenSecurity): String {
        if (authMethod.isOAuth || authMethod.isOpenId || authMethod.isBasic || authMethod.isBasicBasic || authMethod.isBasicBearer) {
            return "'Authorization' header of request\n"
        }
        if (authMethod.isApiKey) {
            return when {
                authMethod.isKeyInHeader -> "'" + authMethod.keyParamName + "' header of request\n"
                authMethod.isKeyInQuery -> "'" + authMethod.keyParamName + "' query parameter value\n"
                authMethod.isKeyInCookie -> "'" + authMethod.keyParamName + "' cookie value\n"
                else -> throw IllegalArgumentException()
            }
        }
        return "\n"
    }


    private fun buildAuthGroupInterceptor(interceptorTag: String, security: List<Map<String, Set<String>>>, authMethods: List<CodegenSecurity>): TypeSpec {
        val b = TypeSpec.classBuilder(interceptorTag + "HttpServerInterceptor")
            .addAnnotation(generated())
            .addSuperinterface(Classes.httpServerInterceptor.asKt())
        val constructor = FunSpec.constructorBuilder()
        val seen = hashSetOf<String>()
        for (securityRequirement in security) {
            val principalExtractorTag = this.security.principalExtractorTagBySecurityRequirementNames[securityRequirement.keys]!!
            if (!seen.add(principalExtractorTag)) {
                continue
            }
            val param = securityRequirementPrincipalExtractorParameter(authMethods, securityRequirement, principalExtractorTag)
            constructor.addParameter(param)
            b.addProperty(
                PropertySpec.builder(param.name, param.type, KModifier.PRIVATE)
                    .initializer("%N", param.name)
                    .build()
            )
        }
        b.primaryConstructor(constructor.build())
        val intercept = FunSpec.builder("intercept")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Classes.httpServerResponse.asKt())
            .addParameter("request", Classes.httpServerRequest.asKt())
            .addParameter("chain", Classes.httpServerInterceptChain.asKt())

        val securitySchemaSeen = mutableSetOf<String>()
        val securityRequirementSeen = mutableSetOf<String>()
        for (securityRequirement in security) {
            for ((securitySchemaName, scopes) in securityRequirement.entries) {
                if (securitySchemaSeen.add(securitySchemaName)) {
                    val securitySchema = authMethods.first { s -> s.name.equals(securitySchemaName) }
                    if (securitySchema.isApiKey) {
                        when {
                            securitySchema.isKeyInHeader -> intercept.addStatement(
                                "val %N = request.headers().getFirst(%S)",
                                securitySchemaName,
                                securitySchema.keyParamName
                            )

                            securitySchema.isKeyInQuery -> intercept.addStatement(
                                "val %N = request.queryParams().get(%S)?.firstOrNull()",
                                securitySchemaName,
                                securitySchema.keyParamName
                            )

                            securitySchema.isKeyInCookie -> intercept.addStatement(
                                "val %N = request.cookies().firstOrNull { c -> %S == c.name() }?.value()",
                                securitySchemaName,
                                securitySchema.keyParamName
                            )

                            else -> throw IllegalArgumentException()
                        }
                    } else if (securitySchema.isBasicBasic || securitySchema.isBasicBearer || securitySchema.isOAuth) {
                        intercept.addStatement("var %N = request.headers().getFirst(%S)", securitySchemaName, "Authorization")
                    } else {
                        throw IllegalArgumentException()
                    }
                }
            }
            val extractorTag = this.security.principalExtractorTagBySecurityRequirementNames[securityRequirement.keys]!!
            if (securityRequirementSeen.add(extractorTag)) {
                if (securityRequirement.size == 1) {
                    intercept.addStatement("val %N = this.%N_.extract(request, %N)", extractorTag, extractorTag, securityRequirement.keys.first())
                } else {
                    val authData = ClassName(this.apiPackage, "ApiSecurity", extractorTag + "AuthData");
                    val params = securityRequirement.keys.map { CodeBlock.of("%N", it) }.joinToCode(", ", "(", ")")
                    intercept.addStatement("val %N = this.%N_.extract(\n  request,\n  %T%L)", extractorTag, extractorTag, authData, params)
                }
            }
            intercept.beginControlFlow("if (%N != null)", extractorTag)
            var scopesCount = 0

            for ((securitySchemaName, scopes) in securityRequirement.entries) {
                val securitySchema = authMethods.first { it.name.equals(securitySchemaName) }
                if (securitySchema.isOAuth == true && !scopes.isEmpty()) {
                    for (scope in scopes) {
                        intercept.beginControlFlow("if (%N.scopes().contains(%S))", extractorTag, scope)
                        scopesCount++
                    }
                }
            }
            intercept.addStatement("return %T.with<%T, %T>(%N) { chain.process(request) }", Classes.principal.asKt(), Classes.httpServerResponse.asKt(), RuntimeException::class.asClassName(), extractorTag)
            for (i in 0 until scopesCount) {
                intercept.endControlFlow()
            }

            intercept.endControlFlow()

            intercept.addCode("\n");
        }

        intercept.addStatement("throw %T.of(403, %S)", Classes.httpServerResponseException.asKt(), "Forbidden")
        b.addFunction(intercept.build())
        return b.build()
    }


    private fun buildAuthGroupInterceptorComponent(interceptorTag: String, security: List<Map<String, Set<String>>>, authMethods: List<CodegenSecurity>): FunSpec {
        val interceptorClass = ClassName(apiPackage, "ApiSecurity", interceptorTag + "HttpServerInterceptor")
        val b = FunSpec.builder(interceptorTag + "HttpServerInterceptorComponent")
            .addAnnotation(securityTagAnnotation(interceptorTag))
            .addAnnotation(Classes.defaultComponent.asKt())
            .returns(interceptorClass)
            .addCode("return %T(", interceptorClass)
        val seen = mutableSetOf<String>()
        for (securityRequirement in security) {
            val principalExtractorTag = this.security.principalExtractorTagBySecurityRequirementNames.get(securityRequirement.keys)!!
            if (!seen.add(principalExtractorTag)) {
                continue
            }
            val param = securityRequirementPrincipalExtractorParameter(authMethods, securityRequirement, principalExtractorTag)
            b.addParameter(param)
            if (seen.size > 1) {
                b.addCode(", ")
            }
            b.addCode("%N", param.name)
        }
        b.addCode(")\n")

        return b.build()
    }

    private fun securityRequirementPrincipalExtractorParameter(
        authMethods: List<CodegenSecurity>,
        securityRequirement: Map<String, Set<String>>,
        principalExtractorTag: String
    ): ParameterSpec {
        val needScopes = authMethods
            .filter { auth -> securityRequirement.containsKey(auth.name) }
            .any { auth -> auth.isOAuth || auth.isOpenId }
        val extractorParam = if (securityRequirement.size == 1) String::class.asClassName() else ClassName(apiPackage, "ApiSecurity", principalExtractorTag + "AuthData")
        val principalType = if (needScopes) Classes.principalWithScopes.asKt() else Classes.principal.asKt()
        val extractorType = Classes.httpServerPrincipalExtractor.asKt().parameterizedBy(extractorParam, principalType)
        return ParameterSpec.builder(principalExtractorTag + "_", extractorType)
            .addAnnotation(securityTagAnnotation(principalExtractorTag))
            .build()
    }



    private fun buildTag(name: String): TypeSpec {
        return TypeSpec.classBuilder(name)
            .addAnnotation(generated())
            .build()
    }
}
