package io.koraframework.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenModel
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenParameter
import org.openapitools.codegen.model.OperationsMap
import java.nio.charset.StandardCharsets


class ServerRequestMappersGenerator : AbstractKotlinGenerator<OperationsMap>() {

    companion object {
        val multipartReader = ClassName("io.koraframework.http.server.common.request.form", "MultipartReaderUtils")
        val formUrlMapper = ClassName("io.koraframework.http.server.common.request.mapper", "FormUrlEncodedServerRequestMapper")
        val base64 = ClassName("java.util", "Base64")
    }

    private fun isByteArrayType(p: CodegenParameter): Boolean =
        p.dataType == "byte[]" || p.dataType == "ByteArray"

    // a url-encoded value of a binary field becomes a data FormPart, a format: byte value is base64 decoded
    private fun readUrlEncodedValue(p: CodegenParameter, value: String): CodeBlock =
        if (p.isFile) CodeBlock.of("%T.data(%S, %N)", Classes.formMultipart.asKt(), p.baseName, value)
        else CodeBlock.of("%T.getDecoder().decode(%N)", base64, value)

    // a non-file, non-byte array collected element by element into a list
    private fun isConvertibleArray(p: CodegenParameter): Boolean =
        p.isArray == true && !p.isFile && !isByteArrayArrayType(p)

    private fun isByteArrayArrayType(p: CodegenParameter): Boolean =
        p.isArray == true && (p.baseType == "byte[]" || p.baseType == "ByteArray"
            || p.dataType?.contains("byte[]") == true || p.dataType?.contains("ByteArray") == true)

    override fun generate(ctx: OperationsMap): FileSpec {
        val b = TypeSpec.interfaceBuilder(ctx.get("classname").toString() + "ServerRequestMappers")
            .addAnnotation(generated())

        for (operation in ctx.operations.operation) {
            if (operation.hasFormParams) {
                b.addType(buildFormParamsRequestMapper(ctx, operation))
            }
        }

        return FileSpec.get(apiPackage, b.build())

    }

    private fun buildFormParamsRequestMapper(ctx: OperationsMap, op: CodegenOperation): TypeSpec {
        val formParamClass = ClassName(apiPackage, ctx.get("classname").toString() + "Controller", capitalize(op.operationId) + "FormParam")
        val b = TypeSpec.classBuilder(capitalize(op.operationId) + "FormParamRequestMapper")
            .addAnnotation(generated())
            .addAnnotation(Classes.defaultComponent.asKt())
            .addAnnotation(Classes.component.asKt())
            .addModifiers(KModifier.OPEN)
            .addSuperinterface(Classes.httpServerRequestMapper.asKt().parameterizedBy(formParamClass))
        val constructor = FunSpec.constructorBuilder()
        val multipartForm = op.consumes != null && op.consumes.stream()
            .map({ m -> m["mediaType"] })
            .anyMatch { anotherString: String? -> "multipart/form-data".equals(anotherString, ignoreCase = true) }
        val urlEncodedForm = op.consumes != null && op.consumes.stream()
            .map({ m -> m["mediaType"] })
            .anyMatch { anotherString: String? -> "application/x-www-form-urlencoded".equals(anotherString, ignoreCase = true) }
        for (formParam in op.formParams) {
            val formObject = if (urlEncodedForm) explodedFormObject(op, formParam) else null
            if (formObject != null) {
                // an object of a url-encoded form is a field per property, each read with a converter of the property type
                for (property in formObject.allVars) {
                    val valueType = formObjectPropertyValueType(formObject, property)
                    if (valueType == String::class.asClassName()) {
                        continue
                    }
                    val propertyMapperType = Classes.stringParameterReader.asKt().parameterizedBy(valueType)
                    val propertyConverterName = formObjectConverterName(formParam, property)
                    b.addProperty(PropertySpec.builder(propertyConverterName, propertyMapperType).initializer(propertyConverterName).build())
                    constructor.addParameter(propertyConverterName, propertyMapperType)
                }
                if (!multipartForm) {
                    // the multipart format of the same operation still reads the object as a JSON part
                    continue
                }
            }
            val paramType = asType(formParam).asKt()
            // a string is read as is, unless it declares a JSON media type
            val plainString = !isJsonTypedFormPart(formParam)
                && (paramType == List::class.asClassName().parameterizedBy(String::class.asClassName()) || paramType == String::class.asClassName())
            if (plainString || isByteArrayType(formParam) || isByteArrayArrayType(formParam)) {
                continue
            }
            if (formParam.isFile) {
                // a binary field is a FormPart in both body formats, so it never calls a converter
                continue
            }
            val mapperType = Classes.stringParameterReader.asKt().parameterizedBy(if (formParam.isArray) (paramType as ParameterizedTypeName).typeArguments.single() else paramType)
            val converterName = formParam.paramName + "Converter"
            b.addProperty(PropertySpec.builder(converterName, mapperType).initializer(converterName).build())
            val param = ParameterSpec.builder(converterName, mapperType)
            // a JSON part uses the @Json reader, any other declared media type has a tag of its own in ApiFormPartsModule
            val tag = formPartTag(formParam)
            if (isJsonFormPart(formParam)) {
                param.addAnnotation(jsonAnnotation(AnnotationSpec.UseSiteTarget.PARAM))
            } else if (tag != null) {
                param.addAnnotation(formPartTagAnnotation(tag, AnnotationSpec.UseSiteTarget.PARAM))
            }
            constructor.addParameter(param.build())
        }
        b.primaryConstructor(constructor.build())

        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .returns(formParamClass)
            .addParameter("rq", Classes.httpServerRequest.asKt())

        if (urlEncodedForm && multipartForm) {
            // both are declared: the request content-type picks the body format
            apply.addStatement("val _contentType = rq.headers().getFirst(%S)", "content-type")
            apply.beginControlFlow("if (_contentType != null && _contentType.lowercase().startsWith(%S))", "multipart/form-data")
            apply.addCode(mapMultipart(ctx, op, formParamClass))
            apply.endControlFlow()
            apply.addCode(mapUrlEncoded(ctx, op, formParamClass))
        } else if (urlEncodedForm) {
            apply.addCode(mapUrlEncoded(ctx, op, formParamClass))
        } else if (multipartForm) {
            apply.addCode(mapMultipart(ctx, op, formParamClass))
        } else {
            throw IllegalArgumentException(missingFormContentTypeError(op))
        }


        b.addFunction(apply.build())
        return b.build()
    }

    private fun missingFormContentTypeError(operation: CodegenOperation): String {
        return """
            Invalid OpenAPI operation `${operation.operationId}`: unsupported server form request body.

            Operation has form parameters, but consumes neither `application/x-www-form-urlencoded` nor `multipart/form-data`.

            Fix: set requestBody content type to one supported form media type, or remove form parameters.
        """.trimIndent()
    }

    private fun mapMultipart(ctx: OperationsMap, op: CodegenOperation, formParamClass: ClassName): CodeBlock {
        val b = CodeBlock.builder()
        for (formParam in op.formParams) {
            if (formParam.isFile && formParam.isArray) {
                b.addStatement("val %N = mutableListOf<%T>()", formParam.paramName, Classes.formPart.asKt())
                continue
            } else if (isByteArrayArrayType(formParam)) {
                b.addStatement("val %N = mutableListOf<ByteArray>()", formParam.paramName)
                continue
            } else if (formParam.isArray) {
                val elementType = (asType(formParam).asKt() as ParameterizedTypeName).typeArguments.single()
                b.addStatement("val %N = mutableListOf<%T>()", formParam.paramName, elementType)
                continue
            }
            var type = asType(formParam).asKt()
            if (formParam.isFile) {
                type = Classes.formPart.asKt()
            }
            b.addStatement("var %N = null as %T?", formParam.paramName, type.copy(false))
        }
        b.addStatement("val _parts = %T.read(rq)", multipartReader)
        b.beginControlFlow("for (_part in _parts) when(_part.name())")
        for (formParam in op.formParams) {
            b.beginControlFlow("%S -> ", formParam.baseName)
            val type = asType(formParam).asKt()
            if (formParam.isFile && formParam.isArray) {
                b.addStatement("%N.add(_part)", formParam.paramName)
            } else if (formParam.isFile) {
                b.addStatement("%N = _part", formParam.paramName)
            } else if (isByteArrayArrayType(formParam)) {
                b.addStatement("%N.add(%T.getDecoder().decode(_part.content()))", formParam.paramName, base64)
            } else if (isByteArrayType(formParam)) {
                b.addStatement("%N = %T.getDecoder().decode(_part.content())", formParam.paramName, base64)
            } else if (formParam.isArray) {
                val elementType = (type as ParameterizedTypeName).typeArguments.single()
                if (elementType == String::class.asClassName() && !isJsonTypedFormPart(formParam)) {
                    b.addStatement("%N.add(%T(_part.content(), %T.UTF_8))", formParam.paramName, String::class.asClassName(), StandardCharsets::class.asClassName())
                } else {
                    val converterName = formParam.paramName + "Converter"
                    b.addStatement("%N.add(%N.read(%T(_part.content(), %T.UTF_8)))", formParam.paramName, converterName, String::class.asClassName(), StandardCharsets::class.asClassName())
                }
            } else if (type == String::class.asClassName() && !isJsonTypedFormPart(formParam)) {
                b.addStatement("%N = %T(_part.content(), %T.UTF_8)", formParam.paramName, String::class.asClassName(), StandardCharsets::class.asClassName())
            } else {
                val converterName = formParam.paramName + "Converter"
                b.addStatement("%N = %N.read(%T(_part.content(), %T.UTF_8))", formParam.paramName, converterName, String::class.asClassName(), StandardCharsets::class.asClassName())
            }
            b.endControlFlow()
        }
        b.add("else -> {}\n")
        b.endControlFlow()
        for (formParam in op.formParams) {
            if (formParam.required) {
                if (formParam.isArray) {
                    b.beginControlFlow("if (%N.isEmpty())", formParam.paramName)
                } else {
                    b.beginControlFlow("if (%N == null)", formParam.paramName)
                }
                b.addStatement("throw %T.of(400, %S)", Classes.httpServerResponseException.asKt(), "Form key '${formParam.baseName}' is required")
                b.endControlFlow()
            }
        }

        b.add("return %T(", formParamClass)
        for (i in 0..<op.formParams.size) {
            val formParam = op.formParams[i]
            if (i > 0) {
                b.add(", ")
            }
            if (!formParam.required && isConvertibleArray(formParam)) {
                // an absent optional array yields null rather than an empty collection
                b.add("%N.ifEmpty { null }", formParam.paramName)
            } else {
                b.add(formParam.paramName)
            }
        }
        b.add(")\n")
        return b.build()
    }

    // an object of a url-encoded form is read from a field per property, an optional object is absent when none of its fields is sent
    private fun readFormObject(p: CodegenParameter, model: CodegenModel): CodeBlock {
        val b = CodeBlock.builder()
        val objectType = asType(p).asKt().copy(nullable = false, annotations = emptyList())
        if (!p.required) {
            val present = if (model.allVars.isEmpty()) CodeBlock.of("false")
            else model.allVars.map { CodeBlock.of("_formData[%S]·!=·null", it.baseName) }.joinToCode("·||·")
            b.beginControlFlow("val %N = if (%L)", p.paramName, present)
        }
        val arguments = ArrayList<CodeBlock>()
        for (property in model.allVars) {
            val local = "_" + p.paramName + "_" + property.name
            val partName = local + "_part"
            val string = formObjectPropertyValueType(model, property) == String::class.asClassName()
            val converterName = formObjectConverterName(p, property)
            b.addStatement("val %N = _formData[%S]", partName, property.baseName)
            if (property.isArray) {
                if (string) {
                    b.addStatement("val %N = %N?.values()", local, partName)
                } else {
                    b.addStatement("val %N = %N?.values()?.map·{·this.%N.read(it)·}", local, partName, converterName)
                }
            } else if (string) {
                b.addStatement("val %N = %N?.values()?.firstOrNull()", local, partName)
            } else {
                b.addStatement("val %N = %N?.values()?.firstOrNull()?.let·{·this.%N.read(it)·}", local, partName, converterName)
            }
            if (property.required && !property.isNullable) {
                b.beginControlFlow("if (%N == null)", local)
                    .addStatement("throw %T.of(400, %S)", Classes.httpServerResponseException.asKt(), "Form key '${property.baseName}' is required")
                    .endControlFlow()
            }
            // an optional nullable property is a JsonNullable property
            arguments.add(
                if (property.isNullable && !property.required) CodeBlock.of("%N·=·if (%N == null) %T.undefined() else %T.of(%N)", property.name, local, Classes.jsonNullable.asKt(), Classes.jsonNullable.asKt(), local)
                else CodeBlock.of("%N·=·%N", property.name, local)
            )
        }
        val obj = CodeBlock.of("%T(%L)", objectType, arguments.joinToCode(", "))
        if (p.required) {
            b.addStatement("val %N = %L", p.paramName, obj)
        } else {
            b.addStatement("%L", obj)
            b.nextControlFlow("else")
            b.addStatement("null")
            b.endControlFlow()
        }
        return b.build()
    }

    private fun mapUrlEncoded(ctx: OperationsMap, op: CodegenOperation, formParamClass: ClassName): CodeBlock {
        val b = CodeBlock.builder()
        b.beginControlFlow("rq.body().use { _body ->")
        b.beginControlFlow("_body.asInputStream().use { _is ->")
        b.addStatement("val _bytes = _is.readAllBytes()")
        b.addStatement("val _bodyString = %T(_bytes, %T.UTF_8)", String::class.asClassName(), StandardCharsets::class.asClassName())
        b.addStatement("val _formData = %T.read(_bodyString)", formUrlMapper)
        for (p in op.formParams) {
            val formObject = explodedFormObject(op, p)
            if (formObject != null) {
                b.add(readFormObject(p, formObject))
                continue
            }
            val type = asType(p).asKt()
            val partName = "_" + p.paramName + "_part"
            if (p.isArray) {
                val ptn = type as ParameterizedTypeName
                b.addStatement("var %N = _formData.get(%S)", partName, p.baseName)
                if (p.required) {
                    b.beginControlFlow("if (%N == null)", partName)
                        .addStatement("throw %T.of(400, %S)", Classes.httpServerResponseException.asKt(), "Form key '${p.baseName}' is required")
                        .endControlFlow()
                }
                // an absent optional array yields null
                val call = if (p.required) "." else "?."
                if (p.isFile || isByteArrayArrayType(p)) {
                    b.addStatement("val %N = %N%Lvalues()%LasSequence()%Lmap { %L }%LtoList()", p.paramName, partName, call, call, call, readUrlEncodedValue(p, "it"), call)
                } else {
                    val delimiter = urlEncodedArrayDelimiter(p)
                    // `explode: false`: one field holds the values joined by the delimiter of the style
                    val values = if (delimiter == null) CodeBlock.of("%N%Lvalues()", partName, call)
                    else CodeBlock.of("%N%Lvalues()%LflatMap·{·it.split(%S)·}%Lfilter·{·it.isNotEmpty()·}", partName, call, call, delimiter, call)
                    if (ptn.typeArguments.single() == String::class.asClassName() && !isJsonTypedFormPart(p)) {
                        b.addStatement("val %N = %L", p.paramName, values)
                    } else {
                        val converterName = p.paramName + "Converter"
                        b.addStatement("val %N = %L%LasSequence()%Lmap(this.%N::read)%LtoList()", p.paramName, values, call, call, converterName, call)
                    }
                }
                continue
            }
            b.addStatement("val %N = _formData[%S]", partName, p.baseName)
            val plainString = type == String::class.asClassName() && !p.isFile && !isJsonTypedFormPart(p)
            val strName = if (plainString) p.paramName else "_" + p.paramName + "_str"
            b.addStatement("val %N = %N?.values()?.firstOrNull()", strName, partName)
            if (p.required) {
                b.beginControlFlow("if (%N == null)", strName)
                    .addStatement("throw %T.of(400, %S)", Classes.httpServerResponseException.asKt(), "Form key '${p.baseName}' is required")
                    .endControlFlow()
            }
            if (p.isFile || isByteArrayType(p)) {
                if (p.required) {
                    b.addStatement("val %N = %L", p.paramName, readUrlEncodedValue(p, strName))
                } else {
                    b.addStatement("val %N = %N?.let { %L }", p.paramName, strName, readUrlEncodedValue(p, "it"))
                }
            } else if (!plainString) {
                val converterName = p.paramName + "Converter"
                if (p.required) {
                    b.addStatement("val %N = %N.read(%N)", p.paramName, converterName, strName)
                    b.beginControlFlow("if (%N == null)", p.paramName)
                        .addStatement("throw %T.of(400, %S)", Classes.httpServerResponseException.asKt(), "Form key '${p.baseName}' is required")
                        .endControlFlow()
                } else {
                    // an absent optional field yields null instead of reaching the converter
                    b.addStatement("val %N = %N?.let { %N.read(it) }", p.paramName, strName, converterName)
                }
            }
        }
        b.add("return %T(", formParamClass)
        for (i in 0..<op.formParams.size) {
            if (i > 0) {
                b.add(", ")
            }
            b.add(op.formParams[i].paramName)
        }
        b.add(")\n")
        b.endControlFlow()
        b.endControlFlow()
        return b.build()

    }
}
