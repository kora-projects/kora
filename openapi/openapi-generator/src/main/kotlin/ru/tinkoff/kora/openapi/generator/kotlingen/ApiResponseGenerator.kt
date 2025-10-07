package ru.tinkoff.kora.openapi.generator.kotlingen

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
                b.addType(response(ctx, responseClassName, operation.responses.single()))
            } else {
                val t = TypeSpec.interfaceBuilder(responseClassName)
                    .addAnnotation(generated())
                    .addModifiers(KModifier.SEALED)
                for (response in operation.responses) {
                    val codeResponseClassName = if (response.isDefault)
                        capitalize(operation.operationId) + "DefaultApiResponse"
                    else
                        capitalize(operation.operationId) + response.code + "ApiResponse"
                    val codeResponseName = responseClassName.nestedClass(codeResponseClassName)
                    t.addType(response(ctx, codeResponseName, response))
                }
                b.addType(t.build())
            }
        }
        return FileSpec.get(apiPackage, b.build())
    }

    private fun response(ctx: OperationsMap, name: ClassName, response: CodegenResponse): TypeSpec {
        val t = TypeSpec.classBuilder(name)
            .addAnnotation(generated())
            .addKdoc("${response.message} (status code ${response.code})")
        if (response.isDefault || response.dataType != null || response.headers.isNotEmpty()) {
            t.addModifiers(KModifier.DATA)
        }
        if (name.enclosingClassName()?.enclosingClassName() != null) {
            t.addSuperinterface(name.enclosingClassName()!!)
        }
        val c = FunSpec.constructorBuilder()
        if (response.isDefault) {
            c.addParameter("statusCode", INT)
            t.addProperty(PropertySpec.builder("statusCode", INT).initializer("statusCode").build())
        }
        response.dataType?.let {
            val type = asType(response).asKt()
            c.addParameter("content", type)
            t.addProperty(PropertySpec.builder("content", type).initializer("content").build())
        }
        for (header in response.headers) {
            var type: TypeName = String::class.asClassName() // todo Some header decoding maybe? We can do it
            if (!header.required) {
                type = type.copy(nullable = true)
            }
            c.addParameter(header.name, type)
            t.addProperty(PropertySpec.builder(header.name, type).initializer(header.name).build())
        }
        return t.primaryConstructor(c.build()).build()
    }
}
