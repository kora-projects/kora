package ru.tinkoff.kora.json.ksp.reader

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import ru.tinkoff.kora.json.common.BufferedParserWithDiscriminator
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorValue
import ru.tinkoff.kora.json.ksp.KnownType
import ru.tinkoff.kora.json.ksp.jsonReaderName
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import javax.annotation.processing.Generated

@KspExperimental
class SealedInterfaceReaderGenerator(private val resolver: Resolver, logger: KSPLogger) {
    private val readerErasure: KSType = resolver.getClassDeclarationByName(JsonReader::class.qualifiedName!!)!!.asStarProjectedType()
    private val readerTypeMetaParser = ReaderTypeMetaParser(resolver, KnownType(resolver), logger)

    fun generateSealedReader(jsonElement: KSTypeReference, jsonElements: List<KSDeclaration>): List<TypeSpec> {
        val meta = readerTypeMetaParser.parse(jsonElement, true) ?: throw ProcessingErrorException("Can't parse json meta", jsonElement)

        val typesToProcess = mutableSetOf(meta)
        if (meta.type.isMarkedNullable) {
            typesToProcess.add(meta.copy(type = meta.type.makeNotNullable()))
        } else {
            typesToProcess.add(meta.copy(type = meta.type.makeNullable()))
        }
        return typesToProcess.map { m -> generateSealedReaderTypeSpec(m, jsonElements) }
    }

    private fun generateSealedReaderTypeSpec(
        meta: JsonClassReaderMeta,
        jsonElements: List<KSDeclaration>
    ): TypeSpec {
        val readerInterface = readerErasure.toClassName().parameterizedBy(meta.type.toTypeName())
        val typeName = jsonReaderName(meta.type)
        val typeParameterResolver = meta.type.declaration.typeParameters.toTypeParameterResolver()
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addAnnotation(
                AnnotationSpec.builder(Generated::class)
                    .addMember(CodeBlock.of("%S", SealedInterfaceReaderGenerator::class.java.canonicalName))
                    .build()
            )
            .addSuperinterface(readerInterface)
            .addModifiers(KModifier.PUBLIC)
            .addOriginatingKSFile(meta.type.declaration.containingFile!!)

        meta.type.declaration.typeParameters.forEach {
            typeBuilder.addTypeVariable(it.toTypeVariableName())
        }

        addReaders(meta, typeBuilder, jsonElements, typeParameterResolver)
        val discriminatorField = meta.discriminatorField ?: throw ProcessingErrorException(
            "Unspecified discriminator field for sealed interface, please use @JsonDiscriminatorField annotation",
            meta.type.declaration
        )
        val function = FunSpec.builder("read")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("_parser", JsonParser::class)
            .returns(meta.type.toTypeName())
        function.addCode("val bufferedParser = %T(_parser)\n", BufferedParserWithDiscriminator::class)
        function.addCode("val discriminator = bufferedParser.getDiscriminator(%S) ", discriminatorField)
        function.addCode("?: throw %T(_parser, %S)\n", JsonParseException::class, "Discriminator required, but not provided")
        function.addCode("bufferedParser.resetPosition()\n")
        function.beginControlFlow("return when(discriminator) {")
        jsonElements.forEach { elem ->
            val readerName = getReaderFieldName(elem)
            val discriminatorValueAnnotation = elem.getAnnotationsByType(JsonDiscriminatorValue::class).firstOrNull()
            val requiredDiscriminatorValue = discriminatorValueAnnotation?.value ?: elem.simpleName.asString()
            function.addCode(
                "%S -> %L.read(bufferedParser)%L\n",
                requiredDiscriminatorValue,
                readerName,
                if (meta.type.isMarkedNullable) "" else "!!"
            )
        }
        function.addCode("else -> throw %T(_parser, %S)", JsonParseException::class.java, "Unknown discriminator")
        function.endControlFlow()
        typeBuilder.addFunction(function.build())
        return typeBuilder.build()
    }

    private fun addReaders(meta: JsonClassReaderMeta, typeBuilder: TypeSpec.Builder, jsonElements: List<KSDeclaration>, typeParameterResolver: TypeParameterResolver) {
        val constructor = FunSpec.constructorBuilder()
        jsonElements.forEach { elem ->
            val elementType = if (meta.type.isMarkedNullable) {
                (elem as KSClassDeclaration).asStarProjectedType().makeNullable()
            } else {
                (elem as KSClassDeclaration).asStarProjectedType().makeNotNullable()
            }
            val fieldName = getReaderFieldName(elem)
            val fieldType = ClassName(
                JsonReader::class.java.packageName,
                JsonReader::class.simpleName!!
            ).parameterizedBy(elementType.toTypeName(typeParameterResolver))

            val readerField = PropertySpec.builder(fieldName, fieldType, KModifier.PRIVATE)
            constructor.addParameter(fieldName, fieldType)
            constructor.addStatement("this.%L = %L", fieldName, fieldName)
            typeBuilder.addProperty(readerField.build())
        }
        typeBuilder.primaryConstructor(constructor.build())
    }

    private fun getReaderFieldName(elem: KSDeclaration): String {
        return elem.simpleName.asString().replaceFirstChar { it.lowercaseChar() } + "Reader"
    }
}
