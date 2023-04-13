package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.detectSealedHierarchyTypeVariables
import ru.tinkoff.kora.json.ksp.discriminatorField
import ru.tinkoff.kora.json.ksp.jsonReaderName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.KspCommonUtils.collectFinalSealedSubtypes
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.*

class SealedInterfaceReaderGenerator(private val resolver: Resolver, logger: KSPLogger) {
    fun generateSealedReader(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val subclasses = jsonClassDeclaration.collectFinalSealedSubtypes().toList()
        val (typeArgMap, readerTypeVariables) = detectSealedHierarchyTypeVariables(jsonClassDeclaration, subclasses)

        val typeName = if (jsonClassDeclaration.typeParameters.isEmpty())
            jsonClassDeclaration.toClassName() else
            jsonClassDeclaration.toClassName().parameterizedBy(readerTypeVariables)

        val readerInterface = JsonTypes.jsonReader.parameterizedBy(typeName)

        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonReaderName())
            .generated(SealedInterfaceReaderGenerator::class)
            .addSuperinterface(readerInterface)
            .addModifiers(KModifier.PUBLIC)
        jsonClassDeclaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }

        readerTypeVariables.forEach {
            if (it is TypeVariableName) {
                typeBuilder.addTypeVariable(it)
            }
        }

        addReaders(typeBuilder, subclasses, typeArgMap)

        val discriminatorField = jsonClassDeclaration.discriminatorField(resolver)
            ?: throw ProcessingErrorException("Sealed interface should have @JsonDiscriminatorField annotation", jsonClassDeclaration)
        val function = FunSpec.builder("read")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("_parser", JsonTypes.jsonParser)
            .returns(typeName.copy(nullable = true))
        function.addCode("val bufferedParser = %T(_parser)\n", JsonTypes.bufferedParserWithDiscriminator)
        function.addCode("val discriminator = bufferedParser.getDiscriminator(%S) ", discriminatorField)
        function.addCode("?: throw %T(_parser, %S)\n", JsonTypes.jsonParseException, "Discriminator required, but not provided")
        function.addCode("bufferedParser.resetPosition()\n")
        function.beginControlFlow("return when(discriminator) {")
        subclasses.forEach { elem ->
            val readerName = getReaderFieldName(elem)
            val requiredDiscriminatorValue = elem.findAnnotation(JsonTypes.jsonDiscriminatorValue)
                ?.findValue<String>("value")
                ?: elem.simpleName.asString()
            function.addCode(
                "%S -> %L.read(bufferedParser)\n",
                requiredDiscriminatorValue,
                readerName
            )
        }
        function.addCode("else -> throw %T(_parser, %S)", JsonTypes.jsonParseException, "Unknown discriminator")
        function.endControlFlow()
        typeBuilder.addFunction(function.build())
        return typeBuilder.build()
    }


    private fun addReaders(typeBuilder: TypeSpec.Builder, jsonElements: List<KSClassDeclaration>, typeArgMap: IdentityHashMap<KSTypeParameter, TypeName>) {
        val constructor = FunSpec.constructorBuilder()
        jsonElements.forEach { sealedSub ->
            val fieldName = getReaderFieldName(sealedSub)
            val subtypeTypeName = sealedSub.toTypeName(sealedSub.typeParameters.map { typeArgMap[it] ?: STAR })
            val fieldType = JsonTypes.jsonReader.parameterizedBy(subtypeTypeName)
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
