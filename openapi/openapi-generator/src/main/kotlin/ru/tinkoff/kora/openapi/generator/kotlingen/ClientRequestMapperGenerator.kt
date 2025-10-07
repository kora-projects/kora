package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenParameter
import org.openapitools.codegen.model.OperationsMap
import ru.tinkoff.kora.openapi.generator.KoraCodegen.isContentJson


class ClientRequestMapperGenerator : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val className = ClassName(apiPackage, ctx["classname"].toString() + "ClientRequestMappers")
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
        for (operation in ctx.operations.operation) {
            if (operation.hasFormParams) {
                b.addType(buildFormMapper(ctx, className, operation))
            }
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun buildFormMapper(ctx: OperationsMap, rootName: ClassName, operation: CodegenOperation): TypeSpec {
        val className = rootName.nestedClass(capitalize(operation.operationId) + "FormParamRequestMapper")
        val formParamClassName = ClassName(apiPackage, ctx["classname"].toString(), capitalize(operation.operationId) + "FormParam")
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addSuperinterface(Classes.httpClientRequestMapper.asKt().parameterizedBy(formParamClassName))
        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .returns(Classes.httpBodyOutput.asKt())
            .addParameter("ctx", Classes.context.asKt())
            .addParameter("value", formParamClassName)
            .addModifiers(KModifier.OVERRIDE)
        for (p in operation.formParams) {
            if (requiresMapper(p)) {
                val mapperType = Classes.stringParameterConverter.asKt().parameterizedBy(asType(p).asKt())
                val mapperName = p.paramName + "Converter"
                constructor.addParameter(mapperName, mapperType)
                b.addProperty(PropertySpec.builder(mapperName, mapperType).initializer(mapperName).build())
            }
        }
        val urlEncodedForm = operation.consumes != null && operation.consumes.asSequence()
            .map { m -> m["mediaType"] }
            .any { anotherString: String? -> "application/x-www-form-urlencoded".equals(anotherString, ignoreCase = true) }
        val multipartForm = operation.consumes != null && operation.consumes.asSequence()
            .map { m -> m["mediaType"] }
            .any { anotherString: String? -> "multipart/form-data".equals(anotherString, ignoreCase = true) }
        if (urlEncodedForm && multipartForm) {
            throw IllegalArgumentException("Unsupported form type: $operation")
        }
        if (urlEncodedForm) {
            apply.addStatement("val b = %T()", ClassName("ru.tinkoff.kora.http.client.common.form", "UrlEncodedWriter"))
            for (formParam in operation.formParams) {
                if (formParam.required) {
                    apply.beginControlFlow("value.%N.let", formParam.paramName)
                } else {
                    apply.beginControlFlow("value.%N?.let", formParam.paramName)
                }
                if (requiresMapper(formParam)) {
                    apply.addStatement("b.add(%S, %N.convert(it))", formParam.baseName, formParam.paramName + "Converter")
                } else {
                    apply.addStatement("b.add(%S, it.toString())", formParam.baseName)
                }
                apply.endControlFlow()
            }
            apply.addStatement("return b.write()")
        } else if (multipartForm) {
            apply.addStatement("val l = arrayListOf<%T>()", Classes.formPart.asKt())
            for (formParam in operation.formParams) {
                if (formParam.required) {
                    apply.beginControlFlow("value.%N.let", formParam.paramName)
                } else {
                    apply.beginControlFlow("value.%N?.let", formParam.paramName)
                }
                if (formParam.isFile) {
                    apply.addStatement("l.add(it)")
                } else if (requiresMapper(formParam)) {
                    apply.addStatement("l.add(%T.data(%S, %N.convert(it)))", Classes.formMultipart.asKt(), formParam.baseName, formParam.paramName + "Converter")
                } else {
                    apply.addStatement("l.add(%T.data(%S, it.toString()))", Classes.formMultipart.asKt(), formParam.baseName)
                }
                apply.endControlFlow()
            }
            apply.addStatement("return %T.write(l)", Classes.multipartWriter.asKt())
        } else {
            throw IllegalArgumentException("Unsupported form type: $operation")
        }

        b.addFunction(apply.build())
        b.primaryConstructor(constructor.build())
        return b.build()
    }

    private fun requiresMapper(p: CodegenParameter): Boolean {
        if (isContentJson(p)) {
            return true
        }
        if (p.isEnum || !p.allowableValues.isNullOrEmpty()) {
            return true
        }
        return !p.isPrimitiveType
    }

}
