package io.koraframework.openfeature.symbol.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.*
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.toTypeName
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.openfeature.symbol.processor.ConfigUtils.ConfigField
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class OpenfeatureSymbolProcessor(private val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {

    private enum class FlagValueType(val method: String) {
        STRING("getStringValue"),
        INTEGER("getIntegerValue"),
        DOUBLE("getDoubleValue"),
        BOOLEAN("getBooleanValue"),
        OBJECT("getObjectValue")
    }

    data class FieldKey(val typeName: TypeName, val tags: Set<String>)

    val classValue = ClassName("dev.openfeature.sdk", "Value")
    val classFlagType = ClassName("dev.openfeature.sdk", "FlagValueType")
    val classClient = ClassName("dev.openfeature.sdk", "Client")
    val classFlagRegistrar = ClassName("io.koraframework.openfeature", "OpenfeatureFlagRegistrar")
    val annotationSourceClass = ClassName("io.koraframework.openfeature.annotation", "OpenfeatureSource")
    val annotationOpenfeatureType = ClassName("io.koraframework.openfeature.annotation", "OpenfeatureType")
    val classMapper = ClassName("io.koraframework.openfeature", "OpenfeatureFlagMapper")
    val classContext = ClassName("dev.openfeature.sdk", "EvaluationContext")
    val classImmutableContext = ClassName("dev.openfeature.sdk", "ImmutableContext")

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(annotationSourceClass.canonicalName).toList()
        val sources = getSourceDeclarations(symbols)
        for (source in sources) {
            val configFields = generateFlagConfigFields(resolver, source)

            val configClassName = generateFlagConfig(source, configFields)
            val impl = generateFlagImpl(source, configClassName)

            val packageName = source.packageName.asString()
            val openfeatureModule = source.generatedClass("Module")
            val type = TypeSpec.interfaceBuilder(openfeatureModule)
                .generated(OpenfeatureSymbolProcessor::class)
                .addAnnotation(AnnotationSpec.builder(CommonClassNames.module).build())
                .addOriginatingKSFile(source)

            val registrarMethod = getFlagRegistrarMethod(source)
            type.addFunction(registrarMethod)
            val sourceMethod = getFlagImplMethod(source, impl, configClassName)
            type.addFunction(sourceMethod)

            val fileSpec = FileSpec.builder(packageName, openfeatureModule)
                .addType(type.build())
                .build()
            fileSpec.writeTo(environment.codeGenerator, false)
        }

        return symbols.filterNot { it.validate() }.toList()
    }

    private fun generateFlagConfig(source: KSClassDeclaration, configFields: List<ConfigField>): ClassName {
        val annotationSource = source.findAnnotation(annotationSourceClass)!!
        val sourcePath = annotationSource.findValueNoDefault<String>("value")!!

        val configName = source.generatedClassName("Config")
        val builder = TypeSpec.interfaceBuilder(configName)
            .addModifiers(KModifier.PUBLIC)
            .generated(OpenfeatureSymbolProcessor::class)
            .addAnnotation(
                AnnotationSpec.builder(ConfigClassNames.configSourceAnnotation)
                    .addMember("%S", sourcePath)
                    .build()
            )
            .addOriginatingKSFile(source)

        for (field in configFields) {
            val methodBuilder = FunSpec.builder(field.name)
                .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)

            if (field.isNullable || field.hasDefault) {
                methodBuilder.returns(field.typeName.copy(true))
            } else {
                methodBuilder.returns(field.typeName.copy(false))
            }

            builder.addFunction(methodBuilder.build())
        }

        val packageName = source.packageName.asString()
        val fileSpec = FileSpec.builder(packageName, configName)
            .addType(builder.build())
            .build()
        fileSpec.writeTo(environment.codeGenerator, false)
        return ClassName(packageName, configName)
    }

    data class FlagImpl(val name: ClassName, val mappers: Map<Mapper, String>)

    data class Mapper(val type: TypeName, val tags: Set<String>)

    private fun generateFlagImpl(source: KSClassDeclaration, configClassName: ClassName): FlagImpl {
        val sourceType = source.toTypeName()
        val annotationSource = source.findAnnotation(annotationSourceClass)!!
        val sourcePath = annotationSource.findValueNoDefault<String>("value")

        val className: String = source.generatedClassName("Impl")
        val builder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .generated(OpenfeatureSymbolProcessor::class)
            .addOriginatingKSFile(source)
            .addSuperinterface(source.toClassName())

        val constructor = FunSpec.constructorBuilder()
            .addParameter("client", classClient)
            .addParameter("config", configClassName)
            .addParameter("context", classContext)

        builder.addProperty("client", classClient, KModifier.PRIVATE, KModifier.FINAL)
        builder.addProperty("config", configClassName, KModifier.PRIVATE, KModifier.FINAL)
        builder.addProperty("context", classContext, KModifier.PRIVATE, KModifier.FINAL)

        val fieldCounter = AtomicInteger(0)
        val mapperToField = linkedMapOf<Mapper, String>()
        val flagMethods = getFlagMethods(source)
        for (method in flagMethods) {
            val flagType = getFlagType(method)
            val methodName = method.simpleName.asString()
            val featureKey = "$sourcePath.$methodName"

            val methodBuilder = FunSpec.builder(method.simpleName.asString())
                .returns(method.returnType!!.toTypeName())
                .addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)

            val bodyBuilder = CodeBlock.builder()
            if (flagType == FlagValueType.OBJECT) {
                if (!method.isAbstract) {
                    bodyBuilder.addStatement(
                        "val defaultValue = %T.requireNonNullElse(config.%L(), super.%L())",
                        Objects::class.java, methodName, methodName
                    )
                } else {
                    bodyBuilder.addStatement("val defaultValue = config.%L()", methodName)
                }

                val mapping = method.parseMappingData().getMapping(classMapper)
                val mapperType = classMapper.parameterizedBy(method.returnType!!.toTypeName().copy(false))
                val mapperImplType = if (mapping?.mapper == null) mapperType else mapping.mapper!!.toTypeName()
                val mapperTags = if (mapping?.tags != null) mapping.tags else setOf()

                val mapperField = mapperToField.computeIfAbsent(Mapper(mapperImplType, mapperTags)) { k ->
                    val fieldName = "mapper" + fieldCounter.incrementAndGet()
                    builder.addProperty(fieldName, mapperType, KModifier.PRIVATE, KModifier.FINAL)

                    val paramBuilder = ParameterSpec.builder(fieldName, mapperImplType)
                    if (mapperTags.isNotEmpty()) {
                        paramBuilder.addAnnotation(mapperTags.toTagAnnotation())
                    }

                    constructor.addParameter(paramBuilder.build())
                    fieldName
                }

                bodyBuilder.addStatement("return %N.map(client, %S, defaultValue, context)", mapperField, featureKey);
            } else {
                if (!method.isAbstract) {
                    bodyBuilder.addStatement(
                        "val defaultValue = %T.requireNonNullElse(config.%L(), super.%L())",
                        Objects::class.java, methodName, methodName
                    )
                } else {
                    bodyBuilder.addStatement("val defaultValue = config.%L()", methodName)
                }

                bodyBuilder.addStatement(
                    "return client.%L(%S, defaultValue, context)",
                    flagType.method, featureKey
                )
            }

            builder.addFunction(methodBuilder.addCode(bodyBuilder.build()).build())
        }

        constructor
            .addStatement("this.client = client")
            .addStatement("this.config = config")
            .addStatement("this.context = context")
        for (field in mapperToField.values) {
            constructor.addStatement("this.%L = %L", field, field)
        }
        builder.addFunction(constructor.build())

        val packageName = source.packageName.asString()
        val name = ClassName(packageName, className)

        if (source.getAllFunctions().any { it.simpleName.asString() == "withContext" && it.parameters.size == 1 }) {
            val withContextBody = CodeBlock.builder()
            if (mapperToField.isEmpty()) {
                withContextBody.addStatement("return %T(client, config, ctx)", name)
            } else {
                withContextBody.addStatement("return %T(client, config, ctx, %L)", name, mapperToField.values.joinToString(", "))
            }

            val withContextMethod = FunSpec.builder("withContext")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameter("ctx", classContext)
                .addCode(withContextBody.build())
                .returns(sourceType)
            builder.addFunction(withContextMethod.build())
        }


        val fileSpec = FileSpec.builder(packageName, className)
            .addType(builder.build())
            .build()
        fileSpec.writeTo(environment.codeGenerator, false)
        return FlagImpl(name, mapperToField)
    }

    private fun generateFlagConfigFields(resolver: Resolver, source: KSClassDeclaration): List<ConfigField> {
        val configFields = ConfigUtils.parseFields(resolver, source)
        if (configFields.isRight) {
            for (processingError in configFields.right()) {
                processingError.print(kspLogger)
            }
        }

        for (field in configFields.left()) {
            if (field.isNullable) {
                throw ProcessingErrorException("Openfeature method can't have nullable parameters", source)
            }
        }

        return configFields.left()
    }

    private fun getFlagImplMethod(source: KSClassDeclaration, impl: FlagImpl, configClassName: ClassName): FunSpec {
        val sourceTypeName = source.toTypeName()
        val flagBuilder = FunSpec.builder("flagSource")
            .addModifiers(KModifier.PUBLIC)
            .addParameter("client", classClient)
            .addParameter("config", configClassName)
            .returns(sourceTypeName)

        impl.mappers.forEach { (mapper, field) ->
            val builder = ParameterSpec.builder(field, mapper.type)
            if (mapper.tags.isNotEmpty()) {
                builder.addAnnotation(mapper.tags.toTagAnnotation())
            }
            flagBuilder.addParameter(builder.build())
        }

        if (impl.mappers.isEmpty()) {
            flagBuilder.addStatement("return %T(client, config, %T())", impl.name, classImmutableContext)
        } else {
            flagBuilder.addStatement("return %T(client, config, %T(), %L)", impl.name, classImmutableContext, impl.mappers.values.joinToString(", "))
        }

        return flagBuilder.build()
    }

    private fun getFlagRegistrarMethod(source: KSClassDeclaration): FunSpec {
        val flagBuilder = FunSpec.builder("flagRegistration")
            .addModifiers(KModifier.PUBLIC)
            .returns(classFlagRegistrar)

        val bodyBuilder = CodeBlock.builder()
        val memberMapOf = MemberName("kotlin.collections", "mapOf")
        bodyBuilder.add("return %T { %M (\n", classFlagRegistrar, memberMapOf)
        bodyBuilder.indent().indent()

        val annotationSource = source.findAnnotation(annotationSourceClass)
        val sourcePath = annotationSource!!.findValueNoDefault<String>("value")
        val flagMethods = getFlagMethods(source)
        for (i in flagMethods.indices) {
            val method = flagMethods[i]
            val flagType = getFlagType(method)

            val openfeatureFlagType = if (flagType == FlagValueType.OBJECT) {
                method.findAnnotation(annotationOpenfeatureType)
                    ?.findValueNoDefault<KSClassDeclaration>("value")?.toClassName()?.simpleName
                    ?: "OBJECT"
            } else {
                flagType.name
            }

            bodyBuilder.add("%S to %T.%L", sourcePath + "." + method.simpleName.asString(), classFlagType, openfeatureFlagType)
            if (i + 1 < flagMethods.size) {
                bodyBuilder.add(",\n")
            } else {
                bodyBuilder.add("\n")
            }
        }
        bodyBuilder.unindent().unindent().addStatement(") }")

        flagBuilder.addCode(bodyBuilder.build())
        return flagBuilder.build()
    }

    private fun getFlagType(method: KSFunctionDeclaration): FlagValueType {
        val returnType = method.returnType
        val typeName = returnType!!.toTypeName().copy(false)

        return when {
            INT == typeName -> FlagValueType.INTEGER
            BOOLEAN == typeName -> FlagValueType.BOOLEAN
            DOUBLE == typeName -> FlagValueType.DOUBLE
            STRING == typeName -> FlagValueType.STRING
            else -> FlagValueType.OBJECT
        }
    }

    private fun getSourceDeclarations(symbols: List<KSAnnotated>): List<KSClassDeclaration> {
        return symbols.asSequence()
            .filter { s -> s.validate() }
            .map { s -> s.visitClass { it } }
            .filterNotNull()
            .filter { it.classKind == ClassKind.INTERFACE }
            .toList()
    }

    private fun getFlagMethods(source: KSClassDeclaration): List<KSFunctionDeclaration> {
        return source
            .getDeclaredFunctions()
            .toList()
    }
}
