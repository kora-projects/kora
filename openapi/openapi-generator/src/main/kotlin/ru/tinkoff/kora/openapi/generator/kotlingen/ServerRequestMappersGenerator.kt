package ru.tinkoff.kora.openapi.generator.kotlingen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.model.OperationsMap
import ru.tinkoff.kora.openapi.generator.KoraCodegen
import java.nio.charset.StandardCharsets


class ServerRequestMappersGenerator : AbstractKotlinGenerator<OperationsMap>() {
    override fun generate(ctx: OperationsMap): FileSpec {
        val b = TypeSpec.interfaceBuilder(ctx.get("classname").toString() + "ServerRequestMappers")
            .addAnnotation(generated())
            .addAnnotation(Classes.module.asKt())

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
            .addSuperinterface(Classes.httpServerRequestMapper.asKt().parameterizedBy(formParamClass))
        val constructor = FunSpec.constructorBuilder()
        val multipartForm = op.consumes != null && op.consumes.stream()
            .map({ m -> m["mediaType"] })
            .anyMatch { anotherString: String? -> "multipart/form-data".equals(anotherString, ignoreCase = true) }
        val urlEncodedForm = op.consumes != null && op.consumes.stream()
            .map({ m -> m["mediaType"] })
            .anyMatch { anotherString: String? -> "application/x-www-form-urlencoded".equals(anotherString, ignoreCase = true) }

        for (formParam in op.formParams) {
            val paramType = asType(formParam).asKt()
            if (paramType == List::class.asClassName().parameterizedBy(String::class.asClassName()) || paramType == String::class.asClassName()) {
                continue
            }
            val mapperType = Classes.stringParameterReader.asKt().parameterizedBy(if (formParam.isArray) (paramType as ParameterizedTypeName).typeArguments.single() else paramType)
            val converterName = formParam.paramName + "Converter"
            b.addProperty(PropertySpec.builder(converterName, mapperType).initializer(converterName).build())
            val param = ParameterSpec.builder(converterName, mapperType)
            if (KoraCodegen.isContentJson(formParam)) {
                param.addAnnotation(Classes.json.asKt())
            }
            constructor.addParameter(param.build())
        }
        b.primaryConstructor(constructor.build())

        val apply = FunSpec.builder("apply")
            .addModifiers(KModifier.OVERRIDE)
            .returns(formParamClass)
            .addParameter("rq", Classes.httpServerRequest.asKt())

        if (urlEncodedForm) {
            apply.addCode(mapUrlEncoded(ctx, op, formParamClass))
        } else if (multipartForm) {
            apply.addCode(mapMultipart(ctx, op, formParamClass))
        } else {
            throw IllegalArgumentException()
        }


        b.addFunction(apply.build())
        return b.build()
    }

    private fun mapMultipart(ctx: OperationsMap, op: CodegenOperation, formParamClass: ClassName): CodeBlock {
        val b = CodeBlock.builder()
        for (formParam in op.formParams) {
            var type = asType(formParam).asKt()
            if (formParam.isFile) {
                type = Classes.formPart.asKt()
            }
            b.addStatement("var %N = null as %T?", formParam.paramName, type.copy(false))
        }
        b.addStatement("val _parts = %T.read(rq)", ClassName("ru.tinkoff.kora.http.server.common.form", "MultipartReader"))
        b.beginControlFlow("for (_part in _parts) when(_part.name())")
        for (formParam in op.formParams) {
            b.beginControlFlow("%S -> ", formParam.baseName)
            val type = asType(formParam).asKt()
            if (formParam.isFile) {
                b.addStatement("%N = _part", formParam.paramName)
            } else if (type == String::class.asClassName()) {
                b.addStatement("%N = %T(_part.content(), %T.UTF_8)", formParam.paramName, String::class.asClassName(), StandardCharsets::class.asClassName())
            } else {
                val converterName = formParam.paramName + "Converter"
                b.addStatement("%N = %T.read(%T(_part.content(), %T.UTF_8))", formParam.paramName, converterName, String::class.asClassName(), StandardCharsets::class.asClassName())
            }
            b.endControlFlow()
        }
        b.add("else -> {}\n")
        b.endControlFlow()
        for (formParam in op.formParams) {
            if (formParam.required) {
                b.beginControlFlow("if (%N == null)", formParam.paramName)
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
            b.add(formParam.paramName)
        }
        b.add(")\n")
        return b.build()
    }

    private fun mapUrlEncoded(ctx: OperationsMap, op: CodegenOperation, formParamClass: ClassName): CodeBlock {
        val b = CodeBlock.builder()
        b.beginControlFlow("rq.body().use { _body ->")
        b.beginControlFlow("_body.asInputStream().use { _is ->")
        b.addStatement("val _bytes = _is.readAllBytes()")
        b.addStatement("val _bodyString = %T(_bytes, %T.UTF_8)", String::class.asClassName(), StandardCharsets::class.asClassName())
        b.addStatement("val _formData = %T.read(_bodyString)", ClassName("ru.tinkoff.kora.http.server.common.form", "FormUrlEncodedServerRequestMapper"))
        for (p in op.formParams) {
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
                if (ptn.typeArguments.single() == String::class.asClassName()) {
                    b.addStatement("val %N = %N.values()", p.paramName, partName)
                } else {
                    val converterName = p.paramName + "Converter"
                    b.addStatement("val %N = %N.values().asSequence().map(this.%N::read).toList()", p.paramName, partName, converterName)
                }
                continue
            }
            b.addStatement("val %N = _formData[%S]", partName, p.baseName)
            val strName = if (type == String::class.asClassName()) p.paramName else "_" + p.paramName + "_str"
            b.addStatement("val %N = %N?.values()?.firstOrNull()", strName, partName)
            if (p.required) {
                b.beginControlFlow("if (%N == null)", strName)
                    .addStatement("throw %T.of(400, %S)", Classes.httpServerResponseException.asKt(), "Form key '${p.baseName}' is required")
                    .endControlFlow()
            }
            if (type != String::class.asClassName()) {
                val converterName = p.paramName + "Converter"
                b.addStatement("val %N = %N.read(%N)", p.paramName, converterName, strName)
                if (p.required) {
                    b.beginControlFlow("if (%N == null)", p.paramName)
                        .addStatement("throw %T.of(400, %S)", Classes.httpServerResponseException.asKt(), "Form key '${p.baseName}' is required")
                        .endControlFlow()
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
