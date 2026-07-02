package ru.tinkoff.kora.json.ksp.writer

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.jsonWriterName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName

class EnumJsonWriterGenerator {
    fun generateEnumWriter(jsonClassDeclaration: KSClassDeclaration): TypeSpec {
        val className = jsonClassDeclaration.toClassName()
        val typeName = jsonClassDeclaration.toTypeName()
        val enumType = detectValueType(jsonClassDeclaration)

        val extractor = if (enumType.isStatic) {
            CodeBlock.of("{ e: %T -> %T.%N(e) }", className, className, enumType.accessor)
        } else {
            CodeBlock.of("%T::%N", className, enumType.accessor)
        }

        val typeBuilder = TypeSpec.classBuilder(jsonClassDeclaration.jsonWriterName())
            .generated(JsonWriterGenerator::class)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("valueWriter", JsonTypes.jsonWriter.parameterizedBy(enumType.type))
                    .build()
            )
            .addSuperinterface(
                JsonTypes.jsonWriter.parameterizedBy(typeName),
                CodeBlock.of("%T(%T.values(), %L, valueWriter)", JsonTypes.enumJsonWriter, className, extractor)
            )
            .addOriginatingKSFile(jsonClassDeclaration)
        return typeBuilder.build()
    }


    data class EnumValue(val type: TypeName, val accessor: String, val isStatic: Boolean = false)

    fun detectValueType(typeElement: KSClassDeclaration): EnumValue {
        detectWriterMethod(typeElement)?.let { return it }
        for (function in typeElement.getAllFunctions()) {
            if (function.isPublic() && function.parameters.isEmpty() && function.isAnnotationPresent(JsonTypes.json)) {
                val typeName = function.returnType!!.toTypeName()
                return EnumValue(typeName, function.simpleName.asString())
            }
        }
        val typeName = String::class.asTypeName()
        return EnumValue(typeName, "toString")
    }

    fun detectWriterMethod(enumDeclaration: KSClassDeclaration): EnumValue? {
        val companion = enumDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject }
        val companionMethods = companion
            ?.getAllFunctions()
            ?.filter { it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation) }
            ?.toList()
            .orEmpty()
        val bodyMethods = enumDeclaration.getAllFunctions()
            .filter { it.isAnnotationPresent(JsonTypes.jsonWriterAnnotation) }
            .toList()
        val methods = companionMethods + bodyMethods
        if (methods.isEmpty()) {
            return null
        }
        if (methods.size > 1) {
            throw ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException(
                "Enum ${enumDeclaration.simpleName.asString()} has multiple @JsonWriter methods, only one is allowed",
                methods[1]
            )
        }
        val method = methods[0]
        if (method !in companionMethods) {
            throw ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException(
                "@JsonWriter enum method must be static (declared in the enum's companion object)",
                method
            )
        }
        if (!method.isPublic()) {
            throw ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException("@JsonWriter method must be public", method)
        }
        if (method.parameters.size != 1) {
            throw ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException(
                "@JsonWriter method must have exactly one parameter, got ${method.parameters.size}",
                method
            )
        }
        val paramDeclaration = method.parameters[0].type.resolve().declaration
        if (paramDeclaration != enumDeclaration) {
            throw ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException(
                "@JsonWriter method parameter must be of type ${enumDeclaration.simpleName.asString()}",
                method
            )
        }
        val returnDeclaration = method.returnType?.resolve()?.declaration
        if (returnDeclaration == null || returnDeclaration.qualifiedName?.asString() == "kotlin.Unit") {
            throw ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException("@JsonWriter method must return a value", method)
        }
        return EnumValue(method.returnType!!.toTypeName(), method.simpleName.asString(), isStatic = true)
    }
}
