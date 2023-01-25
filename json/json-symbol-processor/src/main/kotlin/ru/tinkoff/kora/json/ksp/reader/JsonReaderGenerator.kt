package ru.tinkoff.kora.json.ksp.reader

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.io.SerializedString
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.json.common.EnumJsonReader
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum.*
import ru.tinkoff.kora.json.ksp.jsonReaderName
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.UUID

class JsonReaderGenerator(val resolver: Resolver) {
    companion object {
        private const val maxFields: Int = 31
    }

    private val readerErasureDeclaration = resolver.getClassDeclarationByName(JsonReader::class.qualifiedName!!.toString())!!.asStarProjectedType()
    private val enumType = resolver.getClassDeclarationByName<Enum<*>>()!!.asStarProjectedType()

    fun generate(meta: JsonClassReaderMeta): TypeSpec {
        return generateForClass(meta)
    }

    private fun generateForClass(meta: JsonClassReaderMeta): TypeSpec {
        val declaration = meta.type.declaration
        val typeParameterResolver = declaration.typeParameters.toTypeParameterResolver()
        val readerInterface = JsonReader::class.asClassName().parameterizedBy(meta.type.toTypeName(typeParameterResolver).copy(false))
        val typeBuilder = TypeSpec.classBuilder(jsonReaderName(meta.type))
            .generated(JsonReaderGenerator::class)
        declaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }

        if (enumType.isAssignableFrom(meta.type.makeNotNullable())) {
            return this.generateForEnum(meta, typeBuilder, typeParameterResolver)
        }
        typeBuilder.addSuperinterface(readerInterface)

        meta.type.declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver))
        }

        this.addBitSet(typeBuilder, meta)
        this.addReaders(typeBuilder, meta, typeParameterResolver)
        this.addFieldNames(typeBuilder, meta)
        this.addReadMethods(typeBuilder, meta, typeParameterResolver)
        val functionBody = CodeBlock.builder()
        functionBody.addStatement("var _token = _parser.currentToken()")
        if (meta.type.isMarkedNullable) {
            functionBody.controlFlow("if (_token == %T.VALUE_NULL) ", JsonToken::class) {
                addStatement("return null")
            }
        }
        assertTokenType(functionBody, JsonToken.START_OBJECT)
        if (meta.fields.size <= maxFields) {
            functionBody.addStatement("val _receivedFields =  intArrayOf(NULLABLE_FIELDS_RECEIVED)")
        } else {
            functionBody.addStatement("val _receivedFields = NULLABLE_FIELDS_RECEIVED.clone() as %T", BitSet::class.java)
        }
        functionBody.add("\n")

        this.addFieldVariables(functionBody, meta, typeParameterResolver)
        functionBody.add("\n")

        this.addFastPath(functionBody, meta)
        functionBody.add("\n")

        if (meta.fields.isEmpty()) {
            functionBody.addStatement("_token = _parser.nextToken()")
        } else {
            functionBody.addStatement("_token = _parser.currentToken()")
        }
        functionBody.controlFlow("while (_token != %T.END_OBJECT) ", JsonToken::class.java) {
            assertTokenType(functionBody, JsonToken.FIELD_NAME)
            functionBody.addStatement("val _fieldName = _parser.currentName")
            functionBody.controlFlow("when (_fieldName)") {
                meta.fields.forEach { field ->
                    functionBody.addStatement("%S -> %L = %L(_parser, _receivedFields)", field.jsonName, field.parameter, readerMethodName(field))
                }
                functionBody.controlFlow("else -> ") {
                    addStatement("_parser.nextToken()")
                    addStatement("_parser.skipChildren()")
                }
            }
            functionBody.addStatement("_token = _parser.nextToken()")
        }

        val errorSwitch = CodeBlock.builder()
            .controlFlow("when (__i)") {
                for (i in 0 until meta.fields.size) {
                    val field = meta.fields[i]
                    addStatement("%L -> %S", i, "${field.parameter.name!!.asString()}(${field.jsonName})")
                }
                addStatement("else -> \"\"")
            }
        if (meta.fields.size > maxFields) {
            functionBody.controlFlow("if (!_receivedFields.equals(ALL_FIELDS_RECEIVED))") {
                addStatement(" _receivedFields.flip(0, %L)", meta.fields.size)
                addStatement("val __error = %T(\"Some of required json fields were not received:\")", StringBuilder::class)

                addStatement("var __i = _receivedFields.nextSetBit(0)")
                controlFlow("while(__i >= 0)") {
                    add("__error.append(\" \").append(\n")
                    indent()
                    add(errorSwitch.build())
                    unindent()
                    add(")\n")
                }

                addStatement("throw %T(_parser, __error.toString())", JsonParseException::class)
            }
        } else {
            functionBody.controlFlow("if (_receivedFields[0] != ALL_FIELDS_RECEIVED)") {
                addStatement("val _nonReceivedFields = _receivedFields[0].inv() and ALL_FIELDS_RECEIVED")
                addStatement("val __error = %T(\"Some of required json fields were not received:\")", StringBuilder::class)
                controlFlow("(0..%L).forEach { __i ->", meta.fields.size) {
                    controlFlow("if ((_nonReceivedFields and (1 shl __i)) != 0)") {
                        add("__error.append(\" \").append(\n")
                        indent()
                        add(errorSwitch.build())
                        unindent()
                        add(")\n")
                    }
                    addStatement("throw %T(_parser, __error.toString())", JsonParseException::class)
                }
            }
        }
        generateReturnResult(meta, functionBody)

        typeBuilder.addFunction(
            FunSpec.builder("read")
                .addParameter("_parser", JsonParser::class)
                .returns(meta.type.toTypeName(typeParameterResolver))
                .addModifiers(KModifier.OVERRIDE)
                .addCode(functionBody.build())
                .build()
        )
        return typeBuilder.build()
    }

    private fun generateReturnResult(meta: JsonClassReaderMeta, functionBody: CodeBlock.Builder) {
        functionBody.add("return %T(\n", meta.type.toClassName()).indent()
        for (i in 0 until meta.fields.size) {
            val field = meta.fields[i]
            val type = field.type
            val paramName = field.parameter.name!!.asString()

            when {
                type.isMarkedNullable -> functionBody.add("%L", paramName)
                type == resolver.builtIns.booleanType -> functionBody.add("%L", paramName)
                type == resolver.builtIns.shortType -> functionBody.add("%L", paramName)
                type == resolver.builtIns.intType -> functionBody.add("%L", paramName)
                type == resolver.builtIns.longType -> functionBody.add("%L", paramName)
                type == resolver.builtIns.floatType -> functionBody.add("%L", paramName)
                type == resolver.builtIns.doubleType -> functionBody.add("%L", paramName)
                else -> functionBody.add("%L!!", paramName)
            }

            functionBody.add(",\n")
        }
        functionBody.unindent().add(")\n")
    }

    private fun generateForEnum(meta: JsonClassReaderMeta, typeBuilder: TypeSpec.Builder, typeParameterResolver: TypeParameterResolver): TypeSpec {
        val readerInterface = readerErasureDeclaration.toClassName().parameterizedBy(meta.type.makeNotNullable().toTypeName(typeParameterResolver))
        typeBuilder.addSuperinterface(readerInterface, CodeBlock.of("%T(%T.values(), { it.toString() })", EnumJsonReader::class.asClassName(), meta.type.makeNotNullable().toClassName()))
        return typeBuilder.build()
    }

    private fun readerFieldName(field: JsonClassReaderMeta.FieldMeta): String {
        return field.parameter.name!!.asString() + "Reader"
    }

    private fun assertTokenType(method: CodeBlock.Builder, expectedToken: JsonToken) {
        method.controlFlow("if (_token != %T.%L)", JsonToken::class, expectedToken.name) {
            addStatement("throw %T(\n _parser, \n%P\n)", JsonParseException::class.java, "Expecting %s token, got \$_token".format(expectedToken))
        }
    }

    private fun addFieldVariables(method: CodeBlock.Builder, meta: JsonClassReaderMeta, typeParameterResolver: TypeParameterResolver) {
        for (i in meta.fields.indices) {
            val field = meta.fields[i]
            val type = field.type
            val paramName = field.parameter.name!!.asString()

            when {
                type.isMarkedNullable -> method.addStatement("var %L: %T = null", paramName, field.parameter.type.toTypeName(typeParameterResolver))
                type == resolver.builtIns.booleanType -> method.addStatement("var %L = false", paramName)
                type == resolver.builtIns.shortType -> method.addStatement("var %L: Short = 0", paramName)
                type == resolver.builtIns.intType -> method.addStatement("var %L = 0", paramName)
                type == resolver.builtIns.longType -> method.addStatement("var %L = 0L", paramName)
                type == resolver.builtIns.floatType -> method.addStatement("var %L = 0f", paramName)
                type == resolver.builtIns.doubleType -> method.addStatement("var %L = 0.0", paramName)
                else -> method.addStatement("var %L: %T = null", paramName, field.parameter.type.toTypeName(typeParameterResolver).copy(nullable = true))
            }
        }
    }

    private fun addReaders(typeBuilder: TypeSpec.Builder, classMeta: JsonClassReaderMeta, typeParameterResolver: TypeParameterResolver) {
        val constructor = FunSpec.constructorBuilder()
        for (field in classMeta.fields) {
            if (field.reader == null && field.typeMeta is ReaderFieldType.KnownTypeReaderMeta) {
                continue
            }
            if (field.reader != null) {
                val fieldName = this.readerFieldName(field)
                val fieldType = field.reader
                val readerProperty = PropertySpec.builder(fieldName, fieldType.toTypeName(typeParameterResolver), KModifier.PRIVATE)
                val readerDeclaration = fieldType.declaration as KSClassDeclaration
                if (!readerDeclaration.modifiers.contains(Modifier.OPEN)) {
                    val constructors = readerDeclaration.getConstructors().toList()
                    if (constructors.size == 1) {
                        readerProperty.initializer("%T()", fieldType.toTypeName(typeParameterResolver))
                        typeBuilder.addProperty(readerProperty.build())
                        continue
                    }
                }
                typeBuilder.addProperty(readerProperty.build())
                constructor.addParameter(fieldName, fieldType.toTypeName(typeParameterResolver))
                constructor.addStatement("this.%L = %L", fieldName, fieldName)
            } else if (field.typeMeta is ReaderFieldType.UnknownTypeReaderMeta) {
                val fieldName: String = this.readerFieldName(field)
                val fieldType = JsonReader::class.asClassName().parameterizedBy(field.typeMeta.type.toTypeName(typeParameterResolver).copy(nullable = false))
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
                addStatement("if (!_parser.nextFieldName(%L)) return@run", jsonNameStaticName(field))
                addStatement("%L = %L(_parser, _receivedFields)", field.parameter, readerMethodName(field))
                functionBody.add("\n")
            }

            functionBody.add("\n")
            functionBody.addStatement("_token = _parser.nextToken()")
            functionBody.controlFlow("while (_token != %T.END_OBJECT)", JsonToken::class) {
                addStatement("_parser.nextToken()")
                addStatement("_parser.skipChildren()")
                addStatement("_token = _parser.nextToken()")
            }
            generateReturnResult(meta, functionBody)
        }

    }

    private fun addFieldNames(typeBuilder: TypeSpec.Builder, meta: JsonClassReaderMeta) {
        for (field in meta.fields) {
            typeBuilder.addProperty(
                PropertySpec.builder(
                    this.jsonNameStaticName(field),
                    SerializedString::class.java,
                    KModifier.PRIVATE
                )
                    .initializer(CodeBlock.of(" %T(%S)", SerializedString::class.java, field.jsonName))
                    .build()
            )
        }
    }

    private fun addReadMethods(typeBuilder: TypeSpec.Builder, meta: JsonClassReaderMeta, typeParameterResolver: TypeParameterResolver) {
        val fields: List<JsonClassReaderMeta.FieldMeta> = meta.fields
        for (i in fields.indices) {
            typeBuilder.addFunction(this.readParamFunction(i, fields.size, fields[i], typeParameterResolver))
        }
    }

    private fun jsonNameStaticName(field: JsonClassReaderMeta.FieldMeta): String {
        return "_" + field.parameter.name!!.asString() + "_optimized_field_name"
    }

    private fun readParamFunction(index: Int, size: Int, field: JsonClassReaderMeta.FieldMeta, typeParameterResolver: TypeParameterResolver): FunSpec {
        val function = FunSpec.builder(readerMethodName(field))
            .addModifiers(KModifier.PRIVATE)
            .addParameter("_parser", JsonParser::class)
            .addParameter("_receivedFields", if (size > maxFields) ClassName(BitSet::class.java.packageName, BitSet::class.simpleName!!) else INT_ARRAY)
            .returns(field.typeMeta.type.toTypeName(typeParameterResolver))

        val functionBody = CodeBlock.builder()
        val fieldParameterType = field.parameter.type.resolve()
        val isMarkedNullable = fieldParameterType.isMarkedNullable

        if (field.reader != null) {
            functionBody.add("val _token = _parser.nextToken()\n")
            if (!isMarkedNullable) {
                functionBody.controlFlow("if (_token == %T.VALUE_NULL)", JsonToken::class) {
                    addStatement("throw %T(\n   _parser, %S\n)", JsonParseException::class, "Expecting non nul value for field %s, got VALUE_NULL token".format(field.jsonName))
                }
                if (size > maxFields) {
                    functionBody.add("_receivedFields.set(%L)\n", index)
                } else {
                    functionBody.add("_receivedFields[0] = _receivedFields[0] or (1 shl %L)\n", index)
                }
            }
            functionBody.add("return %L.read(_parser)\n", this.readerFieldName(field))

            return function.addCode(functionBody.build()).build()
        }

        functionBody.addStatement("val _token = _parser.nextToken()\n")
        if (field.typeMeta is ReaderFieldType.KnownTypeReaderMeta) {
            if (size > maxFields) {
                functionBody.add("_receivedFields.set(%L)\n", index)
            } else {
                functionBody.add("_receivedFields[0] = _receivedFields[0] or (1 shl %L)\n", index)
            }
            functionBody.add(readKnownType(field.jsonName, field.typeMeta.knownType, isMarkedNullable))

            return function.addCode(functionBody.build()).build()
        }

        if (field.type.isMarkedNullable) {
            functionBody.controlFlow("if (_token == %T.VALUE_NULL)", JsonToken::class) {
                addStatement("return null")
            }
        } else {
            functionBody.controlFlow("if (_token == %T.VALUE_NULL)", JsonToken::class) {
                add("throw %T(", JsonParseException::class.java)
                addStatement("_parser,")
                addStatement("%S", "Expecting non null value for field ${field.jsonName}, got VALUE_NULL token")
                add(")")
            }
            if (size > maxFields) {
                functionBody.addStatement("_receivedFields.set(%L)", index)
            } else {
                functionBody.addStatement("_receivedFields[0] = _receivedFields[0] or (1 shl %L)", index)
            }
        }

        val exceptionBlock = if (isMarkedNullable) CodeBlock.of("") else CodeBlock.of(
            " ?: throw %T(\n_parser, %S\n)",
            JsonParseException::class,
            "Field ${field.jsonName} not marked as nullable but null was provided"
        )
        functionBody.addStatement("return %L.read(_parser)%L", readerFieldName(field), exceptionBlock)
        return function.addCode(functionBody.build()).build()
    }

    private fun readKnownType(jsonName: String, knownType: KnownTypesEnum, isNullable: Boolean): CodeBlock {
        val method = CodeBlock.builder()
        method.add(
            when (knownType) {
                KnownTypesEnum.STRING -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %T.VALUE_STRING)", JsonToken::class).apply {
                        addStatement("return _parser.text")
                    }.build()
                }

                KnownTypesEnum.BOOLEAN -> {
                    CodeBlock.builder().apply {
                        beginControlFlow("if (_token == %T.VALUE_TRUE)", JsonToken::class)
                        addStatement("return true")
                        nextControlFlow("else if (_token == %T.VALUE_FALSE)", JsonToken::class)
                        addStatement("return false")
                    }.build()
                }

                INTEGER -> {
                    CodeBlock.builder().beginControlFlow(" if (_token == %T.VALUE_NUMBER_INT)", JsonToken::class).apply {
                        addStatement("return _parser.intValue")
                    }.build()
                }

                BIG_INTEGER -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %T.VALUE_NUMBER_INT)", JsonToken::class).apply {
                        addStatement("return _parser.bigIntegerValue")
                    }.build()
                }

                BIG_DECIMAL -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %1T.VALUE_NUMBER_INT || _token == %1T.VALUE_NUMBER_FLOAT)", JsonToken::class).apply {
                        addStatement("return _parser.decimalValue")
                    }.build()
                }

                KnownTypesEnum.DOUBLE -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %T.VALUE_NUMBER_FLOAT)", JsonToken::class).apply {
                        addStatement("return _parser.doubleValue")
                    }.build()
                }

                KnownTypesEnum.FLOAT -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %T.VALUE_NUMBER_FLOAT)", JsonToken::class).apply {
                        addStatement("return _parser.floatValue\n")
                    }.build()
                }

                KnownTypesEnum.LONG -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %T.VALUE_NUMBER_INT)", JsonToken::class).apply {
                        addStatement("return _parser.longValue")
                    }.build()
                }

                KnownTypesEnum.SHORT -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %T.VALUE_NUMBER_INT)", JsonToken::class).apply {
                        addStatement("return _parser.shortValue")
                    }.build()
                }

                BINARY -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %T.VALUE_STRING)", JsonToken::class).apply {
                        addStatement("return _parser.binaryValue")
                    }.build()
                }

                KnownTypesEnum.UUID -> {
                    CodeBlock.builder().beginControlFlow("if (_token == %T.VALUE_STRING)", JsonToken::class).apply {
                        addStatement("return %T.fromString(_parser.text)", UUID::class)
                    }.build()
                }
            }
        )
        if (isNullable) {
            method.nextControlFlow("else if (_token == %T.VALUE_NULL)", JsonToken::class)
            method.addStatement("return null")
        }
        method.nextControlFlow("else")
        val exceptionMessage = "Expecting %s token for field '%s', got \$_token".format(
            expectedTokens(
                knownType,
                isNullable
            ).contentToString(), jsonName
        )
        method.addStatement("throw %T(\n _parser,\n%P\n)", JsonParseException::class, exceptionMessage)
        method.endControlFlow()
        return method.build()
    }

    private fun expectedTokens(knownType: KnownTypesEnum, nullable: Boolean): Array<JsonToken> {
        var result = when (knownType) {
            KnownTypesEnum.STRING, BINARY, KnownTypesEnum.UUID -> arrayOf(
                JsonToken.VALUE_STRING
            )

            KnownTypesEnum.BOOLEAN -> arrayOf(
                JsonToken.VALUE_TRUE,
                JsonToken.VALUE_FALSE
            )

            KnownTypesEnum.SHORT, INTEGER, KnownTypesEnum.LONG, BIG_INTEGER -> arrayOf(
                JsonToken.VALUE_NUMBER_INT
            )

            BIG_DECIMAL, KnownTypesEnum.DOUBLE, KnownTypesEnum.FLOAT -> arrayOf(
                JsonToken.VALUE_NUMBER_FLOAT, JsonToken.VALUE_NUMBER_INT
            )
        }
        if (nullable) {
            result = result.plus(JsonToken.VALUE_NULL)
        }
        return result
    }

    private fun readerMethodName(field: JsonClassReaderMeta.FieldMeta): String {
        return "read_" + field.parameter.name!!.asString()
    }

    private fun addBitSet(typeBuilder: TypeSpec.Builder, meta: JsonClassReaderMeta) {
        if (meta.fields.size <= maxFields) {
            val sb = StringBuilder()
            for (i in meta.fields.size - 1 downTo 0) {
                val f = meta.fields[i]
                val nullable = f.parameter.type.resolve().isMarkedNullable
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
