package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.model.OperationsMap
import ru.tinkoff.kora.openapi.generator.DelegateMethodBodyMode


class ServerApiDelegateGenerator : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val b = TypeSpec.interfaceBuilder(ctx.get("classname").toString() + "Delegate")
            .addAnnotation(generated())

        for (operation in ctx.operations.operation) {
            b.addFunction(buildFunction(ctx, operation))
            if (operation.hasFormParams) {
                b.addType(buildFormParamsRecord(ctx, operation))
            }
        }
        return FileSpec.get(apiPackage, b.build())
    }

    private fun buildFunction(ctx: OperationsMap, operation: CodegenOperation): FunSpec {
        val tag = ctx.get("baseName").toString()
        val b = FunSpec.builder(operation.operationId)
            .addKdoc(buildFunctionKdoc(ctx, operation))
        if (operation.isDeprecated) {
            b.addAnnotation(AnnotationSpec.builder(Deprecated::class).addMember("%S", "deprecated").build())
        }
        this.buildAdditionalAnnotations(tag).forEach(b::addAnnotation)
        this.buildImplicitHeaders(operation).forEach(b::addAnnotation)
        b.addAnnotation(this.buildRouteAnnotation(operation))
        if (params.delegateMethodBodyMode == DelegateMethodBodyMode.THROW_EXCEPTION) {
            b.addStatement("TODO()")
        } else {
            b.addModifiers(KModifier.ABSTRACT)
        }
        if (params.requestInDelegateParams) {
            b.addParameter("_serverRequest", Classes.httpServerRequest.asKt())
        }
        for (param in operation.allParams) {
            if (param.isFormParam) {
                continue  // form params are handled separately
            }
            if (param.isHeaderParam && operation.implicitHeadersParams != null && operation.implicitHeadersParams.stream().anyMatch({ h -> h.paramName.equals(param.paramName) })) {
                continue
            }
            b.addParameter(this.buildParameter(ctx, operation, param))
        }
        if (operation.hasFormParams) {
            val className = ClassName(
                apiPackage, "${ctx.get("classname")}Controller", capitalize(operation.operationId) + "FormParam"
            )
            val mapper = ClassName(
                apiPackage, "${ctx.get("classname")}ServerRequestMappers", capitalize(operation.operationId) + "FormParamRequestMapper"
            )
            val parameter = ParameterSpec.builder("form", className)
                .addAnnotation(
                    AnnotationSpec.builder(Classes.mapping.asKt())
                        .addMember("value = %T::class", mapper)
                        .build()
                )
                .build()
            b.addParameter(parameter)
        }
        b.returns(ClassName(apiPackage, ctx.get("classname").toString() + "Responses", capitalize(operation.operationId) + "ApiResponse"))
        return b.build()
    }
}
