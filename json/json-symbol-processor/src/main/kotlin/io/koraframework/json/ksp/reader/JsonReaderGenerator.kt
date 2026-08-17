package io.koraframework.json.ksp.reader

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import io.koraframework.json.ksp.JsonTypes
import io.koraframework.json.ksp.KnownType.KnownTypesEnum
import io.koraframework.json.ksp.KnownType.KnownTypesEnum.*
import io.koraframework.json.ksp.jsonReaderName
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.toTypeName
import java.util.*

class JsonReaderGenerator(val resolver: Resolver) {
    companion object {
        private const val maxFields: Int = 31
    }

    fun generate(meta: JsonClassReaderMeta): TypeSpec {
        return generateForClass(meta)
    }

    private fun generateForClass(meta: JsonClassReaderMeta): TypeSpec {
        val declaration = meta.classDeclaration
        val typeName = declaration.toTypeName()
        val typeParameterResolver = declaration.typeParameters.toTypeParameterResolver()
        val readerInterface = JsonTypes.jsonReader.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(declaration.jsonReaderName())
            .generated(JsonReaderGenerator::class)
            .addOriginatingKSFile(declaration)

        typeBuilder.addSuperinterface(readerInterface)

        declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver))
        }

        this.addBitSet(typeBuilder, meta)
        this.addReaders(typeBuilder, meta, typeParameterResolver)
        this.addFieldNames(typeBuilder, meta)
        this.addReadMethods(typeBuilder, meta)
        this.addErrorMethods(typeBuilder, meta)
        val functionBody = CodeBlock.builder()
        functionBody.addStatement("var __token = __parser.currentToken()")
        functionBody.controlFlow("if (__token == %T.VALUE_NULL) ", JsonTypes.jsonToken) {
            if (JsonTypes.jsonNullable == declaration.toClassName()) {
                addStatement("return %T.nullValue()", JsonTypes.jsonNullable)
            } else {
                addStatement("return null")
            }
        }
        assertTokenType(functionBody, "START_OBJECT", "an object '{...}'")
        functionBody.add("\n")
        if (meta.fields.size <= maxFields) {
            functionBody.addStatement("var __receivedFields = NULLABLE_FIELDS_RECEIVED")
        } else {
            functionBody.addStatement("val __receivedFields = NULLABLE_FIELDS_RECEIVED.clone() as %T", BitSet::class.java)
        }
        functionBody.add("\n")

        this.addFieldVariables(functionBody, meta)
        functionBody.add("\n")
        this.addFastPath(functionBody, meta)

        if (meta.fields.isEmpty()) {
            functionBody.addStatement("__token = __parser.nextToken()")
        } else {
            functionBody.addStatement("__token = __parser.currentToken()")
        }
        functionBody.controlFlow("while (__token != %T.END_OBJECT) ", JsonTypes.jsonToken) {
            assertTokenType(functionBody, "PROPERTY_NAME", "a field name")
            functionBody.addStatement("val __fieldName = __parser.currentName()")
            functionBody.controlFlow("when (__fieldName)") {
                meta.fields.forEachIndexed { i, field ->
                    functionBody.controlFlow("%S ->", field.jsonName) {
                        addStatement("%N = %N(__parser)", field.parameter.name!!.asString(), readerMethodName(field))
                        add(markReceived(meta, i))
                    }
                }
                functionBody.controlFlow("else -> ") {
                    addStatement("__parser.nextToken()")
                    addStatement("__parser.skipChildren()")
                }
            }
            functionBody.addStatement("__token = __parser.nextToken()")
        }

        val errorSwitch = CodeBlock.builder()
            .controlFlow("when (__i)") {
                for (i in 0 until meta.fields.size) {
                    val field = meta.fields[i]
                    addStatement("%L -> %S", i, field.jsonName)
                }
                addStatement("else -> \"\"")
            }
        if (meta.fields.size > maxFields) {
            functionBody.controlFlow("if (__receivedFields != ALL_FIELDS_RECEIVED)") {
                addStatement(" __receivedFields.flip(0, %L)", meta.fields.size)
                addStatement("val __missing = %T()", StringBuilder::class)

                addStatement("var __i = __receivedFields.nextSetBit(0)")
                controlFlow("while (__i >= 0)") {
                    addStatement("if (__missing.isNotEmpty()) __missing.append(\", \")")
                    add("__missing.append(\n")
                    indent()
                    add(errorSwitch.build())
                    unindent()
                    add(")\n")
                    add("__i = __receivedFields.nextSetBit(__i + 1)\n")
                }
                addStatement("throw __missingRequiredFields(__parser, __missing.toString())")
            }
        } else {
            functionBody.controlFlow("if (__receivedFields != ALL_FIELDS_RECEIVED)") {
                addStatement("val _nonReceivedFields = __receivedFields.inv() and ALL_FIELDS_RECEIVED")
                addStatement("val __missing = %T()", StringBuilder::class)
                controlFlow("(0..%L).forEach { __i ->", meta.fields.size) {
                    controlFlow("if ((_nonReceivedFields and (1 shl __i)) != 0)") {
                        addStatement("if (__missing.isNotEmpty()) __missing.append(\", \")")
                        add("__missing.append(\n")
                        indent()
                        add(errorSwitch.build())
                        unindent()
                        add(")\n")
                    }
                }
                addStatement("throw __missingRequiredFields(__parser, __missing.toString())")
            }
        }
        generateReturnResult(meta, functionBody)

        typeBuilder.addFunction(
            FunSpec.builder("read")
                .addParameter("__parser", JsonTypes.jsonParser)
                .returns(typeName.copy(nullable = true))
                .addModifiers(KModifier.OVERRIDE)
                .addCode(functionBody.build())
                .build()
        )
        return typeBuilder.build()
    }

    private fun generateReturnResult(meta: JsonClassReaderMeta, functionBody: CodeBlock.Builder) {
        functionBody.add("return %T(\n", meta.classDeclaration.toClassName()).indent()
        for (i in 0 until meta.fields.size) {
            val field = meta.fields[i]
            val type = field.type
            val paramName = field.parameter.name!!.asString()

            when {
                type.isNullable -> functionBody.add("%N", paramName)
                type == resolver.builtIns.booleanType -> functionBody.add("%N", paramName)
                type == resolver.builtIns.shortType -> functionBody.add("%N", paramName)
                type == resolver.builtIns.intType -> functionBody.add("%N", paramName)
                type == resolver.builtIns.longType -> functionBody.add("%N", paramName)
                type == resolver.builtIns.floatType -> functionBody.add("%N", paramName)
                type == resolver.builtIns.doubleType -> functionBody.add("%N", paramName)
                else -> {
                    if (field.typeMeta.isJsonNullable) {
                        functionBody.add("%N", paramName)
                    } else {
                        functionBody.add("%N!!", paramName)
                    }
                }
            }

            functionBody.add(",\n")
        }
        functionBody.unindent().add(")\n")
    }

    private fun readerFieldName(field: JsonClassReaderMeta.FieldMeta): String {
        return field.parameter.name!!.asString() + "Reader"
    }

    private fun assertTokenType(method: CodeBlock.Builder, expectedToken: String, expectedPhrase: String) {
        method.controlFlow("if (__token != %T.%L)", JsonTypes.jsonToken, expectedToken) {
            addStatement("throw __unexpectedToken(__parser, %S, %S)", "", expectedPhrase)
        }
    }

    private fun markReceived(meta: JsonClassReaderMeta, index: Int): CodeBlock {
        return if (meta.fields.size > maxFields) {
            CodeBlock.of("__receivedFields.set(%L)\n", index)
        } else {
            CodeBlock.of("__receivedFields = __receivedFields or (1 shl %L)\n", index)
        }
    }

    private fun addFieldVariables(method: CodeBlock.Builder, meta: JsonClassReaderMeta) {
        for (i in meta.fields.indices) {
            val field = meta.fields[i]
            val type = field.type
            val paramName = field.parameter.name!!.asString()

            when {
                field.typeMeta.isJsonNullable -> {
                    if (type.isNullable) {
                        method.addStatement("var %N: %T = %T.undefined()", paramName, field.type.copy(nullable = true), JsonTypes.jsonNullable)
                    } else {
                        method.addStatement("var %N: %T = %T.undefined()", paramName, field.type, JsonTypes.jsonNullable)
                    }
                }

                type.isNullable -> method.addStatement("var %N: %T = null", paramName, field.type)
                type == resolver.builtIns.booleanType -> method.addStatement("var %N = false", paramName)
                type == resolver.builtIns.shortType -> method.addStatement("var %N: Short = 0", paramName)
                type == resolver.builtIns.intType -> method.addStatement("var %N = 0", paramName)
                type == resolver.builtIns.longType -> method.addStatement("var %N = 0L", paramName)
                type == resolver.builtIns.floatType -> method.addStatement("var %N = 0f", paramName)
                type == resolver.builtIns.doubleType -> method.addStatement("var %N = 0.0", paramName)
                else -> method.addStatement("var %N: %T = null", paramName, field.type.copy(nullable = true))
            }
        }
    }

    private fun addReaders(typeBuilder: TypeSpec.Builder, classMeta: JsonClassReaderMeta, typeParameterResolver: TypeParameterResolver) {
        val constructor = FunSpec.constructorBuilder()
        for (field in classMeta.fields) {
            if (field.reader == null && field.typeMeta is ReaderFieldType.KnownTypeReaderMeta) {
                continue
            }
            val fieldName = this.readerFieldName(field)
            if (field.reader != null) {
                val mapperType = field.reader.mapper
                val fieldType: TypeName
                if (mapperType != null) {
                    fieldType = mapperType.toTypeName(typeParameterResolver)
                    val readerProp = PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE)
                    val readerDecl = mapperType.declaration as KSClassDeclaration
                    if (!readerDecl.modifiers.contains(Modifier.OPEN)) {
                        val constructors = readerDecl.getConstructors().toList()
                        if (constructors.size == 1) {
                            readerProp.initializer("%T()", mapperType.toTypeName(typeParameterResolver))
                            typeBuilder.addProperty(readerProp.build())
                            continue
                        }
                    }
                } else {
                    fieldType = JsonTypes.jsonWriter.parameterizedBy(field.typeMeta.type.toTypeName(typeParameterResolver))
                }
                val readerProp = PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE)
                    .tag(field.reader.tag)
                typeBuilder.addProperty(readerProp.build())
                constructor.addParameter(fieldName, fieldType)
                constructor.addStatement("this.%L = %L", fieldName, fieldName)
            } else if (field.typeMeta is ReaderFieldType.UnknownTypeReaderMeta) {
                val fieldType = JsonTypes.jsonReader.parameterizedBy(field.typeMeta.typeName.copy(nullable = false))
                val readerField = PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE)
                constructor.addParameter(fieldName, fieldType)
                constructor.addStatement("this.%L = %L", fieldName, fieldName)
                typeBuilder.addProperty(readerField.build())
            }
        }
        typeBuilder.primaryConstructor(constructor.build())
    }

    private fun addFastPath(functionBody: CodeBlock.Builder, meta: JsonClassReaderMeta) {
        functionBody.controlFlow("run") {
            for (i in meta.fields.indices) {
                val field: JsonClassReaderMeta.FieldMeta = meta.fields[i]
                addStatement("if (!__parser.nextName(%N)) return@run", jsonNameStaticName(field))
                addStatement("%N = %N(__parser)", field.parameter.name!!.asString(), readerMethodName(field))
                add(markReceived(meta, i))
                functionBody.add("\n")
            }

            functionBody.addStatement("__token = __parser.nextToken()")
            functionBody.controlFlow("while (__token != %T.END_OBJECT)", JsonTypes.jsonToken) {
                addStatement("__parser.nextToken()")
                addStatement("__parser.skipChildren()")
                addStatement("__token = __parser.nextToken()")
            }
            generateReturnResult(meta, functionBody)
        }

    }

    private fun addFieldNames(typeBuilder: TypeSpec.Builder, meta: JsonClassReaderMeta) {
        for (field in meta.fields) {
            typeBuilder.addProperty(
                PropertySpec.builder(
                    this.jsonNameStaticName(field),
                    JsonTypes.serializedString,
                    KModifier.PRIVATE
                )
                    .initializer(CodeBlock.of(" %T(%S)", JsonTypes.serializedString, field.jsonName))
                    .build()
            )
        }
    }

    private fun addReadMethods(typeBuilder: TypeSpec.Builder, meta: JsonClassReaderMeta) {
        val fields: List<JsonClassReaderMeta.FieldMeta> = meta.fields
        for (i in fields.indices) {
            typeBuilder.addFunction(this.readParamFunction(i, fields.size, fields[i]))
        }
    }

    private fun jsonNameStaticName(field: JsonClassReaderMeta.FieldMeta): String {
        return "_" + field.parameter.name!!.asString() + "_optimized_field_name"
    }

    private fun readParamFunction(index: Int, size: Int, field: JsonClassReaderMeta.FieldMeta): FunSpec {
        val function = FunSpec.builder(readerMethodName(field))
            .addModifiers(KModifier.PRIVATE)
            .addParameter("__parser", JsonTypes.jsonParser)
            .returns(field.type)

        val functionBody = CodeBlock.builder()
        val isMarkedNullable = field.parameter.type.resolve().isMarkedNullable

        if (field.reader != null) {
            functionBody.add("val __token = __parser.nextToken()\n")
            if (field.typeMeta.isJsonNullable || !isMarkedNullable) {
                functionBody.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                    addStatement("throw __requiredFieldNull(__parser, %S)", ".${field.jsonName}")
                }
            }
            functionBody.add("return %L.read(__parser)\n", this.readerFieldName(field))

            return function.addCode(functionBody.build()).build()
        }

        functionBody.addStatement("val __token = __parser.nextToken()\n")
        if (field.typeMeta is ReaderFieldType.KnownTypeReaderMeta) {
            functionBody.add(readKnownType(field.jsonName, field.typeMeta.knownType, isMarkedNullable, field.typeMeta.isJsonNullable))

            return function.addCode(functionBody.build()).build()
        }

        if (field.typeMeta.isJsonNullable) {
            functionBody.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                addStatement("return %T.nullValue()", JsonTypes.jsonNullable)
            }
        } else if (field.type.isNullable) {
            functionBody.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                addStatement("return null")
            }
        } else {
            functionBody.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                addStatement("throw __requiredFieldNull(__parser, %S)", ".${field.jsonName}")
            }
        }

        if (field.typeMeta.isJsonNullable) {
            functionBody.addStatement("return %T.ofNullable(%L.read(__parser))", JsonTypes.jsonNullable, readerFieldName(field))
        } else {
            val exceptionBlock = if (isMarkedNullable) CodeBlock.of("") else CodeBlock.of(
                " ?: throw __requiredFieldNull(__parser, %S)",
                ".${field.jsonName}"
            )
            functionBody.addStatement("return %L.read(__parser)%L", readerFieldName(field), exceptionBlock)
        }
        return function.addCode(functionBody.build()).build()
    }

    private fun readKnownType(jsonName: String, knownType: KnownTypesEnum, isNullable: Boolean, isJsonNullable: Boolean): CodeBlock {
        val method = CodeBlock.builder()
        when (knownType) {
            KnownTypesEnum.STRING -> method.controlFlow("if (__token == %T.VALUE_STRING)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.text)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.text")
                }
            }

            KnownTypesEnum.BOOLEAN -> {
                method.controlFlow("if (__token == %T.VALUE_TRUE)", JsonTypes.jsonToken) {
                    if (isJsonNullable) {
                        addStatement("return %T.of(true)", JsonTypes.jsonNullable)
                    } else {
                        addStatement("return true")
                    }
                }
                method.controlFlow("if (__token == %T.VALUE_FALSE)", JsonTypes.jsonToken) {
                    if (isJsonNullable) {
                        addStatement("return %T.of(false)", JsonTypes.jsonNullable)
                    } else {
                        addStatement("return false")
                    }
                }
            }

            INTEGER -> method.controlFlow("if (__token == %T.VALUE_NUMBER_INT)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.intValue)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.intValue")
                }
            }

            BIG_INTEGER -> method.controlFlow("if (__token == %T.VALUE_NUMBER_INT)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.bigIntegerValue)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.bigIntegerValue")
                }
            }

            KnownTypesEnum.DOUBLE -> method.controlFlow("if (__token == %1T.VALUE_NUMBER_FLOAT || __token == %1T.VALUE_NUMBER_INT)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.doubleValue)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.doubleValue")
                }
            }

            KnownTypesEnum.FLOAT -> method.controlFlow("if (__token == %1T.VALUE_NUMBER_FLOAT || __token == %1T.VALUE_NUMBER_INT)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.floatValue)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.floatValue")
                }
            }

            KnownTypesEnum.LONG -> method.controlFlow("if (__token == %T.VALUE_NUMBER_INT)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.longValue)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.longValue")
                }
            }

            KnownTypesEnum.SHORT -> method.controlFlow("if (__token == %T.VALUE_NUMBER_INT)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.shortValue)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.shortValue")
                }
            }

            BINARY -> method.controlFlow("if (__token == %T.VALUE_STRING)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.binaryValue)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.binaryValue")
                }
            }

            KnownTypesEnum.UUID -> method.controlFlow("if (__token == %T.VALUE_STRING)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(%T.fromString(__parser.text))", JsonTypes.jsonNullable, java.util.UUID::class)
                } else {
                    addStatement("return %T.fromString(__parser.text)", java.util.UUID::class)
                }
            }
        }

        if (isJsonNullable) {
            method.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                addStatement("return %T.nullValue()", JsonTypes.jsonNullable)
            }
        } else if (isNullable) {
            method.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                addStatement("return null")
            }
        } else {
            method.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                addStatement("throw __requiredFieldNull(__parser, %S)", ".$jsonName")
            }
        }

        method.addStatement(
            "throw __unexpectedToken(__parser, %S, %S)",
            ".$jsonName",
            expectedPhrase(knownType)
        )
        return method.build()
    }

    private fun expectedPhrase(knownType: KnownTypesEnum): String {
        return when (knownType) {
            KnownTypesEnum.STRING -> "a string"
            BINARY -> "a base64-encoded string"
            KnownTypesEnum.UUID -> "a UUID string"
            KnownTypesEnum.BOOLEAN -> "a boolean"
            KnownTypesEnum.SHORT, INTEGER, KnownTypesEnum.LONG, BIG_INTEGER -> "an integer number"
            KnownTypesEnum.DOUBLE, KnownTypesEnum.FLOAT -> "a number"
        }
    }

    /**
     * Generates the private helper functions each reader uses to build detailed, consistent parse-error
     * messages (type + member + JSON path + humanized expected/actual value). Kept inside the mapper
     * itself, not extracted to a shared runtime class.
     */
    private fun addErrorMethods(typeBuilder: TypeSpec.Builder, meta: JsonClassReaderMeta) {
        val typeName = meta.classDeclaration.simpleName.asString()

        typeBuilder.addFunction(
            FunSpec.builder("__jsonPath")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("__parser", JsonTypes.jsonParser)
                .returns(String::class)
                .addStatement("val __p = __parser.streamReadContext().pathAsPointer().toString()")
                .addStatement("return if (__p.isEmpty()) %S else __p", "<root>")
                .build()
        )

        val actual = FunSpec.builder("__actualValue")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("__parser", JsonTypes.jsonParser)
            .returns(String::class)
        actual.addStatement("val __t = __parser.currentToken() ?: return %S", "nothing (end of input)")
        actual.addStatement("var __v = __parser.text")
        actual.addStatement("if (__v != null && __v.length > 128) __v = __v.substring(0, 128) + %S", "...(truncated)")
        actual.beginControlFlow("return when (__t)")
        actual.addStatement("%T.VALUE_NULL -> %S", JsonTypes.jsonToken, "null")
        actual.addStatement("%T.START_OBJECT -> %S", JsonTypes.jsonToken, "an object")
        actual.addStatement("%T.START_ARRAY -> %S", JsonTypes.jsonToken, "an array")
        actual.addStatement("%T.VALUE_STRING -> %S + __v + %S", JsonTypes.jsonToken, "a string \"", "\"")
        actual.addStatement("%T.VALUE_NUMBER_INT -> %S + __v", JsonTypes.jsonToken, "a number ")
        actual.addStatement("%T.VALUE_NUMBER_FLOAT -> %S + __v", JsonTypes.jsonToken, "a fractional number ")
        actual.addStatement("%1T.VALUE_TRUE, %1T.VALUE_FALSE -> %2S + __v", JsonTypes.jsonToken, "a boolean ")
        actual.addStatement("else -> %S + __t", "token ")
        actual.endControlFlow()
        typeBuilder.addFunction(actual.build())

        typeBuilder.addFunction(
            FunSpec.builder("__unexpectedToken")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("__parser", JsonTypes.jsonParser)
                .addParameter("__member", String::class)
                .addParameter("__expected", String::class)
                .returns(JsonTypes.jsonParseException)
                .addStatement(
                    "return %T(__parser, %S + __member + %S + __expected + %S + __actualValue(__parser) + %S + __jsonPath(__parser) + %S)",
                    JsonTypes.jsonParseException, "Failed to read json $typeName", ": expected ", ", but got ", " (at ", ")"
                )
                .build()
        )

        typeBuilder.addFunction(
            FunSpec.builder("__missingRequiredFields")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("__parser", JsonTypes.jsonParser)
                .addParameter("__fields", String::class)
                .returns(JsonTypes.jsonParseException)
                .addStatement(
                    "return %T(__parser, %S + __fields + %S + __jsonPath(__parser) + %S)",
                    JsonTypes.jsonParseException, "Failed to read json $typeName: missing required field(s): ", " (at ", ")"
                )
                .build()
        )

        val anyRequired = meta.fields.any { !(it.parameter.type.resolve().isMarkedNullable || it.typeMeta.isJsonNullable) }
        if (anyRequired) {
            typeBuilder.addFunction(
                FunSpec.builder("__requiredFieldNull")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("__parser", JsonTypes.jsonParser)
                    .addParameter("__member", String::class)
                    .returns(JsonTypes.jsonParseException)
                    .addStatement(
                        "return %T(__parser, %S + __member + %S + __jsonPath(__parser) + %S)",
                        JsonTypes.jsonParseException, "Failed to read json $typeName", ": required field must not be null (at ", ")"
                    )
                    .build()
            )
        }
    }

    private fun readerMethodName(field: JsonClassReaderMeta.FieldMeta): String {
        return "read_" + field.parameter.name!!.asString()
    }

    private fun addBitSet(typeBuilder: TypeSpec.Builder, meta: JsonClassReaderMeta) {
        if (meta.fields.size <= maxFields) {
            val sb = StringBuilder()
            for (i in meta.fields.size - 1 downTo 0) {
                val f = meta.fields[i]
                val nullable = f.parameter.type.resolve().isMarkedNullable || f.typeMeta.isJsonNullable
                sb.append(if (nullable) "1" else "0")
            }
            val nullableFieldsReceived = if (meta.fields.isEmpty()) "0" else "0b$sb"
            val allFieldsReceived = if (meta.fields.isEmpty()) "0" else "0b" + "1".repeat(meta.fields.size)
            typeBuilder
                .addProperty(
                    PropertySpec.builder("ALL_FIELDS_RECEIVED", Int::class)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer(CodeBlock.of(allFieldsReceived))
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("NULLABLE_FIELDS_RECEIVED", Int::class)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer(CodeBlock.of(nullableFieldsReceived))
                        .build()
                )
        } else {
            typeBuilder
                .addProperty("ALL_FIELDS_RECEIVED", BitSet::class, KModifier.PRIVATE)
                .addProperty("NULLABLE_FIELDS_RECEIVED", BitSet::class, KModifier.PRIVATE)

            val fieldReceivedInitBlock = CodeBlock.builder()
                .addStatement("ALL_FIELDS_RECEIVED = %T(%L)", BitSet::class, meta.fields.size)
                .addStatement("ALL_FIELDS_RECEIVED.set(0, %L)", meta.fields.size)
                .addStatement("NULLABLE_FIELDS_RECEIVED = %T(%L)", BitSet::class.java, meta.fields.size)

            for (i in 0 until meta.fields.size) {
                val field = meta.fields[i]
                val nullable = field.parameter.type.resolve().isMarkedNullable
                if (nullable) {
                    fieldReceivedInitBlock.addStatement("NULLABLE_FIELDS_RECEIVED.set(%L)", i)
                }
            }
            typeBuilder.addInitializerBlock(fieldReceivedInitBlock.build())
        }
    }
}
