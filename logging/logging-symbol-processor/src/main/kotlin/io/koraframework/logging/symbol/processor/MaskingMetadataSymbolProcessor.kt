package io.koraframework.logging.symbol.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.CommonClassNames.isCollection
import io.koraframework.ksp.common.CommonClassNames.isMap
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.getNameConverter
import io.koraframework.ksp.common.generatedClassName

class MaskingMetadataSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    private val processed = HashSet<String>()
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val delayed = ArrayList<KSAnnotated>()
        for (symbol in resolver.getSymbolsWithAnnotation(LoggingTypes.mask.canonicalName)) {
            if (!symbol.validateAll()) {
                delayed.add(symbol)
                continue
            }
            if (symbol !is KSClassDeclaration) {
                continue
            }
            if (symbol.modifiers.contains(Modifier.ABSTRACT)) {
                kspLogger.error("Abstract classes can't be annotated with @Mask", symbol)
                continue
            }
            if (!processed.add(symbol.qualifiedName!!.asString())) {
                continue
            }
            MaskingMetadataGenerator(resolver, codeGenerator).generate(symbol)
        }
        return delayed
    }
}

private class MaskingMetadataGenerator(
    private val resolver: Resolver,
    private val codeGenerator: CodeGenerator
) {
    fun generate(root: KSClassDeclaration) {
        val packageName = root.packageName.asString()
        val className = root.generatedClassName("MaskingMetadata")
        if (resolver.getClassDeclarationByName(resolver.getKSNameFromString("$packageName.$className")) != null) {
            return
        }

        val visited = linkedMapOf<String, MaskingClassMeta>()
        visit(root, visited)

        val metadataType = LoggingTypes.maskingMetadata.parameterizedBy(root.toClassName())
        val type = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(CommonClassNames.component)
            .generated(MaskingMetadataGenerator::class)
            .addOriginatingKSFile(root)
            .addSuperinterface(metadataType)
            .primaryConstructor(FunSpec.constructorBuilder().build())
            .addProperty(
                PropertySpec.builder(
                    "metadata",
                    Map::class.asClassName().parameterizedBy(Class::class.asClassName().parameterizedBy(STAR), LoggingTypes.maskingClassMeta),
                    KModifier.PRIVATE
                ).initializer(metadataCode(visited.values)).build()
            )
            .addFunction(
                FunSpec.builder("metadata")
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .addParameter("type", Class::class.asClassName().parameterizedBy(STAR))
                    .returns(LoggingTypes.maskingClassMeta.copy(nullable = true))
                    .addStatement("return this.metadata[type]")
                    .build()
            )
            .build()

        FileSpec.builder(packageName, className)
            .addType(type)
            .build()
            .writeTo(codeGenerator = codeGenerator, aggregating = false)
    }

    private fun metadataCode(metas: Collection<MaskingClassMeta>): CodeBlock {
        val keyType = Class::class.asClassName().parameterizedBy(STAR)
        if (metas.isEmpty()) {
            return CodeBlock.of("java.util.Map.of<%T, %T>()", keyType, LoggingTypes.maskingClassMeta)
        }
        val code = CodeBlock.builder().add("java.util.Map.ofEntries<%T, %T>(\n", keyType, LoggingTypes.maskingClassMeta).indent()
        val iterator = metas.iterator()
        while (iterator.hasNext()) {
            val meta = iterator.next()
            code.add("java.util.Map.entry<%T, %T>(%T::class.java, %T(%L))", keyType, LoggingTypes.maskingClassMeta, meta.type.toClassName(), LoggingTypes.maskingClassMeta, fieldsCode(meta))
            if (iterator.hasNext()) {
                code.add(",\n")
            }
        }
        return code.unindent().add("\n)").build()
    }

    private fun fieldsCode(meta: MaskingClassMeta): CodeBlock {
        val fields = ArrayList<CodeBlock>()
        val typeMask = meta.type.findAnnotation(LoggingTypes.mask)
        for (field in meta.fields) {
            val mask = field.mask
            if (mask != null) {
                fields.add(CodeBlock.of("%S, %T.mask(%L)", field.jsonName, LoggingTypes.maskingFieldMeta, maskRuleCode(mask, typeMask)))
                continue
            }
            val nestedType = nestedType(field.type) ?: continue
            val code = when {
                field.type.isCollection() -> CodeBlock.of("%S, %T.collection(%T::class.java)", field.jsonName, LoggingTypes.maskingFieldMeta, nestedType.toClassName())
                field.type.isMap() -> CodeBlock.of("%S, %T.mapValue(%T::class.java)", field.jsonName, LoggingTypes.maskingFieldMeta, nestedType.toClassName())
                else -> CodeBlock.of("%S, %T.object(%T::class.java)", field.jsonName, LoggingTypes.maskingFieldMeta, nestedType.toClassName())
            }
            fields.add(code)
        }
        if (fields.isEmpty()) {
            return CodeBlock.of("java.util.Map.of<%T, %T>()", String::class.asClassName(), LoggingTypes.maskingFieldMeta)
        }

        val code = CodeBlock.builder().add("java.util.Map.ofEntries<%T, %T>(\n", String::class.asClassName(), LoggingTypes.maskingFieldMeta).indent()
        for (i in fields.indices) {
            code.add("java.util.Map.entry<%T, %T>(%L)", String::class.asClassName(), LoggingTypes.maskingFieldMeta, fields[i])
            if (i < fields.size - 1) {
                code.add(",\n")
            }
        }
        return code.unindent().add("\n)").build()
    }

    private fun maskRuleCode(mask: KSAnnotation, typeMask: KSAnnotation?): CodeBlock {
        val value = mask.findValueNoDefault<String>("value")
            ?: typeMask?.findValue<String>("value")
            ?: "***"
        val mode = mask.findValueNoDefault<KSClassDeclaration>("mode")
            ?: typeMask?.findValue<KSClassDeclaration>("mode")
        val modeName = mode?.simpleName?.asString() ?: "FULL"
        val keep = mask.findValueNoDefault<Int>("keep")
            ?: typeMask?.findValue<Int>("keep")
            ?: 4
        return CodeBlock.of("%T.replacement(%S, %T.%L, %L)", LoggingTypes.maskRule, value, LoggingTypes.maskMode, modeName, keep)
    }

    private fun visit(type: KSClassDeclaration, visited: MutableMap<String, MaskingClassMeta>) {
        val key = type.qualifiedName!!.asString()
        if (visited.containsKey(key) || !type.isJsonOrMasked()) {
            return
        }
        val meta = parse(type)
        visited[key] = meta
        for (field in meta.fields) {
            if (field.mask != null) {
                continue
            }
            for (nested in nestedTypes(field.type)) {
                visit(nested, visited)
            }
        }
    }

    private fun parse(type: KSClassDeclaration): MaskingClassMeta {
        val nameConverter = type.getNameConverter()
        val fields = ArrayList<MaskingField>()
        for (property in type.getAllProperties()) {
            if (property.isAnnotationPresent(LoggingTypes.jsonSkip)) {
                continue
            }
            val constructorParameter = type.primaryConstructor?.parameters?.firstOrNull { it.name?.asString() == property.simpleName.asString() }
            val jsonField = property.findAnnotation(LoggingTypes.jsonField) ?: constructorParameter?.findAnnotation(LoggingTypes.jsonField)
            val jsonName = jsonField?.findValueNoDefault<String>("value")?.takeIf { it.isNotBlank() }
                ?: nameConverter?.convert(property.simpleName.asString())
                ?: property.simpleName.asString()
            val mask = property.findAnnotation(LoggingTypes.mask) ?: constructorParameter?.findAnnotation(LoggingTypes.mask)
            fields.add(MaskingField(jsonName, property.type.resolve(), mask))
        }
        return MaskingClassMeta(type, fields)
    }

    private fun nestedType(type: KSType): KSClassDeclaration? {
        val nested = nestedTypes(type)
        return if (nested.size == 1) nested[0] else null
    }

    private fun nestedTypes(type: KSType): List<KSClassDeclaration> {
        if (type.isCollection()) {
            val argument = type.arguments.firstOrNull()?.type?.resolve() ?: return emptyList()
            return nestedTypes(argument)
        }
        if (type.isMap()) {
            val argument = type.arguments.getOrNull(1)?.type?.resolve() ?: return emptyList()
            return nestedTypes(argument)
        }
        val declaration = type.declaration
        if (declaration !is KSClassDeclaration || !declaration.isJsonOrMasked()) {
            return emptyList()
        }
        return listOf(declaration)
    }

    private fun KSClassDeclaration.isJsonOrMasked(): Boolean {
        return this.isAnnotationPresent(LoggingTypes.json)
            || this.isAnnotationPresent(LoggingTypes.jsonWriter)
            || this.isAnnotationPresent(LoggingTypes.mask)
    }
}

private object LoggingTypes {
    val mask = ClassName("io.koraframework.logging.common.annotation", "Mask")
    val maskMode = mask.nestedClass("Mode")
    val json = ClassName("io.koraframework.json.common.annotation", "Json")
    val jsonWriter = ClassName("io.koraframework.json.common.annotation", "JsonWriter")
    val jsonField = ClassName("io.koraframework.json.common.annotation", "JsonField")
    val jsonSkip = ClassName("io.koraframework.json.common.annotation", "JsonSkip")
    val maskingMetadata = ClassName("io.koraframework.logging.common.masking", "MaskingMetadata")
    val maskingClassMeta = ClassName("io.koraframework.logging.common.masking", "MaskingClassMeta")
    val maskingFieldMeta = ClassName("io.koraframework.logging.common.masking", "MaskingFieldMeta")
    val maskRule = ClassName("io.koraframework.logging.common.masking", "MaskRule")
}

private data class MaskingClassMeta(val type: KSClassDeclaration, val fields: List<MaskingField>)

private data class MaskingField(val jsonName: String, val type: KSType, val mask: KSAnnotation?)
