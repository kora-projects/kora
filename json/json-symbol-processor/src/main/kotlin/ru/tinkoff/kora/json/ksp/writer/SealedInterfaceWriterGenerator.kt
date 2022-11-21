package ru.tinkoff.kora.json.ksp.writer

import com.fasterxml.jackson.core.JsonGenerator
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import javax.annotation.processing.Generated

@OptIn(KspExperimental::class)
class SealedInterfaceWriterGenerator(
    resolver: Resolver,
    kspLogger: KSPLogger
) {
    private val writerTypeMetaParser = WriterTypeMetaParser(resolver, kspLogger)

    private val writerErasure = resolver.getClassDeclarationByName(JsonWriter::class.qualifiedName!!)!!.asStarProjectedType()

    fun generateSealedWriter(jsonTypeRef: KSTypeReference, jsonElements: List<KSClassDeclaration>): List<TypeSpec> {
        val meta = writerTypeMetaParser.parse(jsonTypeRef) ?: throw ProcessingErrorException("Can't parse json meta", jsonTypeRef)
        val typesToProcess = mutableListOf(meta)
        if (meta.type.isMarkedNullable) {
            typesToProcess.add(meta.copy(type = meta.type.makeNotNullable()))
        } else {
            typesToProcess.add(meta.copy(type = meta.type.makeNullable()))
        }
        return typesToProcess.map { m -> generateSealedWriterTypeSpec(m, jsonElements) }
    }

    private fun generateSealedWriterTypeSpec(
        meta: JsonClassWriterMeta,
        jsonElements: List<KSClassDeclaration>
    ): TypeSpec {
        val jsonClassDeclaration = meta.type.declaration as KSClassDeclaration
        val typeParameterResolver = jsonClassDeclaration.typeParameters.toTypeParameterResolver()
        val objectType = jsonClassDeclaration.asStarProjectedType().toTypeName(typeParameterResolver)
        val writerInterface = writerErasure.toClassName().parameterizedBy(objectType)
        val typeName = jsonWriterName(meta.type)
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addAnnotation(
                AnnotationSpec.builder(Generated::class)
                    .addMember(CodeBlock.of("%S", SealedInterfaceWriterGenerator::class.java.canonicalName))
                    .build()
            )
            .addSuperinterface(writerInterface)
            .addModifiers(KModifier.PUBLIC)
            .addOriginatingKSFile(jsonClassDeclaration.containingFile!!)


        jsonClassDeclaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName())
        }

        addWriters(typeBuilder, jsonElements)
        val function = FunSpec.builder("write")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("_gen", JsonGenerator::class)
            .addParameter("_object", objectType.copy(nullable = true))
        jsonElements.forEach { sealedSub ->
            val typeParameterResolver = sealedSub.typeParameters.toTypeParameterResolver()
            val writerName = getWriterFieldName(sealedSub)
            function.addCode("if (_object is %T) {\n%L.write(_gen, _object)\n} else ", sealedSub.asStarProjectedType().toTypeName(typeParameterResolver), writerName)
        }
        function.addCode("throw %T(%S)", IllegalStateException::class, "Unsupported class")
        typeBuilder.addFunction(function.build())
        return typeBuilder.build()
    }

    private fun addWriters(typeBuilder: TypeSpec.Builder, jsonElements: List<KSClassDeclaration>) {
        val constructor = FunSpec.constructorBuilder()
        jsonElements.forEach { sealedSub ->
            val fieldName = getWriterFieldName(sealedSub)
            val typeParameterResolver = sealedSub.typeParameters.toTypeParameterResolver()
            val fieldType = writerErasure.toClassName().parameterizedBy(sealedSub.asStarProjectedType().toTypeName(typeParameterResolver))
            val readerField = PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE)
            constructor.addParameter(fieldName, fieldType)
            constructor.addStatement("this.%L = %L", fieldName, fieldName)
            typeBuilder.addProperty(readerField.build())
        }
        typeBuilder.primaryConstructor(constructor.build())
    }

    private fun getWriterFieldName(elem: KSClassDeclaration): String {
        return elem.simpleName.asString().replaceFirstChar { it.lowercaseChar() } + "Writer"
    }
}
