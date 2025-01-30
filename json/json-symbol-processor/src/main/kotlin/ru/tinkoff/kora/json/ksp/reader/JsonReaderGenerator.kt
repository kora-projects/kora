package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum
import ru.tinkoff.kora.json.ksp.KnownType.KnownTypesEnum.*
import ru.tinkoff.kora.json.ksp.jsonReaderName
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import java.util.*
import java.util.UUID

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
        declaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }

        typeBuilder.addSuperinterface(readerInterface)

        declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName(typeParameterResolver))
        }

        this.addBitSet(typeBuilder, meta)
        this.addReaders(typeBuilder, meta, typeParameterResolver)
        this.addFieldNames(typeBuilder, meta)
        this.addReadMethods(typeBuilder, meta)
        val functionBody = CodeBlock.builder()
        functionBody.addStatement("var __token = __parser.currentToken()")
        functionBody.controlFlow("if (__token == %T.VALUE_NULL) ", JsonTypes.jsonToken) {
            if (JsonTypes.jsonNullable == declaration.toClassName()) {
                addStatement("return %T.nullValue()", JsonTypes.jsonNullable)
            } else {
                addStatement("return null")
            }
        }
        assertTokenType(functionBody, "START_OBJECT")
        functionBody.add("\n")
        if (meta.fields.size <= maxFields) {
            functionBody.addStatement("val __receivedFields =  intArrayOf(NULLABLE_FIELDS_RECEIVED)")
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
            assertTokenType(functionBody, "FIELD_NAME")
            functionBody.addStatement("val __fieldName = __parser.currentName()")
            functionBody.controlFlow("when (__fieldName)") {
                meta.fields.forEach { field ->
                    functionBody.addStatement("%S -> %N = %N(__parser, __receivedFields)", field.jsonName, field.parameter.name!!.asString(), readerMethodName(field))
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
                    addStatement("%L -> %S", i, "${field.parameter.name!!.asString()}(${field.jsonName})")
                }
                addStatement("else -> \"\"")
            }
        if (meta.fields.size > maxFields) {
            functionBody.controlFlow("if (__receivedFields != ALL_FIELDS_RECEIVED)") {
                addStatement(" __receivedFields.flip(0, %L)", meta.fields.size)
                addStatement("val __error = %T(\"Some of required json fields were not received:\")", StringBuilder::class)

                addStatement("var __i = __receivedFields.nextSetBit(0)")
                controlFlow("while (__i >= 0)") {
                    add("__error.append(\" \").append(\n")
                    indent()
                    add(errorSwitch.build())
                    unindent()
                    add(")\n")
                    add("__i = __receivedFields.nextSetBit(__i + 1)\n")
                }
                addStatement("throw %T(__parser, __error.toString())", JsonTypes.jsonParseException)
            }
        } else {
            functionBody.controlFlow("if (__receivedFields[0] != ALL_FIELDS_RECEIVED)") {
                addStatement("val _nonReceivedFields = __receivedFields[0].inv() and ALL_FIELDS_RECEIVED")
                addStatement("val __error = %T(\"Some of required json fields were not received:\")", StringBuilder::class)
                controlFlow("(0..%L).forEach { __i ->", meta.fields.size) {
                    controlFlow("if ((_nonReceivedFields and (1 shl __i)) != 0)") {
                        add("__error.append(\" \").append(\n")
                        indent()
                        add(errorSwitch.build())
                        unindent()
                        add(")\n")
                    }
                }
                addStatement("throw %T(__parser, __error.toString())", JsonTypes.jsonParseException)
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
                    if(field.typeMeta.isJsonNullable) {
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

    private fun assertTokenType(method: CodeBlock.Builder, expectedToken: String) {
        method.controlFlow("if (__token != %T.%L)", JsonTypes.jsonToken, expectedToken) {
            addStatement(
                "throw %T(__parser, %P)",
                JsonTypes.jsonParseException,
                "Expecting $expectedToken token, got \$__token"
            )
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
                addStatement("if (!__parser.nextFieldName(%N)) return@run", jsonNameStaticName(field))
                addStatement("%N = %N(__parser, __receivedFields)", field.parameter.name!!.asString(), readerMethodName(field))
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
            .addParameter("__receivedFields", if (size > maxFields) ClassName(BitSet::class.java.packageName, BitSet::class.simpleName!!) else INT_ARRAY)
            .returns(field.type)

        val functionBody = CodeBlock.builder()
        val isMarkedNullable = field.parameter.type.resolve().isMarkedNullable

        if (field.reader != null) {
            functionBody.add("val __token = __parser.nextToken()\n")
            if (field.typeMeta.isJsonNullable) {
                functionBody.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                    addStatement(
                        "throw %T(\n__parser,\n%S\n)",
                        JsonTypes.jsonParseException,
                        "Expecting non nul value for field '${field.jsonName}', got VALUE_NULL token"
                    )
                }
                if (size > maxFields) {
                    functionBody.add("__receivedFields.set(%L)\n", index)
                } else {
                    functionBody.add("__receivedFields[0] = __receivedFields[0] or (1 shl %L)\n", index)
                }
            } else if (!isMarkedNullable) {
                functionBody.controlFlow("if (__token == %T.VALUE_NULL)", JsonTypes.jsonToken) {
                    addStatement(
                        "throw %T(\n__parser,\n%S\n)",
                        JsonTypes.jsonParseException,
                        "Expecting non nul value for field '${field.jsonName}', got VALUE_NULL token"
                    )
                }
                if (size > maxFields) {
                    functionBody.add("__receivedFields.set(%L)\n", index)
                } else {
                    functionBody.add("__receivedFields[0] = __receivedFields[0] or (1 shl %L)\n", index)
                }
            }
            functionBody.add("return %L.read(__parser)\n", this.readerFieldName(field))

            return function.addCode(functionBody.build()).build()
        }

        functionBody.addStatement("val __token = __parser.nextToken()\n")
        if (field.typeMeta is ReaderFieldType.KnownTypeReaderMeta) {
            if (size > maxFields) {
                functionBody.add("__receivedFields.set(%L)\n", index)
            } else {
                functionBody.add("__receivedFields[0] = __receivedFields[0] or (1 shl %L)\n", index)
            }
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
                add("throw %T(", JsonTypes.jsonParseException)
                addStatement("__parser,")
                addStatement("%S", "Expecting non null value for field ${field.jsonName}, got VALUE_NULL token")
                add(")")
            }
            if (size > maxFields) {
                functionBody.addStatement("__receivedFields.set(%L)", index)
            } else {
                functionBody.addStatement("__receivedFields[0] = __receivedFields[0] or (1 shl %L)", index)
            }
        }

        if(field.typeMeta.isJsonNullable) {
            functionBody.addStatement("return %T.ofNullable(%L.read(__parser))", JsonTypes.jsonNullable, readerFieldName(field))
        } else {
            val exceptionBlock = if (isMarkedNullable) CodeBlock.of("") else CodeBlock.of(
                " ?: throw %T(\n__parser, %S\n)",
                JsonTypes.jsonParseException,
                "Field ${field.jsonName} not marked as nullable but null was provided"
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

            BIG_DECIMAL -> method.controlFlow("if (__token == %1T.VALUE_NUMBER_INT || __token == %1T.VALUE_NUMBER_FLOAT)", JsonTypes.jsonToken) {
                if (isJsonNullable) {
                    addStatement("return %T.ofNullable(__parser.decimalValue)", JsonTypes.jsonNullable)
                } else {
                    addStatement("return __parser.decimalValue")
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
                    addStatement("return %T.ofNullable(%T.fromString(__parser.text))", JsonTypes.jsonNullable, UUID::class)
                } else {
                    addStatement("return %T.fromString(__parser.text)", UUID::class)
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
        }

        val expectedTokenStr = expectedTokens(knownType, isNullable)
            .contentToString()

        method.addStatement(
            "throw %T(__parser, %S + __token)",
            JsonTypes.jsonParseException,
            "Expecting $expectedTokenStr token for field '$jsonName', got "
        )
        return method.build()
    }

    private fun expectedTokens(knownType: KnownTypesEnum, nullable: Boolean): Array<String> {
        var result = when (knownType) {
            KnownTypesEnum.STRING, BINARY, KnownTypesEnum.UUID -> arrayOf(
                "VALUE_STRING"
            )

            KnownTypesEnum.BOOLEAN -> arrayOf(
                "VALUE_TRUE",
                "VALUE_FALSE"
            )

            KnownTypesEnum.SHORT, INTEGER, KnownTypesEnum.LONG, BIG_INTEGER -> arrayOf(
                "VALUE_NUMBER_INT"
            )

            BIG_DECIMAL, KnownTypesEnum.DOUBLE, KnownTypesEnum.FLOAT -> arrayOf(
                "VALUE_NUMBER_FLOAT", "VALUE_NUMBER_INT"
            )
        }
        if (nullable) {
            result = result.plus("VALUE_NULL")
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
