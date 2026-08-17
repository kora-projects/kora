package io.koraframework.json.ksp.reader

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import io.koraframework.json.ksp.*
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.collectFinalSealedSubtypes
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.toTypeName
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.*

class SealedInterfaceReaderGenerator {
    fun generateSealedReader(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val typeParameterResolver = jsonClassDeclaration.typeParameters.toTypeParameterResolver()
        val subclasses = jsonClassDeclaration.collectFinalSealedSubtypes().toList()
        val typeArgMap = detectSealedHierarchyTypeVariables(jsonClassDeclaration, subclasses)

        val typeName = jsonClassDeclaration.toTypeName()
        val readerInterface = JsonTypes.jsonReader.parameterizedBy(typeName)

        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonReaderName())
            .generated(SealedInterfaceReaderGenerator::class)
            .addSuperinterface(readerInterface)
            .addModifiers(KModifier.PUBLIC)
            .addOriginatingKSFile(jsonClassDeclaration)

        jsonClassDeclaration.typeParameters.forEach {
            val typeVariableName = it.toTypeVariableName(typeParameterResolver)
            typeBuilder.addTypeVariable(TypeVariableName.invoke(typeVariableName.name, typeVariableName.bounds, null))
        }

        addReaders(typeBuilder, subclasses, typeArgMap)

        val discriminator = jsonClassDeclaration.discriminator()
            ?: throw ProcessingErrorException(
                """
                JsonReader can't be generated for sealed hierarchy:
                  ${jsonClassDeclaration.qualifiedName?.asString()}

                Problem:
                  Sealed JSON hierarchy has no @JsonDiscriminatorField annotation.

                Hint:
                  The discriminator field tells the generated reader which subtype should be created for incoming JSON.

                Fix:
                  Add @JsonDiscriminatorField to the sealed root type, or provide a custom JsonReader<${jsonClassDeclaration.qualifiedName?.asString()}> component.
                """.trimIndent(),
                jsonClassDeclaration
            )
        val discriminatorField = discriminator.field
        val function = FunSpec.builder("read")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("_parser", JsonTypes.jsonParser)
            .returns(typeName.copy(nullable = true))
        function.controlFlow("if (_parser.currentToken() == %T.VALUE_NULL)", JsonTypes.jsonToken) {
            addStatement("return null")
        }
        function.addCode("val bufferingParser = %T(_parser)\n", JsonTypes.bufferingJsonParser)

        val typeSimpleName = jsonClassDeclaration.simpleName.asString()
        val allValues = subclasses.flatMap { elem -> elem.discriminatorValues() }.toList()
        if (discriminator.defaultValue.isNullOrEmpty()) {
            function.addCode("val discriminator = %T.readStringDiscriminator(bufferingParser, %S)\n", JsonTypes.discriminatorHelper, discriminatorField);
            function.addCode(
                "if (discriminator == null) throw %T(_parser, %S + _jsonPath(_parser) + %S)\n",
                JsonTypes.jsonParseException,
                "Failed to read json $typeSimpleName: missing required discriminator field \"$discriminatorField\", expected one of $allValues (at ",
                ")"
            )
        } else {
            function.addCode("val discriminator = %T.readStringDiscriminator(bufferingParser, %S) ?: %S\n", JsonTypes.discriminatorHelper, discriminatorField, discriminator.defaultValue);
        }
        function.addCode("val bufferedParser = %T.createFlattened(false, bufferingParser.reset(), _parser)\n", JsonTypes.jsonParserSequence)
        function.addCode("bufferedParser.nextToken()\n")
        function.beginControlFlow("return when(discriminator) {")
        subclasses.forEach { elem ->
            val readerName = getReaderFieldName(elem)
            val requiredDiscriminatorValues = elem.discriminatorValues()
            for (requiredDiscriminatorValue in requiredDiscriminatorValues) {
                function.addCode(
                    "%S -> %L.read(bufferedParser)\n",
                    requiredDiscriminatorValue,
                    readerName
                )
            }
        }
        function.addCode(
            "else -> throw %T(_parser, %S + discriminator + %S + _jsonPath(_parser) + %S)",
            JsonTypes.jsonParseException,
            "Failed to read json $typeSimpleName: unknown discriminator value \"",
            "\" for field \"$discriminatorField\", expected one of $allValues (at ",
            ")"
        )
        function.endControlFlow()
        typeBuilder.addFunction(function.build())

        typeBuilder.addFunction(
            FunSpec.builder("_jsonPath")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("_parser", JsonTypes.jsonParser)
                .returns(String::class)
                .addStatement("val _p = _parser.streamReadContext().pathAsPointer().toString()")
                .addStatement("return if (_p.isEmpty()) %S else _p", "<root>")
                .build()
        )
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
