package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.detectSealedHierarchyTypeVariables
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.collectFinalSealedSubtypes
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import java.util.*

class SealedInterfaceWriterGenerator {

    fun generateSealedWriter(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val typeParameterResolver = jsonClassDeclaration.typeParameters.toTypeParameterResolver()
        val subclasses = jsonClassDeclaration.collectFinalSealedSubtypes().toList()
        val typeArgMap = detectSealedHierarchyTypeVariables(jsonClassDeclaration, subclasses)
        val typeName = jsonClassDeclaration.toTypeName()
        val writerInterface = JsonTypes.jsonWriter.parameterizedBy(typeName)
        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonWriterName())
            .generated(JsonWriterGenerator::class)
            .addSuperinterface(writerInterface)

        jsonClassDeclaration.containingFile?.let { typeBuilder.addOriginatingKSFile(it) }
        jsonClassDeclaration.typeParameters.forEach {
            val typeVariableName = it.toTypeVariableName(typeParameterResolver)
            typeBuilder.addTypeVariable(TypeVariableName.invoke(typeVariableName.name, typeVariableName.bounds, null))
        }

        addWriters(typeBuilder, subclasses, typeArgMap)

        val function = FunSpec.builder("write")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("_gen", JsonTypes.jsonGenerator)
            .addParameter("_object", typeName.copy(nullable = true))
        function.controlFlow("when (_object)") {
            addCode("null -> _gen.writeNull()\n")
            subclasses.forEach { sealedSub ->
                val writerName = getWriterFieldName(sealedSub)
                val subtypeTypeName = sealedSub.toTypeName(sealedSub.typeParameters.map { typeArgMap[it] ?: STAR })
                addCode("is %T -> %L.write(_gen, _object)\n", subtypeTypeName, writerName)
            }
        }
        typeBuilder.addFunction(function.build())
        return typeBuilder.build()
    }

    private fun addWriters(typeBuilder: TypeSpec.Builder, jsonElements: List<KSClassDeclaration>, typeArgMap: IdentityHashMap<KSTypeParameter, TypeName>) {
        val constructor = FunSpec.constructorBuilder()
        jsonElements.forEach { sealedSub ->
            val fieldName = getWriterFieldName(sealedSub)
            val subtypeTypeName = sealedSub.toTypeName(sealedSub.typeParameters.map { typeArgMap[it] ?: STAR })
            val fieldType = JsonTypes.jsonWriter.parameterizedBy(subtypeTypeName)
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
