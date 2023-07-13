package ru.tinkoff.kora.config.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.common.util.Either
import ru.tinkoff.kora.config.ksp.ConfigUtils.ConfigField
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.FieldFactory
import ru.tinkoff.kora.ksp.common.JavaUtils.isRecord
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.generatedClassName

class ConfigParserGenerator(private val resolver: Resolver) {

    fun generateForInterface(codeGenerator: CodeGenerator, targetType: KSType, aggregating: Boolean = false): Either<Unit, List<ProcessingError>> {
        val element = targetType.declaration as KSClassDeclaration
        val f = ConfigUtils.parseFields(resolver, element)
        if (f.isRight) {
            return Either.right(f.right())
        }

        val typeName = element.generatedClassName(ConfigClassNames.configValueExtractor.simpleName)
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addSuperinterface(ConfigClassNames.configValueExtractor.parameterizedBy(targetType.toTypeName()))
            .generated(ConfigParserGenerator::class)
        element.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }

        val fields = f.left()!!
        val defaultsType = buildDefaultsType(targetType, element, fields)
        val packageName = element.packageName.asString()
        val hasRequiredFields = fields.any { !it.hasDefault && !it.isNullable }
        val implClassName = ClassName(packageName, typeName, element.simpleName.asString() + "_Impl")
        if (defaultsType != null) {
            typeBuilder.addType(defaultsType)
            val defaultImplClassName = ClassName(packageName, typeName, element.simpleName.asString() + "_Defaults")
            val property = PropertySpec.builder("DEFAULTS", defaultImplClassName, KModifier.PRIVATE, KModifier.FINAL)
                .initializer("%T()", defaultImplClassName)
            typeBuilder.addProperty(property.build())
            if (!hasRequiredFields) {
                val initializer = CodeBlock.builder().add("%T(", implClassName).indent()
                for (i in fields.indices) {
                    if (i > 0) {
                        initializer.add(",")
                    }
                    if (fields[i].hasDefault) {
                        initializer.add("\n  DEFAULTS.%N()", fields[i].name)
                    } else {
                        initializer.add("\n  null")
                    }
                }
                initializer.unindent().add("\n)")
            }
        } else {
            if (!hasRequiredFields) {
                val initializer = CodeBlock.builder().add("%T(", implClassName).indent()
                for (i in fields.indices) {
                    if (i > 0) {
                        initializer.add(",")
                    }
                    initializer.add("\nnull")
                }
                initializer.unindent().add("\n)")
                val defaultValue = PropertySpec.builder("DEFAULTS", implClassName, KModifier.PRIVATE, KModifier.FINAL)
                    .initializer(initializer.build())
                typeBuilder.addProperty(defaultValue.build())
            }
        }
        val constructor = buildConstructor(typeBuilder, fields)
        typeBuilder.primaryConstructor(constructor)
        typeBuilder.addFunction(buildExtractMethod(element, targetType.toTypeName(), implClassName, fields))
        val companion = TypeSpec.companionObjectBuilder();
        for (field in fields) {
            companion.addProperty(
                PropertySpec.builder(
                    "_" + field.name + "_path",
                    ConfigClassNames.pathElementKey,
                    KModifier.PRIVATE,
                    KModifier.FINAL
                )
                    .initializer(CodeBlock.of("%T.get(%S)", ConfigClassNames.pathElement, field.name))
                    .build()
            )
            val parseFieldMethod = this.buildParseField(element, field)
            typeBuilder.addFunction(parseFieldMethod)
        }
        typeBuilder.addType(companion.build())
        this.buildConfigInterfaceImplementation(element, fields)?.let {
            typeBuilder.addType(it)
        }
        val file = FileSpec.get(packageName, typeBuilder.build())
        file.writeTo(codeGenerator, aggregating)
        return Either.left(Unit)
    }

    fun generateForDataClass(codeGenerator: CodeGenerator, targetType: KSType, aggregating: Boolean = false): Either<Unit, List<ProcessingError>> {
        val element = targetType.declaration as KSClassDeclaration
        val f = ConfigUtils.parseFields(resolver, element)
        if (f.isRight) {
            return Either.right(f.right())
        }
        val typeName: String = element.generatedClassName(ConfigClassNames.configValueExtractor.simpleName)
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addSuperinterface(ConfigClassNames.configValueExtractor.parameterizedBy(targetType.toTypeName().copy(false)))
            .generated(ConfigParserGenerator::class)
        val fields = f.left()!!
        val hasRequiredFields = fields.stream()
            .anyMatch { !it.hasDefault && !it.isNullable }
        val implClassName = element.toClassName()
        if (!hasRequiredFields) {
            val initializer = CodeBlock.builder().add("%T(", implClassName).indent()
            for (i in fields.indices) {
                if (i > 0) {
                    initializer.add(",")
                }
                initializer.add("\nnull")
            }
            initializer.unindent().add("\n)")
        }
        val constructor = buildConstructor(typeBuilder, fields)
        typeBuilder.primaryConstructor(constructor)
        typeBuilder.addFunction(buildExtractMethod(element, targetType.toTypeName(), implClassName, fields))
        val companion = TypeSpec.companionObjectBuilder();
        for (field in fields) {
            companion.addProperty(
                PropertySpec.builder(
                    "_" + field.name + "_path",
                    ConfigClassNames.pathElementKey,
                    KModifier.PRIVATE,
                    KModifier.FINAL
                )
                    .initializer(CodeBlock.of("%T.get(%S)", ConfigClassNames.pathElement, field.name))
                    .build()
            )
            val parseFieldMethod = buildParseField(element, field)
            typeBuilder.addFunction(parseFieldMethod)
        }
        typeBuilder.addType(companion.build())
        val packageName = element.packageName.asString()
        val file = FileSpec.get(packageName, typeBuilder.build())
        file.writeTo(codeGenerator, aggregating)
        return Either.left(Unit)
    }

    fun generateForRecord(codeGenerator: CodeGenerator, targetType: KSType): Either<Unit, List<ProcessingError>> {
        val decl = targetType.declaration as KSClassDeclaration
        val f = ConfigUtils.parseFields(resolver, decl)
        if (f.isRight) {
            return Either.right(f.right())
        }

        val typeName = decl.generatedClassName(ConfigClassNames.configValueExtractor.simpleName)
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addSuperinterface(ConfigClassNames.configValueExtractor.parameterizedBy(targetType.toTypeName().copy(false)))
            .generated(ConfigParserGenerator::class)
        val fields = f.left()!!

        val hasRequiredFields = fields.any { !it.hasDefault && !it.isNullable }
        val implClassName = decl.toClassName()
        if (!hasRequiredFields) {
            val initializer = CodeBlock.builder().add("%T(", implClassName).indent()
            for (i in fields.indices) {
                if (i > 0) {
                    initializer.add(",")
                }
                initializer.add("\nnull")
            }
            initializer.unindent().add("\n)")
        }
        val constructor = buildConstructor(typeBuilder, fields)
        typeBuilder.primaryConstructor(constructor)
        typeBuilder.addFunction(buildExtractMethod(decl, targetType.toTypeName(), implClassName, fields))
        val companion = TypeSpec.companionObjectBuilder();
        for (field in fields) {
            companion.addProperty(
                PropertySpec.builder(
                    "_" + field.name + "_path",
                    ConfigClassNames.pathElementKey,
                    KModifier.PRIVATE,
                    KModifier.FINAL
                )
                    .initializer(CodeBlock.of("%T.get(%S)", ConfigClassNames.pathElement, field.name))
                    .build()
            )
            val parseFieldMethod = buildParseField(decl, field)
            typeBuilder.addFunction(parseFieldMethod)
        }
        typeBuilder.addType(companion.build())
        val packageName = decl.packageName.asString()
        val file = FileSpec.get(packageName, typeBuilder.build())
        file.writeTo(codeGenerator, true)
        return Either.left(Unit)
    }

    fun generateForPojo(codeGenerator: CodeGenerator, targetType: KSType): Either<Unit, List<ProcessingError>> {
        val decl = targetType.declaration as KSClassDeclaration
        val f = ConfigUtils.parseFields(resolver, decl)
        if (f.isRight) {
            return Either.right(f.right())
        }

        val typeName = decl.generatedClassName(ConfigClassNames.configValueExtractor.simpleName)
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addSuperinterface(ConfigClassNames.configValueExtractor.parameterizedBy(targetType.toTypeName().copy(false)))
            .generated(ConfigParserGenerator::class)
        val fields = f.left()!!

        val implClassName = decl.toClassName()
        val defaults = PropertySpec.builder("DEFAULTS", implClassName, KModifier.PRIVATE, KModifier.FINAL)
            .initializer(CodeBlock.of("%T()", implClassName))
        typeBuilder.addProperty(defaults.build())
        val constructor = buildConstructor(typeBuilder, fields)
        typeBuilder.primaryConstructor(constructor)
        typeBuilder.addFunction(buildExtractMethod(decl, targetType.toTypeName(), implClassName, fields))
        val companion = TypeSpec.companionObjectBuilder();
        for (field in fields) {
            companion.addProperty(
                PropertySpec.builder(
                    "_" + field.name + "_path",
                    ConfigClassNames.pathElementKey,
                    KModifier.PRIVATE,
                    KModifier.FINAL
                )
                    .initializer(CodeBlock.of("%T.get(%S)", ConfigClassNames.pathElement, field.name))
                    .build()
            )
            val parseFieldMethod = buildParseField(decl, field)
            typeBuilder.addFunction(parseFieldMethod)
        }
        typeBuilder.addType(companion.build())
        val packageName = decl.packageName.asString()
        val file = FileSpec.get(packageName, typeBuilder.build())
        file.writeTo(codeGenerator, true)
        return Either.left(Unit)
    }

    private fun buildDefaultsType(type: KSType, typeDecl: KSClassDeclaration, fields: List<ConfigField>): TypeSpec? {
        var hasDefaults = false
        val defaults = TypeSpec.classBuilder(typeDecl.simpleName.asString() + "_Defaults")
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(type.toTypeName())
        for (tp in typeDecl.typeParameters) {
            defaults.addTypeVariable(tp.toTypeVariableName(typeDecl.typeParameters.toTypeParameterResolver()))
        }
        for (field in fields) {
            if (field.hasDefault) {
                hasDefaults = true
                continue
            }
            val m = FunSpec.builder(field.name)
                .addModifiers(KModifier.OVERRIDE)
                .returns(field.typeName)
            m.addStatement("TODO()")
            defaults.addFunction(m.build())
        }
        if (hasDefaults) {
            return defaults.build()
        }
        return null
    }

    private fun buildConstructor(parser: TypeSpec.Builder, fields: List<ConfigField>): FunSpec {
        val constructor = FunSpec.constructorBuilder();
        val fieldFactory = FieldFactory(parser, constructor, "parser")
        for (field in fields) {
            val isSupported = field.mapping == null && supportedTypes.containsKey(field.typeName)
            if (isSupported) {
                continue
            }
            val fieldParserType = ConfigClassNames.configValueExtractor.parameterizedBy(field.typeName.copy(false))
            val constructorParameterName = fieldFactory.add(field.mapping, fieldParserType)
            val fieldParserName: String = field.name + "_parser"
            parser.addProperty(
                PropertySpec.builder(fieldParserName, fieldParserType, KModifier.PRIVATE)
                    .build()
            )
            constructor.addStatement("this.%N = %N", fieldParserName, constructorParameterName)
        }
        return constructor.build()
    }

    private fun buildExtractMethod(typeDecl: KSClassDeclaration, typeName: TypeName, implClassName: ClassName, fields: List<ConfigUtils.ConfigField>): FunSpec {
        val rootParse = FunSpec.builder("extract")
            .addModifiers(KModifier.OVERRIDE)
            .returns(typeName.copy(false))
        rootParse.addParameter("_sourceValue", ConfigClassNames.configValue.parameterizedBy(WildcardTypeName.producerOf(ANY)))
        val mapNullAsEmptyObject = typeDecl.findAnnotation(ConfigClassNames.configValueExtractorAnnotation)
            ?.findValueNoDefault<Boolean>("mapNullAsEmptyObject")
            ?: true

        if (mapNullAsEmptyObject) {
            rootParse.addCode("val _config = ").controlFlow("if (_sourceValue is %T.NullValue)", ConfigClassNames.configValue) {
                addStatement("%T.ObjectValue(_sourceValue.origin(), mapOf())", ConfigClassNames.configValue)
                nextControlFlow("else")
                addStatement("_sourceValue.asObject()")
            }
        } else {
            rootParse.controlFlow("if (_sourceValue is %T.NullValue)", ConfigClassNames.configValue) {
                rootParse.addCode("return null")
                rootParse.returns(typeName.copy(true))
            }
        }
        for (field in fields) {
            rootParse.addStatement("val %N = this.%N(_config)", field.name, "parse_${field.name}")
        }
        if (typeDecl.classKind == ClassKind.CLASS && !typeDecl.modifiers.contains(Modifier.DATA) && !typeDecl.isRecord()) {
            rootParse.addStatement("val _result = %T()", implClassName)
            for (field in fields) {
                rootParse.addStatement("_result.%N = %N", field.name, field.name)
            }
            rootParse.addStatement("return _result")
        } else {
            val returnCodeBlock = CodeBlock.builder()
            returnCodeBlock.add("return %T(\n", implClassName)
            for (i in fields.indices) {
                val field = fields[i]
                if (i > 0) {
                    returnCodeBlock.add(",\n")
                }
                returnCodeBlock.add("  %N", field.name)
            }
            rootParse.addCode(returnCodeBlock.add("\n);\n").build())
        }
        return rootParse.build()
    }

    private fun buildParseField(typeDecl: KSClassDeclaration, field: ConfigField): FunSpec {
        val parse = FunSpec.builder("parse_" + field.name)
            .addModifiers(KModifier.PRIVATE)
            .returns(field.typeName)
        parse.addParameter("config", ConfigClassNames.objectValue)
        parse.addStatement("var value = config.get(%N)", "_${field.name}_path")
        val isSupportedType = field.mapping == null && supportedTypes.containsKey(field.typeName)
        if (field.hasDefault || isSupportedType) {
            parse.controlFlow("if (value is %T.NullValue)", ConfigClassNames.configValue) {
                if (field.hasDefault) {
                    if (typeDecl.classKind == ClassKind.INTERFACE) {
                        parse.addStatement("return DEFAULTS.%N()", field.name)
                    } else {
                        parse.addStatement("return DEFAULTS.%N", field.name)
                    }
                } else {
                    if (field.isNullable) {
                        parse.addStatement("return null")
                    } else {
                        parse.addStatement("throw %T.missingValue(value)", ConfigClassNames.configValueExtractionException)
                    }
                }
            }
        }
        if (isSupportedType) {
            parse.addStatement("return %L", this.parseSupportedType(field.typeName))
        } else if (field.isNullable) {
            parse.addStatement("return %N.extract(value)", "${field.name}_parser")
        } else {
            parse.addStatement("var parsed = %N.extract(value)", "${field.name}_parser")
            parse.controlFlow("if (parsed == null)") {
                parse.addStatement("throw %T.missingValueAfterParse(value)", ConfigClassNames.configValueExtractionException)
                parse.nextControlFlow("else")
                parse.addStatement("return parsed")
            }
        }
        return parse.build()
    }

    private fun buildConfigInterfaceImplementation(typeDecl: KSClassDeclaration, fields: List<ConfigField>): TypeSpec? {
        var b = TypeSpec.classBuilder(typeDecl.simpleName.asString() + "_Impl")
            .addModifiers(KModifier.DATA)
            .addSuperinterface(typeDecl.toTypeName(listOf()))
        val constructor = FunSpec.constructorBuilder()
        for (field in fields) {
            constructor.addParameter(field.name, field.typeName)
            b.addProperty(PropertySpec.builder(field.name, field.typeName).initializer("%N", field.name).build())
            b.addFunction(
                FunSpec.builder(field.name)
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(field.typeName)
                    .addStatement("return this.%N", field.name)
                    .build()
            )
        }
        if (fields.any { it.typeName is ParameterizedTypeName && it.typeName.rawType == ARRAY || it.typeName is ClassName && it.typeName.packageName == "kotlin" && it.typeName.simpleName.endsWith("Array") }) {
            val equals = FunSpec.builder("equals")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("that", ANY.copy(true))
                .addCode("return this === that || that is %T\n", typeDecl.toTypeName())
            for (field in fields) {
                if (field.typeName is ParameterizedTypeName && field.typeName.rawType == ARRAY || field.typeName is ClassName && field.typeName.packageName == "kotlin" && field.typeName.simpleName.endsWith(
                        "Array"
                    )
                ) {
                    equals.addCode("  && this.%N.contentEquals(that.%N())\n", field.name, field.name)
                } else {
                    equals.addCode("  && this.%N == that.%N()\n", field.name, field.name)
                }
            }
            equals.addCode("  ;\n")
            b.addFunction(equals.build())

        }
        return b.primaryConstructor(constructor.build()).build()
    }


    private val supportedTypes = mapOf(
        INT to CodeBlock.of("value.asNumber().toInt()"),
        INT.copy(true) to CodeBlock.of("value.asNumber().toInt()"),
        LONG to CodeBlock.of("value.asNumber().toLong()"),
        LONG.copy(true) to CodeBlock.of("value.asNumber().toLong()"),
        DOUBLE to CodeBlock.of("value.asNumber().toDouble()"),
        DOUBLE.copy(true) to CodeBlock.of("value.asNumber().toDouble()"),
        STRING to CodeBlock.of("value.asString()"),
        STRING.copy(true) to CodeBlock.of("value.asString()")
    )

    private fun parseSupportedType(typeName: TypeName): CodeBlock {
        return supportedTypes[typeName]!!
    }
}
