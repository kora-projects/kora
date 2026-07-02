package ru.tinkoff.kora.json.ksp.reader

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isConstructor
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

/**
 * Generates a delegating [ru.tinkoff.kora.json.common.JsonReader] for a non-enum type that declares a
 * `@JsonReader`-annotated static factory method `(V) -> T` (Kotlin: in the companion object). The JSON
 * value is read as `V` and passed to the factory (Jackson `@JsonCreator(mode = DELEGATING)` semantics).
 */
class DelegatingJsonReaderGenerator {
    data class ReaderFactory(val methodName: String, val valueType: TypeName, val valueNullable: Boolean)

    fun generate(declaration: KSClassDeclaration): TypeSpec {
        val className = declaration.toClassName()
        val typeName = declaration.toTypeName()
        val factory = detectReaderFactory(declaration)
            ?: throw ProcessingErrorException("No @JsonReader factory method found on ${declaration.simpleName.asString()}", declaration)
        val valueReaderType = JsonTypes.jsonReader.parameterizedBy(factory.valueType)

        val readFun = FunSpec.builder("read")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("parser", JsonTypes.jsonParser)
            .returns(typeName.copy(nullable = true))
            .apply {
                if (factory.valueNullable) {
                    addStatement("return %T.%N(valueReader.read(parser))", className, factory.methodName)
                } else {
                    addStatement("val value = valueReader.read(parser) ?: return null")
                    addStatement("return %T.%N(value)", className, factory.methodName)
                }
            }
            .build()

        return TypeSpec.classBuilder(declaration.jsonReaderName())
            .generated(DelegatingJsonReaderGenerator::class)
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
            .addFunction(readFun)
            .addOriginatingKSFile(declaration)
            .build()
    }

    fun detectReaderFactory(declaration: KSClassDeclaration): ReaderFactory? {
        val companion = declaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject }
        val companionFactories = companion
            ?.getAllFunctions()
            ?.filter { it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation) }
            ?.toList()
            .orEmpty()
        val bodyFactories = declaration.getAllFunctions()
            .filter { !it.isConstructor() }
            .filter { it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation) }
            .toList()
        val factories = companionFactories + bodyFactories
        if (factories.isEmpty()) {
            return null
        }
        if (factories.size > 1) {
            throw ProcessingErrorException(
                "Type ${declaration.simpleName.asString()} has multiple @JsonReader factory methods, only one is allowed",
                factories[1]
            )
        }
        val factory = factories[0]
        if (factory !in companionFactories) {
            throw ProcessingErrorException(
                "@JsonReader factory method must be static (declared in the companion object)",
                factory
            )
        }
        if (!factory.isPublic()) {
            throw ProcessingErrorException("@JsonReader factory method must be public", factory)
        }
        if (factory.parameters.size != 1) {
            throw ProcessingErrorException(
                "@JsonReader factory method must have exactly one parameter, got ${factory.parameters.size}",
                factory
            )
        }
        val returnDeclaration = factory.returnType?.resolve()?.declaration
        if (returnDeclaration != declaration) {
            throw ProcessingErrorException(
                "@JsonReader factory method must return ${declaration.simpleName.asString()}",
                factory
            )
        }
        val hasReaderConstructor = declaration.getConstructors().any {
            it.isAnnotationPresent(JsonTypes.jsonReaderAnnotation) || it.isAnnotationPresent(JsonTypes.json)
        }
        if (hasReaderConstructor) {
            throw ProcessingErrorException(
                "Type ${declaration.simpleName.asString()} has both a @JsonReader factory method and a @JsonReader/@Json constructor — only one is allowed",
                factory
            )
        }
        val valueType = factory.parameters[0].type.toTypeName()
        val valueNullable = factory.parameters[0].type.resolve().isMarkedNullable
        return ReaderFactory(factory.simpleName.asString(), valueType, valueNullable)
    }
}
