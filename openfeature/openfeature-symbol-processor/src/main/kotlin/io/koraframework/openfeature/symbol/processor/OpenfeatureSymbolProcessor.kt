package io.koraframework.openfeature.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.*
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException

class OpenfeatureSymbolProcessor(private val environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {
    private val sourceAnnotation = ClassName("io.koraframework.openfeature.annotation", "OpenfeatureSource")
    private val typeAnnotation = ClassName("io.koraframework.openfeature.annotation", "OpenfeatureType")
    private val keyAnnotation = ClassName("io.koraframework.openfeature.annotation", "OpenfeatureKey")
    private val client = ClassName("dev.openfeature.sdk", "Client")
    private val context = ClassName("dev.openfeature.sdk", "EvaluationContext")
    private val immutableContext = ClassName("dev.openfeature.sdk", "ImmutableContext")
    private val mapper = ClassName("io.koraframework.openfeature.mapper", "OpenfeatureFlagMapper")
    private val registrar = ClassName("io.koraframework.openfeature", "OpenfeatureFlagRegistrar")
    private val flagType = ClassName("dev.openfeature.sdk", "FlagValueType")
    private val processed = hashSetOf<String>()

    private data class Flag(val method: KSFunctionDeclaration, val type: TypeName, val key: String,
                            val sdkType: String, val sdkMethod: String?, val mapping: MappingData?)
    private data class Mapper(val type: TypeName, val tag: String?)

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(sourceAnnotation.canonicalName).toList()
        for (symbol in symbols.filter { it.validate(enableNewFeatures = true) }) {
            try {
                val source = symbol as? KSClassDeclaration
                    ?: throw ProcessingErrorException("@OpenfeatureSource requires an interface", symbol)
                if (source.classKind != ClassKind.INTERFACE) {
                    throw ProcessingErrorException("@OpenfeatureSource requires an interface", source)
                }
                if (source.typeParameters.isNotEmpty() || Modifier.SEALED in source.modifiers) {
                    throw ProcessingErrorException("@OpenfeatureSource requires a non-generic, non-sealed interface", source)
                }
                if (source.getAllProperties().any()) {
                    throw ProcessingErrorException("OpenFeature flags must be declared as functions", source)
                }
                if (processed.add(source.qualifiedName!!.asString())) {
                    generate(source)
                }
            } catch (e: ProcessingErrorException) {
                e.printError(kspLogger)
            }
        }
        return symbols.filterNot { it.validate(enableNewFeatures = true) }
    }

    private fun generate(source: KSClassDeclaration) {
        val annotation = source.findAnnotation(sourceAnnotation)!!
        val path = annotation.findValueNoDefault<String>("value")!!
        val clientTag = annotation.findValueNoDefault<KSType>("clientTag")?.declaration?.qualifiedName?.asString()
            ?.takeUnless { it == CommonClassNames.tag.canonicalName }
        val flags = flags(source, path)
        val packageName = source.packageName.asString()
        val sourceType = source.asType(emptyList()).toTypeName()
        val configName = ClassName(packageName, source.generatedClassName("Config"))
        val implName = ClassName(packageName, source.generatedClassName("Impl"))
        val moduleName = ClassName(packageName, source.generatedClassName("Module"))
        val config = TypeSpec.interfaceBuilder(configName)
            .generated(OpenfeatureSymbolProcessor::class).addOriginatingKSFile(source)
            .addAnnotation(AnnotationSpec.builder(ConfigClassNames.configSourceAnnotation).addMember("%S", path).build())
        val impl = TypeSpec.classBuilder(implName).addSuperinterface(sourceType)
            .generated(OpenfeatureSymbolProcessor::class).addOriginatingKSFile(source)
        val constructor = FunSpec.constructorBuilder().addParameter(clientParameter(clientTag))
            .addParameter("config", configName).addParameter("context", context)
        impl.addProperty(PropertySpec.builder("client", client, KModifier.PRIVATE).initializer("client").build())
            .addProperty(PropertySpec.builder("config", configName, KModifier.PRIVATE).initializer("config").build())
            .addProperty(PropertySpec.builder("context", context, KModifier.PRIVATE)
                .initializer("%T(context.targetingKey, context.asMap())", immutableContext).build())
        val mappers = linkedMapOf<Mapper, String>()
        for (flag in flags) {
            val method = flag.method
            val name = method.simpleName.asString()
            val configMethod = FunSpec.builder(name).addModifiers(KModifier.ABSTRACT)
                .returns(flag.type.copy(nullable = !method.isAbstract))
            val configMapping = method.parseMappingData().getMapping(CommonClassNames.configValueMapper)
            configMapping?.mapper?.let {
                configMethod.addAnnotation(AnnotationSpec.builder(CommonClassNames.mapping)
                    .addMember("%T::class", it.toTypeName()).build())
            }
            if (configMapping?.mapper != null) {
                configMapping.toTagAnnotation()?.let { configMethod.addAnnotation(it) }
            }
            config.addFunction(configMethod.build())
            val implementation = FunSpec.builder(name).addModifiers(KModifier.OVERRIDE).returns(flag.type)
            if (method.isAbstract) {
                implementation.addStatement("val defaultValue = config.%N()", name)
            } else {
                implementation.addStatement("val defaultValue = config.%N() ?: super<%T>.%N()", name, sourceType, name)
            }
            if (flag.sdkMethod != null && flag.mapping == null) {
                implementation.addStatement("return client.%N(%S, defaultValue, context)", flag.sdkMethod, flag.key)
            } else {
                val mapperInterface = mapper.parameterizedBy(flag.type)
                val mapping = flag.mapping
                val mapperType = when {
                    mapping?.mapper == null -> mapperInterface
                    mapping.isGeneric() -> mapping.parameterized(flag.type)
                    else -> mapping.mapper!!.toTypeName()
                }
                val tag = mapping?.tag
                val field = mappers.getOrPut(Mapper(mapperType, tag)) {
                    val fieldName = "mapper" + mappers.size
                    impl.addProperty(PropertySpec.builder(fieldName, mapperType, KModifier.PRIVATE)
                        .initializer(fieldName).build())
                    val parameter = ParameterSpec.builder(fieldName, mapperType)
                    tag?.let { parameter.addAnnotation(it.toTagAnnotation()) }
                    constructor.addParameter(parameter.build())
                    fieldName
                }
                implementation.addStatement("return %N.map(client, %S, defaultValue, context)", field, flag.key)
            }
            impl.addFunction(implementation.build())
        }
        val args = if (mappers.isEmpty()) "" else ", " + mappers.values.joinToString(", ")
        if (source.getAllFunctions().any { isWithContext(it) }) {
            impl.addFunction(FunSpec.builder("withContext").addModifiers(KModifier.OVERRIDE)
                .addParameter("context", context).returns(sourceType)
                .addStatement("return %T(client, config, context%L)", implName, args).build())
        }
        impl.primaryConstructor(constructor.build())
        val prefix = source.generatedClassName("Factory").removePrefix("$").removeSuffix("_Factory")
            .replaceFirstChar { it.lowercaseChar() }
        val factory = FunSpec.builder(prefix + "FlagSource").addParameter(clientParameter(clientTag))
            .addParameter("config", configName).returns(sourceType)
        for ((key, field) in mappers) {
            val parameter = ParameterSpec.builder(field, key.type)
            key.tag?.let { parameter.addAnnotation(it.toTagAnnotation()) }
            factory.addParameter(parameter.build())
        }
        factory.addStatement("return %T(client, config, %T()%L)", implName, immutableContext, args)
        val registration = FunSpec.builder(prefix + "FlagRegistration").returns(registrar)
        clientTag?.let { registration.addAnnotation(it.toTagAnnotation()) }
        val entries = CodeBlock.builder().add("return %T { mapOf(", registrar)
        flags.forEachIndexed { index, flag ->
            if (index > 0) entries.add(", ")
            entries.add("%S to %T.%L", flag.key, flagType, flag.sdkType)
        }
        entries.addStatement(") }")
        registration.addCode(entries.build())
        val module = TypeSpec.interfaceBuilder(moduleName)
            .generated(OpenfeatureSymbolProcessor::class).addOriginatingKSFile(source)
            .addAnnotation(CommonClassNames.module).addFunction(factory.build()).addFunction(registration.build())
        for (type in listOf(config.build(), impl.build(), module.build())) {
            FileSpec.builder(packageName, type.name!!).addType(type).build().writeTo(environment.codeGenerator, false)
        }
    }

    private fun clientParameter(tag: String?): ParameterSpec {
        val parameter = ParameterSpec.builder("client", client)
        tag?.let { parameter.addAnnotation(it.toTagAnnotation()) }
        return parameter.build()
    }

    private fun isWithContext(method: KSFunctionDeclaration): Boolean =
        method.simpleName.asString() == "withContext" && method.parameters.size == 1 &&
            method.parameters[0].type.resolve().declaration.qualifiedName?.asString() == context.canonicalName

    private fun flags(source: KSClassDeclaration, path: String): List<Flag> {
        val sourceType = source.asType(emptyList())
        val seen = hashSetOf<String>()
        val keys = hashSetOf<String>()
        return source.getAllFunctions().mapNotNull { method ->
            val name = method.simpleName.asString()
            if (name in setOf("equals", "hashCode", "toString") || Modifier.PRIVATE in method.modifiers
                || Modifier.JAVA_STATIC in method.modifiers || isWithContext(method)) return@mapNotNull null
            val type = method.asMemberOf(sourceType).returnType!!
            if (method.parameters.isNotEmpty() || type.declaration.qualifiedName?.asString() == "kotlin.Unit") {
                if (!method.isAbstract) return@mapNotNull null
                throw ProcessingErrorException("OpenFeature flag methods must have no arguments and return a value", method)
            }
            if (method.typeParameters.isNotEmpty() || Modifier.SUSPEND in method.modifiers) {
                throw ProcessingErrorException("OpenFeature flag methods must be synchronous and have no type parameters", method)
            }
            if (type.nullability == Nullability.NULLABLE) {
                throw ProcessingErrorException("OpenFeature flag methods must return non-null values", method)
            }
            if (!seen.add(name)) return@mapNotNull null
            val typeName = type.toTypeName().copy(nullable = false)
            val mapping = method.parseMappingData().getMapping(mapper)
            val typeString = typeName.toString()
            var sdkType = when (typeString) {
                "kotlin.Boolean" -> "BOOLEAN"
                "kotlin.String" -> "STRING"
                "kotlin.Int" -> "INTEGER"
                "kotlin.Long" -> "LONG"
                "kotlin.Double", "kotlin.Float" -> "DOUBLE"
                "java.math.BigDecimal", "java.math.BigInteger", "java.util.UUID", "java.util.regex.Pattern",
                "io.koraframework.common.util.Size" -> "STRING"
                else -> if (typeString.startsWith("java.time.") || (type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS)
                    "STRING" else "OBJECT"
            }
            val sdkMethod = when (typeString) {
                "kotlin.Boolean" -> "getBooleanValue"
                "kotlin.String" -> "getStringValue"
                "kotlin.Int" -> "getIntegerValue"
                "kotlin.Long" -> "getLongValue"
                "kotlin.Double" -> "getDoubleValue"
                "dev.openfeature.sdk.Value" -> "getObjectValue"
                else -> null
            }
            method.findAnnotation(typeAnnotation)?.let { annotation ->
                val enumValue = annotation.findValueNoDefault<Any>("value")!!
                val override = when (enumValue) {
                    is KSType -> enumValue.declaration.simpleName.asString()
                    is KSClassDeclaration -> enumValue.simpleName.asString()
                    else -> enumValue.toString().substringAfterLast('.')
                }
                if (override != sdkType && mapping == null) {
                    throw ProcessingErrorException("@OpenfeatureType requires a custom mapper when changing the flag type", method)
                }
                sdkType = override
            }
            val key = method.findAnnotation(keyAnnotation)?.findValueNoDefault<String>("value")
                ?: if (path.isEmpty()) name else "$path.$name"
            if (key.isBlank()) {
                throw ProcessingErrorException("OpenFeature flag key must not be blank", method)
            }
            if (!keys.add(key)) {
                throw ProcessingErrorException("Duplicate OpenFeature flag key: $key", method)
            }
            Flag(method, typeName, key, sdkType, sdkMethod, mapping)
        }.toList()
    }
}
