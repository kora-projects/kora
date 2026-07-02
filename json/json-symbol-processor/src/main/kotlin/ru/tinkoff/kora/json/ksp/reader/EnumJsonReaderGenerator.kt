package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.jsonReaderName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

class EnumJsonReaderGenerator {
    fun generateEnumReader(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val className = jsonClassDeclaration.toClassName()
        val typeName = jsonClassDeclaration.toTypeName()

        val factory = detectReaderFactory(jsonClassDeclaration)
        if (factory != null) {
            return generateFactoryReader(jsonClassDeclaration, className, typeName, factory)
        }

        val enumType = detectValueType(jsonClassDeclaration)

        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonReaderName())
            .generated(JsonReaderGenerator::class)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("valueReader", JsonTypes.jsonReader.parameterizedBy(enumType.type))
                    .build()
            )
            .addSuperinterface(
                JsonTypes.jsonReader.parameterizedBy(typeName),
                CodeBlock.of("%T(%T.values(), %T::%N, valueReader)", JsonTypes.enumJsonReader, className, className, enumType.accessor)
            )
            .addOriginatingKSFile(jsonClassDeclaration)
        return typeBuilder.build()
    }

    data class ReaderFactory(val methodName: String, val valueType: TypeName)

    private fun generateFactoryReader(
        declaration: KSClassDeclaration,
        className: ClassName,
        typeName: TypeName,
        factory: ReaderFactory
    ): TypeSpec {
        val valueReaderType = JsonTypes.jsonReader.parameterizedBy(factory.valueType)
        return TypeSpec.classBuilder(declaration.jsonReaderName())
            .generated(JsonReaderGenerator::class)
            .addSuperinterface(JsonTypes.jsonReader.parameterizedBy(typeName))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("valueReader", valueReaderType)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("valueReader", valueReaderType, KModifier.PRIVATE)
                    .initializer("valueReader")
                    .build()
            )
            .addFunction(
                FunSpec.builder("read")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("parser", JsonTypes.jsonParser)
                    .returns(typeName.copy(nullable = true))
                    .addStatement("val value = valueReader.read(parser) ?: return null")
                    .addStatement("return %T.%N(value)", className, factory.methodName)
                    .build()
            )
            .addOriginatingKSFile(declaration)
            .build()
    }

    fun detectReaderFactory(enumDeclaration: KSClassDeclaration): ReaderFactory? {
        val companion = enumDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject }
        val companionFactories = companion
            ?.getAllFunctions()
            ?.filter { it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation) }
            ?.toList()
            .orEmpty()
        val bodyFactories = enumDeclaration.getAllFunctions()
            .filter { it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation) }
            .toList()
        val factories = companionFactories + bodyFactories
        if (factories.isEmpty()) {
            return null
        }
        if (factories.size > 1) {
            throw ProcessingErrorException(
                "Enum ${enumDeclaration.simpleName.asString()} has multiple @JsonReader factory methods, only one is allowed",
                factories[1]
            )
        }
        val factory = factories[0]
        if (factory !in companionFactories) {
            throw ProcessingErrorException(
                "@JsonReader factory method must be declared in the enum's companion object",
                factory
            )
        }
        if (!factory.isPublic()) {
            throw ProcessingErrorException(
                "@JsonReader factory method must be public",
                factory
            )
        }
        if (factory.parameters.size != 1) {
            throw ProcessingErrorException(
                "@JsonReader factory method must have exactly one parameter, got ${factory.parameters.size}",
                factory
            )
        }
        val returnDeclaration = factory.returnType?.resolve()?.declaration
        if (returnDeclaration != enumDeclaration) {
            throw ProcessingErrorException(
                "@JsonReader factory method must return ${enumDeclaration.simpleName.asString()}",
                factory
            )
        }
        val valueType = factory.parameters[0].type.toTypeName()
        return ReaderFactory(factory.simpleName.asString(), valueType)
    }

    data class EnumValue(val type: TypeName, val accessor: String)

    fun detectValueType(typeElement: KSClassDeclaration): EnumValue {
        for (function in typeElement.getAllFunctions()) {
            if (function.isPublic() && function.parameters.isEmpty() && function.isAnnotationPresent(JsonTypes.json)) {
                val typeName = function.returnType!!.toTypeName()
                return EnumValue(typeName, function.simpleName.asString())
            }
        }
        val typeName = String::class.asTypeName()
        return EnumValue(typeName, "toString")
    }
}
