package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.toTypeName

class SchedulingDbJsonCodecSymbolProcessor(
    private val env: SymbolProcessorEnvironment
) : BaseSymbolProcessor(env) {
    private val schedulingDbJsonCodec = "io.koraframework.scheduling.db.annotation.SchedulingDbJsonCodec"
    private val json = "io.koraframework.json.common.annotation.Json"
    private val schedulingDbCodec = ClassName("io.koraframework.scheduling.db", "SchedulingDbCodec")
    private val jsonReader = ClassName("io.koraframework.json.common", "JsonReader")
    private val jsonWriter = ClassName("io.koraframework.json.common", "JsonWriter")

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(schedulingDbJsonCodec)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { this.generateJsonCodecModule(it) }
        return emptyList()
    }

    private fun generateJsonCodecModule(type: KSClassDeclaration) {
        if (type.annotations.none { it.annotationType.resolve().declaration.qualifiedName?.asString() == json }) {
            throw IllegalArgumentException("@SchedulingDbJsonCodec requires @Json on the same type")
        }

        val typeName = type.toTypeName()
        val codecType = schedulingDbCodec.parameterizedBy(typeName)
        val typeRefType = CommonClassNames.typeRef.parameterizedBy(typeName)
        val readerType = jsonReader.parameterizedBy(typeName)
        val writerType = jsonWriter.parameterizedBy(typeName)
        val methodName = type.simpleName.asString().replaceFirstChar { it.lowercaseChar() } + "SchedulingDbCodec"

        val codec = TypeSpec.anonymousClassBuilder()
            .addSuperinterface(codecType)
            .addFunction(
                FunSpec.builder("typeRef")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(typeRefType)
                    .addStatement("return typeRef")
                    .build()
            )
            .addFunction(
                FunSpec.builder("serialize")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(BYTE_ARRAY)
                    .addParameter("value", typeName.copy(nullable = true))
                    .addStatement("return writer.toByteArray(value)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("deserialize")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(typeName.copy(nullable = true))
                    .addParameter("bytes", BYTE_ARRAY)
                    .addStatement("return reader.read(bytes)")
                    .build()
            )
            .build()

        val module = TypeSpec.interfaceBuilder("\$${type.simpleName.asString()}_SchedulingDbJsonCodecModule")
            .generated(SchedulingDbJsonCodecSymbolProcessor::class)
            .addAnnotation(CommonClassNames.module)
            .addFunction(
                FunSpec.builder(methodName)
                    .addAnnotation(CommonClassNames.defaultComponent)
                    .returns(codecType)
                    .addParameter("typeRef", typeRefType)
                    .addParameter("reader", readerType)
                    .addParameter("writer", writerType)
                    .addStatement("return %L", codec)
                    .build()
            )
            .build()

        FileSpec.get(type.packageName.asString(), module).writeTo(env.codeGenerator, false, listOf(type.containingFile!!))
    }
}

class SchedulingDbJsonCodecSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = SchedulingDbJsonCodecSymbolProcessor(environment)
}
