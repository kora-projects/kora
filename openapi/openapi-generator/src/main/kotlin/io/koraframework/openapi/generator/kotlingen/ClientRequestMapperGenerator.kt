package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenParameter
import org.openapitools.codegen.model.OperationsMap
import io.koraframework.openapi.generator.KoraCodegen.isContentJson


class ClientRequestMapperGenerator : AbstractKotlinGenerator<OperationsMap>() {

    companion object {
        val urlEncodedWriter = ClassName("io.koraframework.http.client.common.request.form", "FormUrlEncodedWriter")
        val base64 = ClassName("java.util", "Base64")
    }

    override fun generate(ctx: OperationsMap): FileSpec {
        val className = ClassName(apiPackage, ctx["classname"].toString() + "ClientRequestMappers")
        val b = TypeSpec.interfaceBuilder(className)
            .addAnnotation(generated())
        for (operation in ctx.operations.operation) {
            if (customBodyContentType(operation.bodyParam) != null) {
                b.addType(buildBodyParamMapper(className, operation))
            }
            if (operation.hasFormParams) {
                b.addType(buildFormMapper(ctx, className, operation))
            }
        }

        return FileSpec.get(apiPackage, b.build())
    }

    private fun buildBodyParamMapper(rootName: ClassName, operation: CodegenOperation): TypeSpec {
        val bodyParam = operation.bodyParam
        val contentType = requireNotNull(customBodyContentType(bodyParam))
        val className = rootName.nestedClass(capitalize(operation.operationId) + "BodyParamRequestMapper")
        var valueType = asType(bodyParam).asKt()
        if (!bodyParam.required) {
            valueType = valueType.copy(nullable = true)
        }
        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Classes.httpBodyOutput.asKt())
            .addParameter("value", valueType)
        if (!bodyParam.required) {
            apply.beginControlFlow("if (value == null)")
                .addStatement("return %T.empty()", Classes.httpBody.asKt())
                .endControlFlow()
        }
        if (bodyParam.isBinary) {
            apply.addStatement("return %T.of(%S, value)", Classes.httpBody.asKt(), contentType)
        } else {
            apply.addStatement("return %T.of(%S, value.toByteArray(Charsets.UTF_8))", Classes.httpBody.asKt(), contentType)
        }

        return TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(Classes.component.asKt())
            .addSuperinterface(Classes.httpClientRequestMapper.asKt().parameterizedBy(valueType.copy(nullable = false)))
            .addFunction(apply.build())
            .build()
    }

    private fun buildFormMapper(ctx: OperationsMap, rootName: ClassName, operation: CodegenOperation): TypeSpec {
        val className = rootName.nestedClass(capitalize(operation.operationId) + "FormParamRequestMapper")
        val formParamClassName = ClassName(apiPackage, ctx["classname"].toString(), capitalize(operation.operationId) + "FormParam")
        val b = TypeSpec.classBuilder(className)
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(Classes.component.asKt())
            .addModifiers(KModifier.OPEN)
            .addSuperinterface(Classes.httpClientRequestMapper.asKt().parameterizedBy(formParamClassName))
        val constructor = FunSpec.constructorBuilder()
        val apply = FunSpec.builder("apply")
            .returns(Classes.httpBodyOutput.asKt())
            .addParameter("value", formParamClassName)
            .addModifiers(KModifier.OVERRIDE)
        for (p in operation.formParams) {
            if (needsConverter(p)) {
                // an array is written element by element, so its converter is over the element type
                val valueType = if (isConvertibleArray(p)) elementType(p) else asType(p).asKt()
                val mapperType = Classes.stringParameterConverter.asKt().parameterizedBy(valueType)
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
            throw IllegalArgumentException(ambiguousFormContentTypeError(operation))
        }
        if (urlEncodedForm) {
            apply.addStatement("val b = %T()", urlEncodedWriter)
            for (formParam in operation.formParams) {
                if (formParam.required) {
                    apply.beginControlFlow("value.%N.let", formParam.paramName)
                } else {
                    apply.beginControlFlow("value.%N?.let", formParam.paramName)
                }
                if (isConvertibleArray(formParam)) {
                    // multiple values are sent as repeated same-named fields, one per element
                    apply.beginControlFlow("for (item in it)")
                    if (elementType(formParam) == String::class.asClassName()) {
                        apply.addStatement("b.add(%S, item)", formParam.baseName)
                    } else {
                        apply.addStatement("b.add(%S, %N.convert(item))", formParam.baseName, formParam.paramName + "Converter")
                    }
                    apply.endControlFlow()
                } else if (requiresMapper(formParam)) {
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
                    if (formParam.isArray) {
                        apply.addStatement("l.addAll(it)")
                    } else {
                        apply.addStatement("l.add(it)")
                    }
                } else if (isByteArrayArrayType(formParam)) {
                    apply.beginControlFlow("for (item in it)")
                        .addStatement("l.add(%T.data(%S, %T.getEncoder().encodeToString(item)))", Classes.formMultipart.asKt(), formParam.baseName, base64)
                        .endControlFlow()
                } else if (isByteArrayType(formParam)) {
                    apply.addStatement("l.add(%T.data(%S, %T.getEncoder().encodeToString(it)))", Classes.formMultipart.asKt(), formParam.baseName, base64)
                } else if (isConvertibleArray(formParam)) {
                    // multiple values are sent as repeated same-named parts, one per element
                    apply.beginControlFlow("for (item in it)")
                    if (elementType(formParam) == String::class.asClassName()) {
                        apply.addStatement("l.add(%T.data(%S, item))", Classes.formMultipart.asKt(), formParam.baseName)
                    } else {
                        apply.addStatement("l.add(%T.data(%S, %N.convert(item)))", Classes.formMultipart.asKt(), formParam.baseName, formParam.paramName + "Converter")
                    }
                    apply.endControlFlow()
                } else if (requiresMapper(formParam)) {
                    apply.addStatement("l.add(%T.data(%S, %N.convert(it)))", Classes.formMultipart.asKt(), formParam.baseName, formParam.paramName + "Converter")
                } else {
                    apply.addStatement("l.add(%T.data(%S, it.toString()))", Classes.formMultipart.asKt(), formParam.baseName)
                }
                apply.endControlFlow()
            }
            apply.addStatement("return %T.write(l)", Classes.multipartWriter.asKt())
        } else {
            throw IllegalArgumentException(missingFormContentTypeError(operation))
        }

        b.addFunction(apply.build())
        b.primaryConstructor(constructor.build())
        return b.build()
    }

    private fun isByteArrayType(p: CodegenParameter): Boolean =
        p.dataType == "byte[]" || p.dataType == "ByteArray"

    private fun isByteArrayArrayType(p: CodegenParameter): Boolean =
        p.isArray == true && (p.baseType == "byte[]" || p.baseType == "ByteArray"
            || p.dataType?.contains("byte[]") == true || p.dataType?.contains("ByteArray") == true)

    private fun requiresMapper(p: CodegenParameter): Boolean {
        if (isContentJson(p)) {
            return true
        }
        if (p.isEnum || !p.allowableValues.isNullOrEmpty()) {
            return true
        }
        return !p.isPrimitiveType
    }

    // a non-file, non-byte array whose elements are written as repeated same-named form fields
    private fun isConvertibleArray(p: CodegenParameter): Boolean =
        p.isArray == true && !p.isFile && !isByteArrayArrayType(p)

    private fun elementType(p: CodegenParameter): TypeName =
        (asType(p).asKt() as ParameterizedTypeName).typeArguments.single()

    private fun needsConverter(p: CodegenParameter): Boolean =
        if (isConvertibleArray(p)) elementType(p) != String::class.asClassName() else requiresMapper(p)

    private fun ambiguousFormContentTypeError(operation: CodegenOperation): String {
        return """
            Invalid OpenAPI operation `${operation.operationId}`: ambiguous form request body.

            Operation declares both `application/x-www-form-urlencoded` and `multipart/form-data`.
            Kora generates one form request mapper per operation and cannot choose between both encodings.

            Fix: keep exactly one supported form content type for this operation.
        """.trimIndent()
    }

    private fun missingFormContentTypeError(operation: CodegenOperation): String {
        return """
            Invalid OpenAPI operation `${operation.operationId}`: unsupported form request body.

            Operation has form parameters, but consumes neither `application/x-www-form-urlencoded` nor `multipart/form-data`.

            Fix: set requestBody content type to one supported form media type, or remove form parameters.
        """.trimIndent()
    }
}
