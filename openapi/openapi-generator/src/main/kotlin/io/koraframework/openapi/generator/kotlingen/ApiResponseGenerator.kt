package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import org.openapitools.codegen.CodegenResponse
import org.openapitools.codegen.model.OperationsMap

class ApiResponseGenerator : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val className = ClassName(apiPackage, ctx["classname"].toString() + "Responses")
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
        for (operation in ctx.operations.operation) {
            val responseClassName = className.nestedClass(capitalize(operation.operationId) + "ApiResponse")
            if (operation.responses.size == 1) {
                b.addType(response(ctx, responseClassName, operation.responses.single(), null))
            } else {
                val t = TypeSpec.interfaceBuilder(responseClassName)
                    .addAnnotation(generated())
                    .addModifiers(KModifier.SEALED)
                val sharedResponses = if (params.codegenMode.isClient) sharedResponses(responseClassName, operation.responses) else emptyMap()
                sharedResponses.values.forEach { t.addType(it.type) }
                for (response in operation.responses) {
                    val codeResponseClassName = if (response.isDefault)
                        capitalize(operation.operationId) + "DefaultApiResponse"
                    else
                        capitalize(operation.operationId) + response.code + "ApiResponse"
                    val codeResponseName = responseClassName.nestedClass(codeResponseClassName)
                    t.addType(response(ctx, codeResponseName, response, sharedResponses[response.dataType]))
                }
                b.addType(t.build())
            }
        }
        return FileSpec.get(apiPackage, b.build())
    }

    private fun response(ctx: OperationsMap, name: ClassName, response: CodegenResponse, sharedResponse: SharedResponse?): TypeSpec {
        val t = TypeSpec.classBuilder(name)
            .addAnnotation(generated())
            .addKdoc("${response.message} (status code ${response.code})")
        if (response.isDefault || response.dataType != null || response.headers.isNotEmpty()) {
            t.addModifiers(KModifier.DATA)
        }
        if (name.enclosingClassName()?.enclosingClassName() != null) {
            t.addSuperinterface(sharedResponse?.className ?: name.enclosingClassName()!!)
        }
        val c = FunSpec.constructorBuilder()
        if (response.isDefault) {
            c.addParameter("statusCode", INT)
            val statusCodeProperty = PropertySpec.builder("statusCode", INT)
                .initializer("statusCode")
            if (sharedResponse?.statusCodeProperty == "statusCode") {
                statusCodeProperty.addModifiers(KModifier.OVERRIDE)
            }
            t.addProperty(statusCodeProperty.build())
        }
        response.dataType?.let {
            val type = asType(response).asKt()
            c.addParameter("content", type)
            val contentProperty = PropertySpec.builder("content", type)
                .initializer("content")
            if (sharedResponse != null) {
                contentProperty.addModifiers(KModifier.OVERRIDE)
            }
            t.addProperty(contentProperty.build())
        }
        for (header in response.headers) {
            var type: TypeName = String::class.asClassName() // todo Some header decoding maybe? We can do it
            if (!header.required) {
                type = type.copy(nullable = true)
            }
            c.addParameter(header.name, type)
            t.addProperty(PropertySpec.builder(header.name, type).initializer(header.name).build())
        }
        if (sharedResponse?.statusCodeProperty != null && (!response.isDefault || sharedResponse.statusCodeProperty != "statusCode")) {
            val property = PropertySpec.builder(sharedResponse.statusCodeProperty, INT)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %L", if (response.isDefault) "statusCode" else response.code)
                        .build()
                )
                .build()
            t.addProperty(property)
        }
        return t.primaryConstructor(c.build()).build()
    }

    private fun sharedResponses(responseClassName: ClassName, responses: List<CodegenResponse>): Map<String, SharedResponse> {
        return responses.asSequence()
            .filter { it.dataType != null }
            .groupBy { it.dataType }
            .filterValues { it.size > 1 }
            .mapValues { (dataType, responsesByType) ->
                val contentType = asType(responsesByType.first()).asKt()
                val className = responseClassName.nestedClass(responseClassName.simpleName.removeSuffix("ApiResponse") + sanitizeSharedResponseName(dataType) + "ApiResponse")
                val type = TypeSpec.interfaceBuilder(className)
                    .addAnnotation(generated())
                    .addSuperinterface(responseClassName)
                    .addProperty(PropertySpec.builder("content", contentType).build())
                    .addProperty(PropertySpec.builder("statusCode", INT).build())
                SharedResponse(className, type.build(), "statusCode")
            }
    }

    private fun sanitizeSharedResponseName(dataType: String): String {
        val name = dataType.replace(Regex("[^a-zA-Z0-9]"), "")
        return if (name.isBlank()) "Content" else capitalize(name)
    }

    private data class SharedResponse(val className: ClassName, val type: TypeSpec, val statusCodeProperty: String)
}
