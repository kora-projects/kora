package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenSecurity
import java.lang.Boolean
import kotlin.Any
import kotlin.IllegalArgumentException
import kotlin.RuntimeException
import kotlin.String


class ServerSecuritySchemaGenerator : AbstractKotlinGenerator<Map<String, Any>>() {
    override fun generate(ctx: Map<String, Any>): FileSpec {
        val b = TypeSpec.interfaceBuilder("ApiSecurity")
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())
        val tags = mutableSetOf<String>()
        val authMethods = ctx["authMethods"] as List<CodegenSecurity>
        for (authMethod in authMethods) {
            tags.add(camelize(toVarName(authMethod.name)))
        }
        val authGroups = collectServerAuthMethodGroups()
        for (authGroup in authGroups) {
            tags.add(authGroup.name)
        }
        tags.addAll(collectServerAuthTags())
        for (tag in tags) {
            b.addType(buildTag(tag))
        }
        for (authGroup in authGroups) {
            b.addType(buildAuthGroupInterceptor(authGroup))
            b.addFunction(buildAuthGroupInterceptorComponent(authGroup))
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun buildAuthGroupInterceptor(authGroup: ServerAuthMethodGroup): TypeSpec {
        val b = TypeSpec.classBuilder(authGroup.name + "HttpServerInterceptor")
            .addAnnotation(generated())
            .addSuperinterface(Classes.httpServerInterceptor.asKt())
        val constructor = FunSpec.constructorBuilder()
        for (method in authGroup.methods) {
            val extractorType = Classes.httpServerPrincipalExtractor.asKt().parameterizedBy(
                if (method.isOAuth)
                    Classes.principalWithScopes.asKt()
                else
                    Classes.principal.asKt()
            )
            b.addProperty(PropertySpec.builder(method.name, extractorType).initializer(method.name).build())
            constructor.addParameter(
                ParameterSpec.builder(method.name, extractorType)
                    .build()
            )
        }
        b.primaryConstructor(constructor.build())
        val intercept = FunSpec.builder("intercept")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Classes.httpServerResponse.asKt())
            .addParameter("request", Classes.httpServerRequest.asKt())
            .addParameter("chain", Classes.httpServerInterceptChain.asKt())
        for (method in authGroup.methods) {
            if (method.isApiKey) {
                if (method.isKeyInHeader) {
                    intercept.addStatement("val %N = request.headers().getFirst(%S)", method.name + "_token", method.keyParamName)
                } else if (method.isKeyInQuery) {
                    intercept.addStatement("val %N = request.queryParams().get(%S)", method.name + "_cookie", method.keyParamName)
                    intercept.addStatement(
                        "val %N = %N?.firstOrNull()",
                        method.name + "_token",
                        method.name + "_query",
                    )
                } else if (method.isKeyInCookie) {
                    intercept.addStatement(
                        "val %N = request.cookies().asSequence().filter { %S == it.name() }.map { it.value() }.firstOrNull()",
                        method.name + "_token",
                        method.keyParamName
                    )
                } else {
                    throw IllegalArgumentException()
                }
            } else if (method.isBasicBasic || method.isBasicBearer || method.isOAuth) {
                intercept.addStatement("val %N = request.headers().getFirst(%S)", method.name + "_token", "Authorization")
            } else {
                throw IllegalArgumentException()
            }
            intercept.addStatement("val %N = this.%N.extract(request, %N)", method.name, method.name, method.name + "_token")
            intercept.beginControlFlow("if (%N != null)", method.name)
            if (Boolean.TRUE == method.isOAuth && method.scopes != null && !method.scopes.isEmpty()) {
                for (scope in method.scopes) {
                    intercept.beginControlFlow("if (!%N.scopes().contains(%S))", method.name, scope["scope"]!!)
                    intercept.addStatement("throw %T.of(403, %S)", Classes.httpServerResponseException.asKt(), "Forbidden")
                    intercept.endControlFlow()
                }
            }
            intercept.addStatement("return %T.with<%T, %T>(%N) { chain.process(request) }", Classes.principal.asKt(), Classes.httpServerResponse.asKt(), RuntimeException::class.asClassName(), method.name)
            intercept.endControlFlow()
        }
        intercept.addStatement("throw %T.of(403, %S)", Classes.httpServerResponseException.asKt(), "Forbidden")
        b.addFunction(intercept.build())
        return b.build()
    }


    private fun buildAuthGroupInterceptorComponent(authGroup: ServerAuthMethodGroup): FunSpec {
        val interceptorClass = ClassName(apiPackage, "ApiSecurity", authGroup.name + "HttpServerInterceptor")
        val b = FunSpec.builder("_" + authGroup.name + "HttpServerInterceptor")
            .addAnnotation(tagAnnotation(capitalize(authGroup.name)))
            .addAnnotation(Classes.defaultComponent.asKt())
            .returns(interceptorClass)
            .addCode("return %T(", interceptorClass)
        for (i in authGroup.methods.indices) {
            val method = authGroup.methods[i]
            val extractorType = Classes.httpServerPrincipalExtractor.asKt().parameterizedBy(
                if (method.isOAuth)
                    Classes.principalWithScopes.asKt()
                else
                    Classes.principal.asKt()
            )
            b.addParameter(
                ParameterSpec.builder(method.name, extractorType)
                    .addAnnotation(tagAnnotation(camelize(toVarName(method.name))))
                    .build()
            )
            if (i > 0) {
                b.addCode(", ")
            }
            b.addCode("%N", method.name)
        }
        b.addCode(")\n")

        return b.build()
    }


    private fun tagAnnotation(securityTagName: String): AnnotationSpec {
        return AnnotationSpec.builder(Classes.tag.asKt())
            .addMember("value = [%T::class]", ClassName(apiPackage, "ApiSecurity", securityTagName))
            .build()
    }

    private fun buildTag(name: String): TypeSpec {
        return TypeSpec.classBuilder(name)
            .addAnnotation(generated())
            .build()
    }
}
